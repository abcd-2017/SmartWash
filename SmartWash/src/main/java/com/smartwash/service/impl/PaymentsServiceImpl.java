package com.smartwash.service.impl;


import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.OrderStatus;
import com.smartwash.entity.Orders;
import com.smartwash.entity.Payments;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.payment.AddPaymentFrom;
import com.smartwash.from.payment.PaymentOrderFrom;
import com.smartwash.from.payment.SearchPaymentFrom;
import com.smartwash.from.payment.UpdatePaymentFrom;
import com.smartwash.mapper.OrdersMapper;
import com.smartwash.mapper.PaymentsMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.IPaymentsService;
import com.smartwash.utils.LoginUser;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.payment.PaymentVo;
import com.smartwash.vo.users.UserVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Service
public class PaymentsServiceImpl extends ServiceImpl<PaymentsMapper, Payments> implements IPaymentsService {
    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private OrdersMapper ordersMapper;

    @Override
    public Page<PaymentVo> getAllPayments(SearchPaymentFrom searchUserFrom) {
        Page<Payments> page = new Page<>(searchUserFrom.getPage(), searchUserFrom.getSize());
        LambdaQueryWrapper<Payments> queryWrapper = getRechargeRecordsLambdaQueryWrapper(searchUserFrom);

        List<Payments> payments = this.list(page, queryWrapper);
        Page<PaymentVo> paymentVoPage = new Page<>();
        BeanUtils.copyProperties(page, paymentVoPage);
        paymentVoPage.setRecords(payments.stream().map(it -> {
            PaymentVo paymentVo = new PaymentVo();
            Users users = usersMapper.selectById(it.getUserId());
            Orders orders = ordersMapper.selectById(it.getOrderId());

            UserVo userVo = new UserVo();
            userVo.setUserId(users.getUserId());
            userVo.setPhoneNumber(users.getPhoneNumber());
            OrdersVo ordersVo = new OrdersVo();
            ordersVo.setOrderId(orders.getOrderId());
            ordersVo.setOrderNo(orders.getOrderNo());


            paymentVo.setOrder(ordersVo);
            paymentVo.setUser(userVo);
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
        BigDecimal amount = BigDecimal.valueOf(orderFrom.getAmount());
        Users users = usersMapper.selectById(user.getUserId());
        if (amount.compareTo(users.getBalance()) > 0) {
            throw new CustomExceptions("余额不足");
        }

        //1.更新支付表
        Payments payments = new Payments();
        if (users.getBalance().compareTo(amount) < 0) return false;
        payments.setOrderId(orderFrom.getOrderId());
        payments.setUserId(user.getUserId());
        payments.setAmount(amount);
        payments.setPaymentMethod(orderFrom.getPaymentType());
        saveOrUpdate(payments);

        //2.用户余额扣减
        usersMapper.decrUserBalance(user.getUserId(), payments.getAmount());

        //3.修改订单状态
        ordersMapper.updateOrderStatus(orderFrom.getOrderId(), OrderStatus.PENDING_SHIPMENT.getStatus());
        //设置寄件码
        int pickCode = RandomUtil.randomInt(1000, 10000);
        ordersMapper.setPickupCode(orderFrom.getOrderId(), String.format("%d:%d:%s", users.getUserId(), orderFrom.getOrderId(), pickCode));
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
        return save(payment);
    }

    @Override
    public Boolean updatePayment(UpdatePaymentFrom updatePaymentFrom) {
        Payments payment = getById(updatePaymentFrom.getOrderId());
        BeanUtils.copyProperties(updatePaymentFrom, payment);
        return updateById(payment);
    }

    @Override
    public Boolean deletePayments(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }
}
