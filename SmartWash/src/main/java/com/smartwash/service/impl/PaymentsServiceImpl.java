package com.smartwash.service.impl;


import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.OrderStatus;
import com.smartwash.entity.*;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.from.payment.AddPaymentFrom;
import com.smartwash.from.payment.PaymentOrderFrom;
import com.smartwash.from.payment.SearchPaymentFrom;
import com.smartwash.from.payment.UpdatePaymentFrom;
import com.smartwash.mapper.*;
import com.smartwash.service.IPaymentsService;
import com.smartwash.task.OrderTimeoutManager;
import com.smartwash.utils.LoginUser;
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
import java.util.*;
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

    @Transactional
    @Override
    public boolean paymentOrder(LoginUser user, PaymentOrderFrom orderFrom) {
        Orders orders = ordersMapper.selectById(orderFrom.getOrderId());
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
            if (userCoupon == null || userCoupon.getIsUsed() || userCoupon.getExpiredAt().isBefore(LocalDateTime.now())) {
                throw new CustomExceptions(("优惠券异常"));
            }

            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (orders.getTotalPrice().compareTo(coupon.getDiscount()) <= 0) orders.setPayPrice(BigDecimal.ZERO);
            else orders.setPayPrice(orders.getTotalPrice().subtract(coupon.getDiscount()));
            orders.setUserCouponId(userCoupon.getUserCouponId());
        }

        if (orders.getPayPrice().compareTo(users.getBalance()) > 0) {
            throw new CustomExceptions("余额不足");
        }

        //1.更新支付表
        Payments payments = new Payments();
        payments.setOrderId(orderFrom.getOrderId());
        payments.setUserId(user.getUserId());
        payments.setAmount(orders.getPayPrice());
        payments.setPaymentMethod(orderFrom.getPaymentType());
        saveOrUpdate(payments);

        //2.用户余额扣减
        if (orders.getPayPrice().compareTo(BigDecimal.ZERO) > 0)
            usersMapper.decrUserBalance(user.getUserId(), orders.getPayPrice());

        //3.如果使用了优惠券，则修改优惠券状态
        if (userCoupon != null) {
            userCoupon.setIsUsed(true);
            userCoupon.setOrderId(orders.getOrderId());
            userCoupon.setUsedAt(LocalDateTime.now());
            userCouponMapper.updateById(userCoupon);
        }

        //4.设置寄件码，修改订单状态
        int pickCode = RandomUtil.randomInt(1000, 10000);
        orders.setPickupCode(String.format("%d:%d:%s", users.getUserId(), orderFrom.getOrderId(), pickCode));
        orders.setStatus(OrderStatus.PENDING_SHIPMENT.getStatus());
        ordersMapper.updateById(orders);
        // 支付成功，取消超时任务
        orderTimeoutManager.cancelTimeout(orderFrom.getOrderId());
        log.info("订单支付成功, orderId: {}, userId: {}, amount: {}", orderFrom.getOrderId(), user.getUserId(), orders.getPayPrice());
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
        BeanUtils.copyProperties(updatePaymentFrom, payment);
        return updateById(payment);
    }

    @Override
    public Boolean deletePayments(String ids) {
        log.info("删除支付记录, ids: {}", ids);
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }
}
