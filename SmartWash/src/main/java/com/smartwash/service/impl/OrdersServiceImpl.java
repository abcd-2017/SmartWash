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
import com.smartwash.entity.LaundryItems;
import com.smartwash.service.IOrdersService;
import com.smartwash.task.OrderTimeoutManager;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.order.OrderGroupVo;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {
    private final OrdersMapper ordersMapper;
    private final UsersMapper usersMapper;
    private final LockersMapper lockersMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final LaundryItemsMapper laundryItemsMapper;
    private final OrderTimeoutManager orderTimeoutManager;

    @Override
    public Page<OrdersVo> getAllOrders(SearchOrderFrom searchOrderFrom) {
        Page<OrdersVo> page = new Page<>(searchOrderFrom.getPage(), searchOrderFrom.getSize());
        return ordersMapper.searchOrders(page, searchOrderFrom);
    }

    @Override
    public Boolean deleteOrders(String ids) {
        log.info("删除订单, ids: {}", ids);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return removeByIds(idList);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @Override
    public Long createOrder(ReservationOrderFrom reservationOrderFrom, LoginUser loginUser) {
        Users user = usersMapper.selectById(loginUser.getUserId());
        Orders orders = new Orders();
        //查找并锁定空余寄存柜（SELECT FOR UPDATE 防竞态）
        orders.setLockerId(findAndAssignFreeLocker(user.getSchoolId()));

        orders.setUserId(user.getUserId());
        orders.setSchoolId(user.getSchoolId());
        //设置套餐，从数据库查询实际价格（不信任客户端传入的价格）
        LaundryItems item = laundryItemsMapper.selectById(reservationOrderFrom.getItemsId());
        if (item == null) {
            throw new CustomExceptions("洗衣套餐不存在");
        }
        orders.setLaundryItemsId(reservationOrderFrom.getItemsId());
        orders.setTotalPrice(item.getBasePrice());
        orders.setPayPrice(item.getBasePrice());

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
        OrdersVo order = ordersMapper.getOrderByOrderId(orderId);
        if (order == null) {
            return null;
        }
        // 校验订单归属：仅允许订单所属用户查看
        LoginUser currentUser = UserContextHolder.getUser();
        if (currentUser != null && order.getUserVo() != null && !Objects.equals(order.getUserVo().getUserId(), currentUser.getUserId())) {
            throw new CustomExceptions("无权查看该订单");
        }
        return order;
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

    // 合法的状态流转映射：key=当前状态, value=允许的目标状态集合
    private static final Map<String, java.util.Set<String>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT.getStatus(), java.util.Set.of(OrderStatus.PENDING_SHIPMENT.getStatus(), OrderStatus.CANCELED.getStatus()),
            OrderStatus.PENDING_SHIPMENT.getStatus(), java.util.Set.of(OrderStatus.RECEIVED.getStatus(), OrderStatus.CANCELED.getStatus()),
            OrderStatus.RECEIVED.getStatus(), java.util.Set.of(OrderStatus.WASHING.getStatus()),
            OrderStatus.WASHING.getStatus(), java.util.Set.of(OrderStatus.DRIED.getStatus(), OrderStatus.READY_FOR_PICKUP.getStatus()),
            OrderStatus.DRIED.getStatus(), java.util.Set.of(OrderStatus.IN_DELIVERY.getStatus()),
            OrderStatus.IN_DELIVERY.getStatus(), java.util.Set.of(OrderStatus.READY_FOR_PICKUP.getStatus()),
            OrderStatus.READY_FOR_PICKUP.getStatus(), java.util.Set.of(OrderStatus.COMPLETED.getStatus())
    );

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean updateOrderStatus(UpdateOrderStatus orderStatus) {
        log.info("管理员变更订单状态, orderId: {}, newStatus: {}", orderStatus.getOrderId(), orderStatus.getStatus());
        Orders orders = getById(orderStatus.getOrderId());
        if (orders == null) {
            throw new CustomExceptions("订单不存在");
        }

        // 校验状态流转是否合法
        java.util.Set<String> allowed = VALID_TRANSITIONS.get(orders.getStatus());
        if (allowed == null || !allowed.contains(orderStatus.getStatus())) {
            throw new CustomExceptions("非法的状态变更：" + OrderStatus.getDescriptionByStatus(orders.getStatus()) + " -> " + OrderStatus.getDescriptionByStatus(orderStatus.getStatus()));
        }

        // 订单到达待取件状态时，分配寄存柜和生成取件码
        if (OrderStatus.READY_FOR_PICKUP.getStatus().equals(orderStatus.getStatus())) {
            orders.setLockerId(findAndAssignFreeLocker(orders.getSchoolId()));
            orders.setStatus(orderStatus.getStatus());
            orders.setPickupCode(String.format("%d:%d:%s", orders.getUserId(), orders.getOrderId(), RandomUtil.randomInt(1000, 10000)));
            updateById(orders);
            return true;
        } else {
            // 订单完成或取消时释放寄存柜
            if (OrderStatus.COMPLETED.getStatus().equals(orderStatus.getStatus())
                    || OrderStatus.CANCELED.getStatus().equals(orderStatus.getStatus())) {
                lockersMapper.unLocker(orders.getLockerId(), LockerStatusEnum.FREE.getValue());
            }
            LambdaUpdateWrapper<Orders> updateWrapper = new LambdaUpdateWrapper<Orders>()
                    .eq(Orders::getOrderId, orderStatus.getOrderId())
                    .set(Orders::getStatus, orderStatus.getStatus());
            return update(updateWrapper);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean pickupOrder(OrderNextStatusFrom statusFrom, LoginUser loginUser) {
        return nextStatusOrder(statusFrom, loginUser, OrderStatus.COMPLETED.getStatus());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean shippingOrder(OrderNextStatusFrom statusFrom, LoginUser loginUser) {
        return nextStatusOrder(statusFrom, loginUser, OrderStatus.RECEIVED.getStatus());
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

    @Override
    public List<ShowOrderVo> getWashingOrderShowVo(LoginUser loginUser, int size) {
        Page<ShowOrderVo> page = new Page<>(1, size);
        return ordersMapper.getOrderList(page, OrderStatus.WASHING.getStatus(), loginUser.getUserId()).getRecords();
    }

    /**
     * 取消订单
     *
     * @param orderId
     * @param userId
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean cancelOrder(Long orderId, Long userId) {
        Orders orders = getById(orderId);
        if (orders == null
                || !orders.getStatus().equals(OrderStatus.PENDING_PAYMENT.getStatus())
                || !Objects.equals(orders.getUserId(), userId)) {
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

    @Override
    public Map<String, OrderGroupVo> getOrderSummary(LoginUser loginUser, int size) {
        Map<String, OrderGroupVo> result = new HashMap<>();

        // 全部订单
        List<ShowOrderVo> allOrders = getOrderListByStatus(null, loginUser.getUserId(), 1, size);
        long allTotal = countByUserIdAndStatus(loginUser.getUserId(), null);
        result.put("001", new OrderGroupVo(allOrders, allOrders.size() >= size, (int) allTotal));

        // 待支付
        List<ShowOrderVo> pendingPayment = getOrderListByStatus(OrderStatus.PENDING_PAYMENT.getStatus(), loginUser.getUserId(), 1, size);
        long pendingPaymentTotal = countByUserIdAndStatus(loginUser.getUserId(), OrderStatus.PENDING_PAYMENT.getStatus());
        result.put("0", new OrderGroupVo(pendingPayment, pendingPayment.size() >= size, (int) pendingPaymentTotal));

        // 待发货
        List<ShowOrderVo> pendingShipment = getOrderListByStatus(OrderStatus.PENDING_SHIPMENT.getStatus(), loginUser.getUserId(), 1, size);
        long pendingShipmentTotal = countByUserIdAndStatus(loginUser.getUserId(), OrderStatus.PENDING_SHIPMENT.getStatus());
        result.put("1", new OrderGroupVo(pendingShipment, pendingShipment.size() >= size, (int) pendingShipmentTotal));

        // 洗涤中
        List<ShowOrderVo> washing = getOrderListByStatus(OrderStatus.WASHING.getStatus(), loginUser.getUserId(), 1, size);
        long washingTotal = countByUserIdAndStatus(loginUser.getUserId(), OrderStatus.WASHING.getStatus());
        result.put("3", new OrderGroupVo(washing, washing.size() >= size, (int) washingTotal));

        // 待取件
        List<ShowOrderVo> readyForPickup = getOrderListByStatus(OrderStatus.READY_FOR_PICKUP.getStatus(), loginUser.getUserId(), 1, size);
        long readyForPickupTotal = countByUserIdAndStatus(loginUser.getUserId(), OrderStatus.READY_FOR_PICKUP.getStatus());
        result.put("6", new OrderGroupVo(readyForPickup, readyForPickup.size() >= size, (int) readyForPickupTotal));

        return result;
    }

    private List<ShowOrderVo> getOrderListByStatus(String status, Long userId, int page, int size) {
        Page<ShowOrderVo> pageParam = new Page<>(page, size);
        return ordersMapper.getOrderList(pageParam, status, userId).getRecords();
    }

    private long countByUserIdAndStatus(Long userId, String status) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getUserId, userId);
        if (status != null) {
            wrapper.eq(Orders::getStatus, status);
        }
        return count(wrapper);
    }

    //计算使用优惠券后的订单价格
    @Override
    public OrdersVo calculationOrder(Long userId, Long orderId, Long userCouponId) {
        log.info("订单计价, orderId: {}, userId: {}, couponId: {}", orderId, userId, userCouponId);
        OrdersVo order = getOrderByOrderId(orderId);
        if (order == null) throw new CustomExceptions("订单状态异常");

        // 不使用优惠券
        if (userCouponId == null || userCouponId == 0) {
            order.setPayPrice(order.getTotalPrice());
            return order;
        }

        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if ((userCoupon == null) || userCoupon.getIsUsed() || userCoupon.getExpiredAt().isBefore(LocalDateTime.now()) || !Objects.equals(userCoupon.getUserId(), userId)) {
            throw new CustomExceptions("优惠券异常");
        }
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            throw new CustomExceptions("优惠券不存在");
        }
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
        if (orders == null) {
            throw new CustomExceptions("订单不存在");
        }
        if (!Objects.equals(loginUser.getUserId(), orders.getUserId())) {
            log.warn("订单用户不匹配, orderId: {}, userId: {}", statusFrom.getOrderId(), loginUser.getUserId());
            throw new CustomExceptions("订单错误");
        }
        // 校验当前状态是否允许流转到目标状态
        java.util.Set<String> allowed = VALID_TRANSITIONS.get(orders.getStatus());
        if (allowed == null || !allowed.contains(nextStatus)) {
            throw new CustomExceptions("非法的状态变更：" + OrderStatus.getDescriptionByStatus(orders.getStatus()) + " -> " + OrderStatus.getDescriptionByStatus(nextStatus));
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
