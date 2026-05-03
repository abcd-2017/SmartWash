package com.smartwash.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.common.OrderStatus;
import com.smartwash.entity.*;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.order.*;
import com.smartwash.mapper.*;
import com.smartwash.service.IOrdersService;
import com.smartwash.task.OrderTimeoutManager;
import com.smartwash.utils.LoginUser;
import com.smartwash.vo.order.OrderItemCountVo;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {
    private final OrdersMapper ordersMapper;
    private final UsersMapper usersMapper;
    private final LockersMapper lockersMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final OrderTimeoutManager orderTimeoutManager;

    @Override
    public Page<OrdersVo> getAllOrders(SearchOrderFrom searchOrderFrom) {
        Page<OrdersVo> page = new Page<>(searchOrderFrom.getPage(), searchOrderFrom.getSize());
        return ordersMapper.searchOrders(page, searchOrderFrom);
    }

    @Override
    public Boolean deleteOrders(String ids) {
        log.info("删除订单, ids: {}", ids);
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public Long createOrder(ReservationOrderFrom reservationOrderFrom, LoginUser loginUser) {
        Users user = usersMapper.selectById(loginUser.getUserId());
        Orders orders = new Orders();
        //查找并锁定空余寄存柜（SELECT FOR UPDATE 防竞态）
        orders.setLockerId(findAndAssignFreeLocker(user.getSchoolId()));

        orders.setUserId(user.getUserId());
        orders.setSchoolId(user.getSchoolId());
        //设置套餐，后续总价格可以叠加优惠券
        orders.setLaundryItemsId(reservationOrderFrom.getItemsId());
        orders.setTotalPrice(BigDecimal.valueOf(reservationOrderFrom.getTotalPrice()));
        orders.setPayPrice(orders.getTotalPrice());

        //设置订单状态
        orders.setStatus(OrderStatus.PENDING_PAYMENT.getStatus());

        //唯一订单号
        Snowflake snowflake = IdUtil.getSnowflake();
        orders.setOrderNo(snowflake.nextIdStr());
        save(orders);
        log.info("订单创建成功, orderId: {}, userId: {}, lockerId: {}", orders.getOrderId(), user.getUserId(), orders.getLockerId());
        // 调度30分钟支付超时任务
        orderTimeoutManager.scheduleTimeout(orders.getOrderId());
        return orders.getOrderId();
    }

    @Override
    public OrdersVo getOrderByOrderId(Long orderId) {
        return ordersMapper.getOrderByOrderId(orderId);
    }

    @Override
    public List<ShowOrderVo> getOrderList(OrderListFrom orderListFrom, LoginUser loginUser) {
        Page<ShowOrderVo> page = new Page<>(orderListFrom.getPage(), orderListFrom.getSize());
        if (Objects.equals(orderListFrom.getStatus(), "001")) {
            return ordersMapper.getOrderList(page, null, loginUser.getUserId()).getRecords();
        } else {
            return ordersMapper.getOrderList(page, orderListFrom.getStatus(), loginUser.getUserId()).getRecords();
        }

    }

    @Override
    public OrderItemCountVo getOrderItemCount(OrderItemCountFrom itemCountFrom, Long userId) {
        OrderItemCountVo itemCountVo = new OrderItemCountVo();

        //待支付数量
        itemCountVo.setPendingPaymentCount(getItemCount(userId, itemCountFrom.getPendingPaymentStatus()));
        //待清洗数量
        itemCountVo.setProcessingCount(getItemCount(userId, itemCountFrom.getProcessingStatus()));
        //待取件数量
        itemCountVo.setPendingPickupCount(getItemCount(userId, itemCountFrom.getPendingPickupStatus()));
        //待寄件数量
        itemCountVo.setShippedCount(getItemCount(userId, itemCountFrom.getShippedStatus()));
        return itemCountVo;
    }

    @Transactional
    @Override
    public Boolean updateOrderStatus(UpdateOrderStatus orderStatus) {
        log.info("管理员变更订单状态, orderId: {}, newStatus: {}", orderStatus.getOrderId(), orderStatus.getStatus());
        Orders orders = getById(orderStatus.getOrderId());
        //当订单状态为清洗中，并且通过后台想要把订单设置成取件，手动模拟发货
        if (orders.getStatus().equals(OrderStatus.WASHING.getStatus()) && orderStatus.getStatus().equals(OrderStatus.READY_FOR_PICKUP.getStatus())) {
            orders.setLockerId(findAndAssignFreeLocker(orders.getSchoolId()));
            orders.setStatus(orderStatus.getStatus());
            orders.setPickupCode(String.format("%d:%d:%s", orders.getUserId(), orders.getOrderId(), RandomUtil.randomInt(1000, 10000)));
            updateById(orders);
            return true;
        } else {
            LambdaUpdateWrapper<Orders> updateWrapper = new LambdaUpdateWrapper<Orders>()
                    .eq(Orders::getOrderId, orderStatus.getOrderId())
                    .set(Orders::getStatus, orderStatus.getStatus());
            return update(updateWrapper);
        }
    }

    @Transactional
    @Override
    public Boolean pickupOrder(OrderNextStatusFrom statusFrom, LoginUser loginUser) {
        return nextStatusOrder(statusFrom, loginUser, OrderStatus.COMPLETED.getStatus());
    }

    @Transactional
    @Override
    public Boolean shippingOrder(OrderNextStatusFrom statusFrom, LoginUser loginUser) {
        return nextStatusOrder(statusFrom, loginUser, OrderStatus.WASHING.getStatus());
    }

    @Override
    public List<Orders> getWashingOrder(LoginUser loginUser, int size) {
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Orders::getUserId, loginUser.getUserId())
                    .and(q -> q.eq(Orders::getStatus, OrderStatus.WASHING.getStatus()))
                    .orderByDesc(Orders::getUpdatedAt)
                    .last("limit " + size);
        return list(queryWrapper);
    }

    /**
     * 取消订单
     *
     * @param orderId
     * @param userId
     * @return
     */
    @Transactional
    @Override
    public Boolean cancelOrder(Long orderId, Long userId) {
        Orders orders = getById(orderId);
        if (orders == null || !orders.getStatus().equals(OrderStatus.PENDING_PAYMENT.getStatus())) {
            throw new CustomExceptions("订单状态异常");
        }
        log.info("订单已取消, orderId: {}, userId: {}", orderId, userId);
        //1.解除寄存柜占用
        lockersMapper.unLocker(orders.getLockerId(), LockerStatusEnum.FREE.getValue());

        //2.修改订单状态
        ordersMapper.nextStatus(orders.getOrderId(), OrderStatus.CANCELED.getStatus());
        // 取消超时任务
        orderTimeoutManager.cancelTimeout(orderId);
        return true;
    }

    //计算使用优惠券后的订单价格
    @Override
    public OrdersVo calculationOrder(Long userId, Long orderId, Long userCouponId) {
        log.info("订单计价, orderId: {}, userId: {}, couponId: {}", orderId, userId, userCouponId);
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if ((userCoupon == null) || userCoupon.getIsUsed() || userCoupon.getExpiredAt().isBefore(LocalDateTime.now()) || !Objects.equals(userCoupon.getUserId(), userId)) {
            throw new CustomExceptions("优惠券异常");
        }
        OrdersVo order = getOrderByOrderId(orderId);
        if (order == null) throw new CustomExceptions("订单状态异常");
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (order.getTotalPrice().compareTo(coupon.getThreshold()) < 0) {
            throw new CustomExceptions("未到达优惠券使用门槛");
        }
        if (order.getTotalPrice().compareTo(coupon.getDiscount()) <= 0) {
            order.setPayPrice(BigDecimal.ZERO);
        } else {
            order.setPayPrice(order.getTotalPrice().subtract(coupon.getDiscount()));
        }
        return order;
    }

    private Boolean nextStatusOrder(OrderNextStatusFrom statusFrom, LoginUser loginUser, String nextStatus) {
        //1.验证取件码是否正确
        Orders orders = getById(statusFrom.getOrderId());
        if (!Objects.equals(loginUser.getUserId(), orders.getUserId())) {
            log.warn("订单用户不匹配, orderId: {}, userId: {}", statusFrom.getOrderId(), loginUser.getUserId());
            throw new CustomExceptions("订单错误");
        }
        if (!Objects.equals(orders.getPickupCode(), statusFrom.getPickupCode())) {
            log.warn("取件码验证失败, orderId: {}", statusFrom.getOrderId());
            throw new CustomExceptions("取件码错误");
        }
        log.info("订单状态变更, orderId: {}, nextStatus: {}", statusFrom.getOrderId(), nextStatus);
        //2.修改订单状态
        orders.setStatus(nextStatus);

        //3.解除被占用的寄存柜
        Long lockerId = orders.getLockerId();
        lockersMapper.unLocker(lockerId, LockerStatusEnum.FREE.getValue());

        ordersMapper.nextStatus(orders.getOrderId(), nextStatus);
        return true;
    }

    //查找并分配空闲寄存柜（SELECT FOR UPDATE 防竞态）
    private Long findAndAssignFreeLocker(Long schoolId) {
        Lockers freeLocker = lockersMapper.getFreeLockerBySchoolIdForUpdate(schoolId);
        if (freeLocker == null) {
            log.warn("寄存柜已满, schoolId: {}", schoolId);
            throw new CustomExceptions("当前寄存柜已满，请稍后再试！");
        }
        freeLocker.setStatus(LockerStatusEnum.USE.getValue());
        lockersMapper.updateById(freeLocker);
        log.info("分配寄存柜, lockerId: {}, schoolId: {}", freeLocker.getLockerId(), schoolId);
        return freeLocker.getLockerId();
    }

    private Integer getItemCount(Long userId, String status) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .and(b -> b.eq(Orders::getStatus, status));
        return Math.toIntExact(count(wrapper));
    }
}
