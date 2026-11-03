package com.smartwash.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.subscription.constant.SubscriptionStatus;
import com.smartwash.subscription.entity.UserSubscription;
import com.smartwash.subscription.entity.UserSubscriptionUsageLog;
import com.smartwash.subscription.from.AddUsageFrom;
import com.smartwash.subscription.mapper.UserSubscriptionMapper;
import com.smartwash.subscription.mapper.UserSubscriptionUsageLogMapper;
import com.smartwash.subscription.service.UserSubscriptionUsageLogService;
import com.smartwash.subscription.vo.SubscriptionGroupCountVo;
import com.smartwash.subscription.vo.SubscriptionUsageVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订阅使用打卡流水服务实现。
 * 打卡以订阅归属 + 状态校验先行；同日重复打卡拒绝（撤销后可重打），不参与任何资金计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSubscriptionUsageLogServiceImpl extends ServiceImpl<UserSubscriptionUsageLogMapper, UserSubscriptionUsageLog>
        implements UserSubscriptionUsageLogService {

    private final UserSubscriptionMapper userSubscriptionMapper;

    @Override
    public SubscriptionUsageVo checkIn(Long subscriptionId, Long userId, AddUsageFrom from) {
        UserSubscription sub = userSubscriptionMapper.selectById(subscriptionId);
        if (sub == null || !sub.getUserId().equals(userId)) {
            throw new CustomExceptions("订阅不存在");
        }
        if (SubscriptionStatus.isTerminal(sub.getStatus())) {
            throw new CustomExceptions("订阅已终态，不可打卡");
        }
        LocalDate usageDate = from.getUsageDate() == null ? LocalDate.now() : from.getUsageDate();
        if (usageDate.isAfter(LocalDate.now())) {
            throw new CustomExceptions("打卡日期不能晚于今天");
        }
        // 每订阅每天一条：同日重复打卡拒绝（撤销后可重打），非唯一索引强约束，双击竞态最多产生无害冗余行
        Long dup = this.getBaseMapper().selectCount(new LambdaQueryWrapper<UserSubscriptionUsageLog>()
                .eq(UserSubscriptionUsageLog::getSubscriptionId, subscriptionId)
                .eq(UserSubscriptionUsageLog::getUsageDate, usageDate));
        if (dup != null && dup > 0) {
            throw new CustomExceptions("该日期已有打卡记录，可先撤销再重打");
        }
        UserSubscriptionUsageLog entity = new UserSubscriptionUsageLog()
                .setSubscriptionId(subscriptionId)
                .setUserId(userId)
                .setUsageDate(usageDate)
                .setNote(from.getNote());
        this.save(entity);
        log.info("使用打卡成功, subscriptionId: {}, userId: {}, usageDate: {}", subscriptionId, userId, usageDate);
        return buildVo(entity);
    }

    @Override
    public void deleteUsage(Long subscriptionId, Long usageId, Long userId) {
        UserSubscriptionUsageLog record = this.getById(usageId);
        if (record == null || !record.getUserId().equals(userId) || !record.getSubscriptionId().equals(subscriptionId)) {
            throw new CustomExceptions("打卡记录不存在");
        }
        this.removeById(usageId);
        log.info("打卡已撤销, usageId: {}, subscriptionId: {}, userId: {}", usageId, subscriptionId, userId);
    }

    @Override
    public IPage<SubscriptionUsageVo> pageBySubscription(Long subscriptionId, Long userId, long page, long size) {
        UserSubscription sub = userSubscriptionMapper.selectById(subscriptionId);
        if (sub == null || !sub.getUserId().equals(userId)) {
            throw new CustomExceptions("订阅不存在");
        }
        LambdaQueryWrapper<UserSubscriptionUsageLog> wrapper = new LambdaQueryWrapper<UserSubscriptionUsageLog>()
                .eq(UserSubscriptionUsageLog::getSubscriptionId, subscriptionId)
                .orderByDesc(UserSubscriptionUsageLog::getUsageDate)
                .orderByDesc(UserSubscriptionUsageLog::getUsageId);
        IPage<UserSubscriptionUsageLog> result = this.page(new Page<>(page, size), wrapper);
        Page<SubscriptionUsageVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::buildVo).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<SubscriptionGroupCountVo> countGroupBySubscription(Long userId, LocalDate sinceDate) {
        return this.getBaseMapper().countGroupBySubscription(userId, sinceDate);
    }

    private SubscriptionUsageVo buildVo(UserSubscriptionUsageLog record) {
        return new SubscriptionUsageVo()
                .setUsageId(record.getUsageId())
                .setSubscriptionId(record.getSubscriptionId())
                .setUsageDate(record.getUsageDate())
                .setNote(record.getNote())
                .setCreatedAt(record.getCreatedAt());
    }
}
