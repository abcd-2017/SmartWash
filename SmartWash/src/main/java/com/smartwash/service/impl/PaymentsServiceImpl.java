package com.smartwash.service.impl;


import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.PayType;
import com.smartwash.common.PaymentStatus;
import com.smartwash.entity.*;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.from.payment.AddPaymentFrom;
import com.smartwash.from.payment.PaymentOrderFrom;
import com.smartwash.from.payment.SearchPaymentFrom;
import com.smartwash.from.payment.UpdatePaymentFrom;
import com.smartwash.mapper.*;
import com.smartwash.service.IPaymentsService;
import com.smartwash.service.PaymentGatewayService;
import com.smartwash.task.OrderTimeoutManager;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.PickupCodeUtils;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.payment.PaymentVo;
import com.smartwash.vo.users.UserVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Slf4j
@Service
public class PaymentsServiceImpl extends ServiceImpl<PaymentsMapper, Payments> implements IPaymentsService {
    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponMapper userCouponMapper;
    @Autowired
    private OrderTimeoutManager orderTimeoutManager;
    @Autowired
    private PaymentGatewayService paymentGatewayService;

    @Override
    public Page<PaymentVo> getAllPayments(SearchPaymentFrom searchUserFrom) {
        Page<Payments> page = new Page<>(searchUserFrom.getPage(), searchUserFrom.getSize());
        LambdaQueryWrapper<Payments> queryWrapper = getRechargeRecordsLambdaQueryWrapper(searchUserFrom);

        List<Payments> payments = this.list(page, queryWrapper);
        Page<PaymentVo> paymentVoPage = new Page<>();
        BeanUtils.copyProperties(page, paymentVoPage);

        // 批量查询关联数据，避免N+1问题
        Set<Long> userIds = payments.stream().map(Payments::getUserId).collect(Collectors.toSet());
        Set<Long> orderIds = payments.stream().map(Payments::getOrderId).collect(Collectors.toSet());
        Map<Long, Users> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : usersMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId, Function.identity()));
        Map<Long, Orders> orderMap = orderIds.isEmpty() ? Collections.emptyMap()
                : ordersMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Orders::getOrderId, Function.identity()));

        paymentVoPage.setRecords(payments.stream().map(it -> {
            PaymentVo paymentVo = new PaymentVo();
            Users users = userMap.get(it.getUserId());
            Orders orders = orderMap.get(it.getOrderId());

            UserVo userVo = new UserVo();
            if (users != null) {
                userVo.setUserId(users.getUserId());
                userVo.setPhoneNumber(users.getPhoneNumber());
            }
            OrdersVo ordersVo = new OrdersVo();
            if (orders != null) {
                ordersVo.setOrderId(orders.getOrderId());
                ordersVo.setOrderNo(orders.getOrderNo());
            }

            paymentVo.setOrder(ordersVo);
            paymentVo.setUser(userVo);
            BeanUtils.copyProperties(it, paymentVo);
            return paymentVo;
        }).toList());

        return paymentVoPage;
    }

    @Override
    public Page<PaymentVo> getUserPayments(Long userId, BaseSearchFrom searchFrom) {
        Page<Payments> page = new Page<>(searchFrom.getPage(), searchFrom.getSize());
        LambdaQueryWrapper<Payments> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Payments::getUserId, userId);
        queryWrapper.orderByDesc(Payments::getPaidAt);

        List<Payments> payments = this.list(page, queryWrapper);
        Page<PaymentVo> paymentVoPage = new Page<>();
        BeanUtils.copyProperties(page, paymentVoPage);

        Set<Long> orderIds = payments.stream().map(Payments::getOrderId).collect(Collectors.toSet());
        Map<Long, Orders> orderMap = orderIds.isEmpty() ? Collections.emptyMap()
                : ordersMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Orders::getOrderId, Function.identity()));

        paymentVoPage.setRecords(payments.stream().map(it -> {
            PaymentVo paymentVo = new PaymentVo();
            Orders orders = orderMap.get(it.getOrderId());
            if (orders != null) {
                OrdersVo ordersVo = new OrdersVo();
                ordersVo.setOrderId(orders.getOrderId());
                ordersVo.setOrderNo(orders.getOrderNo());
                ordersVo.setStatus(orders.getStatus());
                ordersVo.setTotalPrice(orders.getTotalPrice());
                ordersVo.setPayPrice(orders.getPayPrice());
                paymentVo.setOrder(ordersVo);
            }
            BeanUtils.copyProperties(it, paymentVo);
            return paymentVo;
        }).toList());

        return paymentVoPage;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean paymentOrder(LoginUser user, PaymentOrderFrom orderFrom) {
        // 支付方式白名单校验：仅接受 PayType 枚举定义的方式（与 payments.payment_method 取值 1-钱包/2-支付宝/3-微信 对齐），
        // 任意字符串直接拒绝，防止脏数据入库（评审报告后端 #28）
        if (Arrays.stream(PayType.values()).noneMatch(t -> t.getType().equals(orderFrom.getPaymentType()))) {
            String allowed = Arrays.stream(PayType.values())
                    .map(t -> t.getType() + "-" + t.getDescription())
                    .collect(Collectors.joining("、"));
            throw new CustomExceptions("不支持的支付方式（可选：" + allowed + "）");
        }
        Orders orders = ordersMapper.selectByIdForUpdate(orderFrom.getOrderId());
        if (orders == null || !Objects.equals(orders.getUserId(), user.getUserId())) {
            throw new CustomExceptions("订单错误");
        }
        if (!Objects.equals(orders.getStatus(), OrderStatus.PENDING_PAYMENT.getStatus())) {
            throw new CustomExceptions("该订单已经支付");
        }
        Users users = usersMapper.selectById(user.getUserId());
        UserCoupon userCoupon = null;
        //如果用户优惠券id不为空，就使用优惠券
        if (orderFrom.getUserCouponId() != null) {
            userCoupon = userCouponMapper.selectById(orderFrom.getUserCouponId());
            if (userCoupon == null || userCoupon.getIsUsed()
                    || userCoupon.getExpiredAt().isBefore(LocalDateTime.now())
                    || !Objects.equals(userCoupon.getUserId(), user.getUserId())) {
                throw new CustomExceptions("优惠券异常");
            }

            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (coupon == null) {
                throw new CustomExceptions("优惠券不存在");
            }
            // 校验优惠券使用门槛
            if (orders.getTotalPrice().compareTo(coupon.getThreshold()) < 0) {
                throw new CustomExceptions("未到达优惠券使用门槛");
            }
            if (orders.getTotalPrice().compareTo(coupon.getDiscount()) <= 0) orders.setPayPrice(BigDecimal.ZERO);
            else orders.setPayPrice(orders.getTotalPrice().subtract(coupon.getDiscount()));
            orders.setUserCouponId(userCoupon.getUserCouponId());
        }

        if (orders.getPayPrice().compareTo(users.getBalance()) > 0) {
            throw new CustomExceptions("余额不足");
        }

        // 将应付金额与用券信息先落库（仍处于订单行锁与同一事务内），
        // 供支付回调阶段按订单反查用券信息；后续任一环节失败随事务整体回滚，不留脏数据
        ordersMapper.updateById(orders);

        // ===== 两段式支付：处理中 → 回调完成 =====
        // 生成幂等键：PAY + yyyyMMdd + 雪花ID，落库后由唯一索引 uk_payments_out_trade_no 兜底防重
        // （多实例部署时需为各实例配置独立雪花 workerId，与 OrderTimeoutManager 的单机限制同源）
        String outTradeNo = "PAY" + LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + IdUtil.getSnowflakeNextIdStr();
        Payments payments = new Payments();
        payments.setOrderId(orderFrom.getOrderId());
        payments.setUserId(user.getUserId());
        payments.setAmount(orders.getPayPrice());
        payments.setPaymentMethod(orderFrom.getPaymentType());
        payments.setOutTradeNo(outTradeNo);
        // 处理中：paid_at 留空，待回调成功时与 status='1' 同语句写入（Dashboard 收入统计依赖 status='1' + paid_at）
        payments.setStatus(PaymentStatus.PROCESSING.getStatus());
        save(payments);

        // 调用网关下单。桩实现在同一调用栈内同步回调支付成功；回调经 this 自调用绕过 Spring 事务代理，
        // handlePaymentSuccess 运行于本事务内——订单行锁（selectByIdForUpdate）仍由本事务持有，
        // 避免独立事务二次加锁造成自阻塞。真实网关接入后，回调由异步通知（验签后）经代理调用
        // handlePaymentSuccess（注入本实现类触发 CGLIB 代理），届时事务注解生效、独立提交，
        // 届时 createPayment 先返回预支付信息、回调异步到达，本处的同步结果穿透逻辑仅作用于同步回调场景。
        // 回调双标志：received 区分"网关没回调"与"回调被拒"，初始均为 false——
        // 若网关适配缺陷导致从不回调，支付将以失败收尾，绝不出现"用户看到支付成功而流水停在处理中"
        AtomicBoolean callbackReceived = new AtomicBoolean(false);
        AtomicBoolean callbackAccepted = new AtomicBoolean(false);
        try {
            paymentGatewayService.createPayment(outTradeNo, orders.getPayPrice(), "洗衣订单支付-" + orders.getOrderNo(),
                    orderFrom.getPaymentType(), tradeNo -> {
                        // 回调结果穿透：回调被拒（订单已取消/流转）时不能按"支付成功"收尾（评审报告后端 #6 遗留）
                        callbackReceived.set(true);
                        callbackAccepted.set(handlePaymentSuccess(tradeNo));
                    });
        } catch (CustomExceptions ce) {
            // 业务校验类异常（余额不足/优惠券已被使用等）原样上抛：事务整体回滚，
            // “处理中”支付记录一并回滚不留痕，订单保持待支付，由全局异常处理器返回 Result
            throw ce;
        } catch (Exception e) {
            log.error("支付网关下单异常, outTradeNo: {}, orderId: {}", outTradeNo, orderFrom.getOrderId(), e);
            throw new CustomExceptions("支付网关异常，请稍后重试");
        }
        if (!callbackReceived.get()) {
            // 网关实现未回调（适配缺陷）：以失败收尾并留痕排查，事务回滚"处理中"流水不留痕
            log.error("支付网关下单后未回调, outTradeNo: {}, orderId: {}", outTradeNo, orderFrom.getOrderId());
            throw new CustomExceptions("支付失败，请稍后重试");
        }
        if (!callbackAccepted.get()) {
            // 回调被拒：handlePaymentSuccess 内已把支付记录收敛为失败（随本事务整体回滚不留痕），
            // 此处必须抛异常让用户得到明确失败结果，而非提示"支付成功"
            log.warn("支付回调被拒绝（订单状态已变更）, outTradeNo: {}, orderId: {}", outTradeNo, orderFrom.getOrderId());
            throw new CustomExceptions("支付失败：订单状态已变更，请刷新后重试");
        }
        log.info("订单支付成功, orderId: {}, userId: {}, amount: {}, outTradeNo: {}",
                orderFrom.getOrderId(), user.getUserId(), orders.getPayPrice(), outTradeNo);
        return true;
    }

    /**
     * 支付成功统一回调处理（幂等）：以支付记录按 out_trade_no 的条件更新（处理中→已支付）为幂等闸门，
     * 闸门抢到才执行扣款、核销优惠券、订单流转，重复回调/重放不会重复扣款。
     *
     * <p>事务边界说明：
     * <ul>
     * <li>桩同步回调路径：由 {@link #paymentOrder} 经 this 自调用，Spring 事务代理被绕过，
     *     本方法运行于 paymentOrder 的既有事务内，订单行锁仍持有——这是期望行为；</li>
     * <li>真实网关异步路径：由回调控制器经代理调用本方法（注入本实现类），事务注解生效、独立提交，
     *     此时本方法自带完整并发防护（订单行锁 + 幂等闸门 + 条件扣款），可脱离 paymentOrder 独立工作。</li>
     * </ul>
     *
     * @param outTradeNo 网关统一订单号（幂等键）
     * @return true=支付成功或幂等重放；false=回调被拒绝（订单已取消/流转，支付记录已置失败）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaymentSuccess(String outTradeNo) {
        // 1.反查支付记录：金额、归属一律以库内流水为准，不信任任何前端/回调报文携带的金额
        Payments payment = this.baseMapper.selectOne(
                new LambdaQueryWrapper<Payments>().eq(Payments::getOutTradeNo, outTradeNo));
        if (payment == null) {
            log.error("支付回调异常：支付记录不存在, outTradeNo: {}", outTradeNo);
            throw new CustomExceptions("支付记录不存在");
        }
        // 2.幂等快路径：记录已处于终态（已支付/失败）说明回调重复投递，直接按已处理返回，不再扣款
        if (!PaymentStatus.PROCESSING.getStatus().equals(payment.getStatus())) {
            log.info("支付回调重复投递，记录已处于终态，跳过处理, outTradeNo: {}, status: {}", outTradeNo, payment.getStatus());
            return true;
        }
        // 3.订单行锁 + 状态校验：防超时取消/用户取消与异步回调竞态（真实网关路径下订单可能已被取消）
        Orders orders = ordersMapper.selectByIdForUpdate(payment.getOrderId());
        if (orders == null) {
            throw new CustomExceptions("订单不存在");
        }
        if (!OrderStatus.PENDING_PAYMENT.getStatus().equals(orders.getStatus())) {
            // 订单已取消/流转：拒绝本次入账，支付记录收敛为失败终态（条件更新，重复回调幂等安全）
            this.baseMapper.markFail(outTradeNo);
            log.warn("订单状态已非待支付，支付回调拒绝入账并置失败, outTradeNo: {}, orderId: {}, orderStatus: {}",
                    outTradeNo, orders.getOrderId(), orders.getStatus());
            return false;
        }
        // 4.幂等闸门：out_trade_no 条件更新 处理中→已支付 并同语句落 paid_at；
        //   影响行数==0 说明已被并发回调处理完成，按幂等成功返回，不再扣款
        if (this.baseMapper.markSuccess(outTradeNo) == 0) {
            log.warn("支付幂等闸门未命中（已被并发回调处理），跳过, outTradeNo: {}", outTradeNo);
            return true;
        }
        // 5.条件扣款（balance >= amount 防超扣），金额取库内支付流水
        if (payment.getAmount().compareTo(BigDecimal.ZERO) > 0
                && usersMapper.decrUserBalance(payment.getUserId(), payment.getAmount()) == 0) {
            throw new CustomExceptions("余额不足或扣减失败");
        }
        // 6.原子核销优惠券：影响行数==0 即并发下已被使用，抛异常回滚整个事务
        if (orders.getUserCouponId() != null
                && userCouponMapper.markUsed(orders.getUserCouponId(), payment.getUserId(), orders.getOrderId()) == 0) {
            throw new CustomExceptions("优惠券已被使用");
        }
        // 7.订单置待寄件 + 生成取件码（当前处于订单行锁保护内，updateById 安全）。
        //   取件码 userId:orderId:6位纯随机数字：保留三段冒号契约，随机段收敛可预测成分（评审报告后端 #40）；
        //   生成时查重，pickup_code 唯一索引兜底，连续冲突抛异常随本事务整体回滚
        String pickupCode = PickupCodeUtils.generate(payment.getUserId(), orders.getOrderId(),
                code -> {
                    Long existCount = ordersMapper.selectCount(new LambdaQueryWrapper<Orders>().eq(Orders::getPickupCode, code));
                    return existCount != null && existCount > 0;
                });
        orders.setPickupCode(pickupCode);
        orders.setStatus(OrderStatus.PENDING_SHIPMENT.getStatus());
        ordersMapper.updateById(orders);
        // 8.支付成功，取消订单超时任务
        orderTimeoutManager.cancelTimeout(orders.getOrderId());
        log.info("支付回调处理完成, orderId: {}, userId: {}, amount: {}, outTradeNo: {}",
                orders.getOrderId(), payment.getUserId(), payment.getAmount(), outTradeNo);
        return true;
    }

    private LambdaQueryWrapper<Payments> getRechargeRecordsLambdaQueryWrapper(SearchPaymentFrom searchUserFrom) {
        LambdaQueryWrapper<Payments> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(searchUserFrom.getPhoneNumber())) {
            Users user = usersMapper.getUserByPhoneNumber(searchUserFrom.getPhoneNumber());
            Long userId = user == null ? -1 : user.getUserId();
            queryWrapper.and(q -> q.eq(Payments::getUserId, userId));
        }

        if (StringUtils.hasText(searchUserFrom.getOrderNo())) {
            Orders order = ordersMapper.getOrderByOrderNo(searchUserFrom.getOrderNo());
            Long orderId = order == null ? -1 : order.getOrderId();
            queryWrapper.and(q -> q.eq(Payments::getOrderId, orderId));
        }

        queryWrapper.and(searchUserFrom.getPaymentId() != null, b -> b.eq(Payments::getPaymentId, searchUserFrom.getPaymentId()));
        queryWrapper.and(searchUserFrom.getPaymentMethod() != null, b -> b.eq(Payments::getPaymentMethod, searchUserFrom.getPaymentMethod()));
        queryWrapper.and(searchUserFrom.getStatus() != null, b -> b.eq(Payments::getStatus, searchUserFrom.getStatus()));
        queryWrapper.and(searchUserFrom.getStartTime() != null, b -> b.ge(Payments::getPaidAt, searchUserFrom.getStartTime()));
        queryWrapper.and(searchUserFrom.getEndTime() != null, b -> b.le(Payments::getPaidAt, searchUserFrom.getEndTime()));

        return queryWrapper;
    }

    @Override
    public Boolean addPayment(AddPaymentFrom addPaymentFrom) {
        Payments payment = new Payments();
        BeanUtils.copyProperties(addPaymentFrom, payment);
        boolean result = save(payment);
        log.info("新增支付记录, paymentId: {}", payment.getPaymentId());
        return result;
    }

    @Override
    public Boolean updatePayment(UpdatePaymentFrom updatePaymentFrom) {
        log.info("更新支付记录, paymentId: {}", updatePaymentFrom.getPaymentId());
        Payments payment = getById(updatePaymentFrom.getPaymentId());
        if (payment == null) {
            throw new CustomExceptions("支付记录不存在");
        }
        BeanUtils.copyProperties(updatePaymentFrom, payment);
        return updateById(payment);
    }

    // 支付凭证为资金记录，禁止物理删除（项目硬规则；删除入口已随 DELETE /admin/payments/delete/{ids} 一并摘除）
}
