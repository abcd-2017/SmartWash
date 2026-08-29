package com.smartwash.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.PaymentStatus;
import com.smartwash.entity.RechargeRecords;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.from.recharge_records.SearchRechargeRecordsFrom;
import com.smartwash.from.recharge_records.UserRechargeFrom;
import com.smartwash.mapper.RechargeRecordsMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.IRechargeRecordsService;
import com.smartwash.service.PaymentGatewayService;
import com.smartwash.vo.recharge_records.RechargeRecordsVo;
import com.smartwash.vo.users.UserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeRecordsServiceImpl extends ServiceImpl<RechargeRecordsMapper, RechargeRecords> implements IRechargeRecordsService {
    private final UsersMapper usersMapper;
    private final PaymentGatewayService paymentGatewayService;

    /**
     * 单笔充值金额上限（元）：防止异常/恶意大额单笔充值冲击资金链路。
     * 当前为硬编码常量，满足 demo 需求；后续如需运营侧动态调整，再做配置化（环境变量/管理端参数）。
     */
    private static final BigDecimal MAX_RECHARGE_AMOUNT = new BigDecimal("10000");

    //获取所有充值记录
    @Override
    public Page<RechargeRecordsVo> getAllRecords(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        Page<RechargeRecords> page = new Page<>(rechargeRecordsFrom.getPage(), rechargeRecordsFrom.getSize());
        LambdaQueryWrapper<RechargeRecords> queryWrapper = getRechargeRecordsLambdaQueryWrapper(rechargeRecordsFrom);

        List<RechargeRecords> rechargeRecords = this.list(page, queryWrapper);
        Page<RechargeRecordsVo> rechargeRecordsVoPage = new Page<>();
        BeanUtils.copyProperties(page, rechargeRecordsVoPage);

        // 批量查询用户数据，避免N+1问题
        Set<Long> userIds = rechargeRecords.stream().map(RechargeRecords::getUserId).collect(Collectors.toSet());
        Map<Long, Users> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : usersMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId, Function.identity()));

        rechargeRecordsVoPage.setRecords(rechargeRecords.stream().map(it -> {
            RechargeRecordsVo recordsVo = new RechargeRecordsVo();
            BeanUtils.copyProperties(it, recordsVo);
            Users users = userMap.get(it.getUserId());
            UserVo userVo = new UserVo();
            if (users != null) {
                userVo.setUserId(users.getUserId());
                userVo.setPhoneNumber(users.getPhoneNumber());
            }
            recordsVo.setUsers(userVo);
            return recordsVo;
        }).toList());

        return rechargeRecordsVoPage;
    }

    //获取当前用户充值记录
    @Override
    public Page<RechargeRecordsVo> getUserRechargeRecords(Long userId, BaseSearchFrom searchFrom) {
        Page<RechargeRecords> page = new Page<>(searchFrom.getPage(), searchFrom.getSize());
        LambdaQueryWrapper<RechargeRecords> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RechargeRecords::getUserId, userId);
        queryWrapper.orderByDesc(RechargeRecords::getRechargeTime);

        List<RechargeRecords> rechargeRecords = this.list(page, queryWrapper);
        Page<RechargeRecordsVo> rechargeRecordsVoPage = new Page<>();
        BeanUtils.copyProperties(page, rechargeRecordsVoPage);

        rechargeRecordsVoPage.setRecords(rechargeRecords.stream().map(it -> {
            RechargeRecordsVo recordsVo = new RechargeRecordsVo();
            BeanUtils.copyProperties(it, recordsVo);
            return recordsVo;
        }).toList());

        return rechargeRecordsVoPage;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean userRecharge(UserRechargeFrom vo, Long userId) {
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            throw new CustomExceptions("用户不存在");
        }
        BigDecimal amount = vo.getAmount();
        // 金额服务端兜底校验（不信任前端）：必须为正数、最多两位小数且不超过单笔上限；到账金额即该金额，
        // 不存在任何前端可传的赠送/折扣，加钱只发生在回调幂等闸门之后
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomExceptions("充值金额必须大于0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new CustomExceptions("充值金额最多支持两位小数");
        }
        if (amount.compareTo(MAX_RECHARGE_AMOUNT) > 0) {
            throw new CustomExceptions("单笔充值金额不能超过 " + MAX_RECHARGE_AMOUNT.stripTrailingZeros().toPlainString() + " 元");
        }

        // ===== 两段式充值：处理中 → 回调完成 =====
        // 生成幂等键：RCH + yyyyMMdd + 雪花ID，落库后由唯一索引 uk_recharge_records_out_trade_no 兜底防重
        // （多实例部署时需为各实例配置独立雪花 workerId，与 OrderTimeoutManager 的单机限制同源）
        String outTradeNo = "RCH" + LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + IdUtil.getSnowflakeNextIdStr();
        //1.充值表插入数据：处理中状态，到账由回调阶段完成
        RechargeRecords records = new RechargeRecords();
        records.setUserId(userId);
        records.setRechargeType(vo.getRechargeType());
        records.setAmount(amount);
        records.setStatus(PaymentStatus.PROCESSING.getStatus());
        records.setOutTradeNo(outTradeNo);
        save(records);

        //2.调用网关下单。桩实现在同一调用栈内同步回调充值成功；回调经 this 自调用绕过 Spring 事务代理，
        //  handleRechargeSuccess 运行于本事务内。真实网关接入后，回调由异步通知（验签后）经代理调用
        //  handleRechargeSuccess（注入本实现类触发 CGLIB 代理），届时事务注解生效、独立提交。
        try {
            paymentGatewayService.createPayment(outTradeNo, amount, "账户余额充值",
                    vo.getRechargeType(), tradeNo -> handleRechargeSuccess(tradeNo));
        } catch (CustomExceptions ce) {
            // 业务校验类异常原样上抛：事务整体回滚，“处理中”充值记录一并回滚不留痕，
            // 由全局异常处理器返回 Result
            throw ce;
        } catch (Exception e) {
            log.error("充值网关下单异常, outTradeNo: {}, userId: {}", outTradeNo, userId, e);
            throw new CustomExceptions("支付网关异常，请稍后重试");
        }
        log.info("用户充值成功, userId: {}, amount: {}, outTradeNo: {}", userId, amount, outTradeNo);
        return true;
    }

    /**
     * 充值成功统一回调处理（幂等）：以充值记录按 out_trade_no 的条件更新（处理中→已到账）为幂等闸门，
     * 闸门抢到才执行加余额，重复回调/重放不会重复加钱。
     *
     * <p>事务边界说明：桩同步回调路径由 {@link #userRecharge} 经 this 自调用，运行于其既有事务内；
     * 真实网关异步路径由回调控制器经代理调用（注入本实现类），事务注解生效、独立提交，
     * 本方法自带幂等闸门与条件校验，可脱离 userRecharge 独立工作。
     *
     * @param outTradeNo 网关统一订单号（幂等键）
     * @return true=充值成功或幂等重放
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleRechargeSuccess(String outTradeNo) {
        // 1.幂等闸门：out_trade_no 条件更新 处理中→已到账；
        //   影响行数==0 说明已处理过（重复回调/重放），直接按成功返回，绝不重复加钱
        if (this.baseMapper.markSuccess(outTradeNo) == 0) {
            log.warn("充值回调重复投递或状态不符，跳过幂等处理, outTradeNo: {}", outTradeNo);
            return true;
        }
        // 2.反查充值记录：到账金额一律以库内流水为准，不信任任何前端/回调报文携带的金额
        RechargeRecords record = this.baseMapper.selectOne(
                new LambdaQueryWrapper<RechargeRecords>().eq(RechargeRecords::getOutTradeNo, outTradeNo));
        if (record == null || record.getAmount() == null || record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("充值回调异常：充值记录不存在或金额非法, outTradeNo: {}", outTradeNo);
            throw new CustomExceptions("充值记录异常");
        }
        // 3.入账：incrUserBalance 无条件累加，但幂等闸门已确保仅执行一次；
        //   影响行数==0 说明用户不存在，抛异常回滚闸门更新
        if (usersMapper.incrUserBalance(record.getUserId(), record.getAmount()) == 0) {
            throw new CustomExceptions("充值入账失败，用户不存在");
        }
        log.info("充值回调完成, userId: {}, amount: {}, outTradeNo: {}",
                record.getUserId(), record.getAmount(), outTradeNo);
        return true;
    }

    private LambdaQueryWrapper<RechargeRecords> getRechargeRecordsLambdaQueryWrapper(SearchRechargeRecordsFrom rechargeRecordsFrom) {
        LambdaQueryWrapper<RechargeRecords> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(rechargeRecordsFrom.getPhoneNumber())) {
            Users user = usersMapper.getUserByPhoneNumber(rechargeRecordsFrom.getPhoneNumber());
            Long userId = user == null ? -1 : user.getUserId();
            queryWrapper.eq(RechargeRecords::getUserId, userId);
        }

        queryWrapper.and(rechargeRecordsFrom.getRecordId() != null, b -> b.eq(RechargeRecords::getRecordId, rechargeRecordsFrom.getRecordId()));
        queryWrapper.and(rechargeRecordsFrom.getStartTime() != null, b -> b.ge(RechargeRecords::getRechargeTime, rechargeRecordsFrom.getStartTime()));
        queryWrapper.and(rechargeRecordsFrom.getEndTime() != null, b -> b.le(RechargeRecords::getRechargeTime, rechargeRecordsFrom.getEndTime()));
        queryWrapper.and(rechargeRecordsFrom.getRechargeType() != null, b -> b.eq(RechargeRecords::getRechargeType, rechargeRecordsFrom.getRechargeType()));

        return queryWrapper;
    }
}
