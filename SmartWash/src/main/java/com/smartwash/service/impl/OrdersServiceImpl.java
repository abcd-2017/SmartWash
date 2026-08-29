package com.smartwash.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.PaymentStatus;
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
import com.smartwash.vo.order.OrderStatusCountVo;
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

    /**
     * "全部订单"分组标识（接口契约约定的魔法值 "001"，表示不限状态）：
     * 前端按该 key 解析订单摘要/订单列表响应，取值不可变更
     */
    private static final String ORDER_GROUP_ALL = "001";

    private final OrdersMapper ordersMapper;
    private final UsersMapper usersMapper;
    private final LockersMapper lockersMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final LaundryItemsMapper laundryItemsMapper;
    private final PaymentsMapper paymentsMapper;
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
        if (Objects.equals(orderListFrom.getStatus(), ORDER_GROUP_ALL)) {
            return ordersMapper.getOrderList(page, null, loginUser.getUserId()).getRecords();
        } else {
            return ordersMapper.getOrderList(page, orderListFrom.getStatus(), loginUser.getUserId()).getRecords();
        }

    }

    @Override
    public OrderItemCountVo getOrderItemCount(OrderItemCountFrom itemCountFrom, Long userId) {
        // 一次聚合查询取回该用户各状态订单数，按前端传入的状态码装配，替代原先 4 次逐状态 count（评审报告后端 #25）
        Map<String, Long> statusCountMap = countGroupByStatusMap(userId);
        OrderItemCountVo itemCountVo = new OrderItemCountVo();

        //待支付数量
        itemCountVo.setPendingPaymentCount(toStatusCount(statusCountMap, itemCountFrom.getPendingPaymentStatus()));
        //待清洗数量
        itemCountVo.setProcessingCount(toStatusCount(statusCountMap, itemCountFrom.getProcessingStatus()));
        //待取件数量
        itemCountVo.setPendingPickupCount(toStatusCount(statusCountMap, itemCountFrom.getPendingPickupStatus()));
        //待寄件数量
        itemCountVo.setShippedCount(toStatusCount(statusCountMap, itemCountFrom.getShippedStatus()));
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

    // 已支付且未终态的状态集合：管理员取消这些状态的订单时走退款链路
    private static final java.util.Set<String> PAID_IN_FLIGHT_STATUSES = java.util.Set.of(
            OrderStatus.PENDING_SHIPMENT.getStatus(),
            OrderStatus.RECEIVED.getStatus(),
            OrderStatus.WASHING.getStatus(),
            OrderStatus.DRIED.getStatus(),
            OrderStatus.IN_DELIVERY.getStatus(),
            OrderStatus.READY_FOR_PICKUP.getStatus()
    );

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean updateOrderStatus(UpdateOrderStatus orderStatus) {
        log.info("管理员变更订单状态, orderId: {}, newStatus: {}", orderStatus.getOrderId(), orderStatus.getStatus());
        Orders orders = getById(orderStatus.getOrderId());
        if (orders == null) {
            throw new CustomExceptions("订单不存在");
        }

        // 管理员取消：区分待支付（直接取消）与已支付（退款）两条链路，API 契约不变
        if (OrderStatus.CANCELED.getStatus().equals(orderStatus.getStatus())) {
            return adminCancelOrder(orders);
        }

        // 校验状态流转是否合法
        java.util.Set<String> allowed = VALID_TRANSITIONS.get(orders.getStatus());
        if (allowed == null || !allowed.contains(orderStatus.getStatus())) {
            throw new CustomExceptions("非法的状态变更：" + OrderStatus.getDescriptionByStatus(orders.getStatus()) + " -> " + OrderStatus.getDescriptionByStatus(orderStatus.getStatus()));
        }

        // 订单到达待取件状态时，分配寄存柜和生成取件码（条件 UPDATE 判影响行数作并发闸门）
        if (OrderStatus.READY_FOR_PICKUP.getStatus().equals(orderStatus.getStatus())) {
            // 订单原持有的寄件柜：CAS 命中后立即释放，否则旧柜将永久停留"使用中"造成资源泄漏
            Long previousLockerId = orders.getLockerId();
            Long lockerId = findAndAssignFreeLocker(orders.getSchoolId());
            String pickupCode = String.format("%d:%d:%s", orders.getUserId(), orders.getOrderId(), RandomUtil.randomInt(1000, 10000));
            // 状态 + 新柜子 + 取件码一次条件更新写入，expect 取读取快照的当前状态
            int assignedRows = ordersMapper.casStatusAssignPickup(orderStatus.getOrderId(), orders.getStatus(), orderStatus.getStatus(), lockerId, pickupCode);
            if (assignedRows == 0) {
                // 0 行说明订单已被并发取消/退款；上面分配的柜子随当前事务整体回滚，不会泄漏占用
                log.warn("管理员流转到待取件被拒绝：订单状态已并发变更, orderId: {}, expectStatus: {}",
                        orders.getOrderId(), orders.getStatus());
                throw new CustomExceptions("订单状态已变更，请刷新后重试");
            }
            // 释放寄件柜（取件柜从空闲柜中分配，不会与寄件柜相同）；释放失败仅告警不回滚流转
            releaseLockerSafely(previousLockerId, orders.getOrderId());
            log.info("管理员流转订单到待取件, orderId: {}, pickupLockerId: {}, releasedLockerId: {}", orders.getOrderId(), lockerId, previousLockerId);
            return true;
        }

        // 订单完成：释放寄存柜时必须同步清空 orders.locker_id，避免悬挂引用已释放柜子。
        // 复用 casStatus 条件更新（状态白名单已限定 READY_FOR_PICKUP → COMPLETED），
        // 与批次一取消/退款"CAS 闸门 + 释放柜子"口径一致，影响行数 0 说明发生并发状态变更。
        if (OrderStatus.COMPLETED.getStatus().equals(orderStatus.getStatus())) {
            int rows = ordersMapper.casStatus(orderStatus.getOrderId(), orders.getStatus(), OrderStatus.COMPLETED.getStatus());
            if (rows == 0) {
                throw new CustomExceptions("订单状态已变更，请刷新后重试");
            }
            releaseLockerSafely(orders.getLockerId(), orders.getOrderId());
            return true;
        }

        // 其余普通流转：统一 CAS 条件更新判影响行数（expect=读取快照状态，0 行说明已被并发取消/退款/流转，拒绝而非静默覆盖）。
        // 仅 set status、保留 locker_id——待支付→待寄件等在途流转柜子仍被订单持有，
        // 不能复用会固定清空 locker_id 的 casStatus，否则造成在途订单柜子悬挂泄漏
        int rows = ordersMapper.casStatusKeepLocker(orderStatus.getOrderId(), orders.getStatus(), orderStatus.getStatus());
        if (rows == 0) {
            log.warn("管理员流转订单被拒绝：订单状态已并发变更, orderId: {}, expectStatus: {}, targetStatus: {}",
                    orders.getOrderId(), orders.getStatus(), orderStatus.getStatus());
            throw new CustomExceptions("订单状态已变更，请刷新后重试");
        }
        return true;
    }

    /**
     * 管理员取消订单：待支付订单 CAS 直接取消并释放柜子；已支付且未终态订单走退款链路；
     * 终态（已完成/已取消/已退款）订单拒绝取消
     */
    private Boolean adminCancelOrder(Orders orders) {
        String currentStatus = orders.getStatus();
        // 1. 待支付订单：CAS 取消，防止与用户支付并发
        if (OrderStatus.PENDING_PAYMENT.getStatus().equals(currentStatus)) {
            int rows = ordersMapper.casStatus(orders.getOrderId(), currentStatus, OrderStatus.CANCELED.getStatus());
            if (rows == 0) {
                throw new CustomExceptions("订单状态已变更，请刷新后重试");
            }
            releaseLockerSafely(orders.getLockerId(), orders.getOrderId());
            // 取消超时任务（残留任务也会被 CAS 闸门拦截，此处提前清理）
            orderTimeoutManager.cancelTimeout(orders.getOrderId());
            log.info("管理员取消待支付订单, orderId: {}, lockerId: {}", orders.getOrderId(), orders.getLockerId());
            return true;
        }
        // 2. 已支付且未终态订单：退款
        if (PAID_IN_FLIGHT_STATUSES.contains(currentStatus)) {
            refundPaidOrder(orders);
            return true;
        }
        // 3. 终态或未知状态不可取消
        log.warn("管理员取消被拒绝：订单处于终态或未知状态, orderId: {}, status: {}", orders.getOrderId(), currentStatus);
        throw new CustomExceptions("当前订单状态不可取消：" + OrderStatus.getDescriptionByStatus(currentStatus));
    }

    /**
     * 管理员取消已支付订单的退款链路：
     * 1) CAS 流转到已退款作为防重复退款闸门；2) 退款金额以支付流水为准；
     * 3) 退还用户余额；4) 还原订单所用优惠券；5) 释放寄存柜。
     * 注意：本方法由 updateOrderStatus（@Transactional）同类内部调用，事务边界由调用方保证，
     * 任一步骤抛异常将连同状态 CAS 一起整体回滚。
     */
    private void refundPaidOrder(Orders orders) {
        Long orderId = orders.getOrderId();
        // 1. CAS 防重复退款闸门：仅当前已支付状态可流转为已退款，0 行说明已被并发取消/退款
        int rows = ordersMapper.casStatus(orderId, orders.getStatus(), OrderStatus.REFUNDED.getStatus());
        if (rows == 0) {
            throw new CustomExceptions("订单状态已变更，请刷新后重试");
        }
        // 2. 退款金额以库内支付流水为准，不信任订单快照或前端金额
        // 仅认经支付网关产生的流水（out_trade_no 非空）：管理端手工新增的流水无幂等键，
        // 若一并采信，管理员可伪造任意金额 SUCCESS 流水后取消订单凭空退款。
        // 改造前支付的历史订单无 out_trade_no，如需退款走线下人工处理。
        Payments payment = paymentsMapper.selectOne(new LambdaQueryWrapper<Payments>()
                .eq(Payments::getOrderId, orderId)
                .eq(Payments::getStatus, PaymentStatus.SUCCESS.getStatus())
                .isNotNull(Payments::getOutTradeNo)
                .orderByDesc(Payments::getPaidAt)
                .last("limit 1"));
        if (payment == null || payment.getAmount() == null) {
            log.error("订单缺少成功支付流水，退款中止并整体回滚, orderId: {}", orderId);
            throw new CustomExceptions("支付流水异常，无法退款");
        }
        BigDecimal refundAmount = payment.getAmount();
        // 3. 退还用户余额（金额为 0 时无需入账）
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (usersMapper.incrUserBalance(orders.getUserId(), refundAmount) == 0) {
                throw new CustomExceptions("余额退还失败，请稍后重试");
            }
        }
        // 4. 还原订单使用的优惠券（MyBatis-Plus 内置方法；券记录不存在则跳过并告警）
        if (orders.getUserCouponId() != null) {
            UserCoupon userCoupon = userCouponMapper.selectById(orders.getUserCouponId());
            if (userCoupon != null) {
                userCoupon.setIsUsed(false);
                userCouponMapper.updateById(userCoupon);
            } else {
                log.warn("退款时未找到优惠券记录，跳过还原, orderId: {}, userCouponId: {}", orderId, orders.getUserCouponId());
            }
        }
        // 5. 释放寄存柜（CAS 已清 locker_id，此处用读取时留存的 ID 释放）
        releaseLockerSafely(orders.getLockerId(), orderId);
        log.info("管理员取消已支付订单并退款完成, orderId: {}, userId: {}, 退款金额: {}", orderId, orders.getUserId(), refundAmount);
    }

    /**
     * 释放寄存柜：失败仅告警不抛出，避免影响已完成的取消/退款结果；柜子残留占用可由管理端人工修复
     */
    private void releaseLockerSafely(Long lockerId, Long orderId) {
        if (lockerId == null) {
            return;
        }
        try {
            lockersMapper.unLocker(lockerId, LockerStatusEnum.FREE.getValue());
            log.info("已释放寄存柜, orderId: {}, lockerId: {}", orderId, lockerId);
        } catch (Exception e) {
            log.error("释放寄存柜失败, orderId: {}, lockerId: {}", orderId, lockerId, e);
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
     * 取消订单（用户端）：仅待支付订单可取消。
     * 存在性与归属用读校验，状态流转用 CAS 条件更新作并发闸门，防止取消与支付竞态导致已支付订单被取消
     *
     * @param orderId 订单 ID
     * @param userId  当前用户 ID
     * @return 是否取消成功
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean cancelOrder(Long orderId, Long userId) {
        Orders orders = getById(orderId);
        if (orders == null || !Objects.equals(orders.getUserId(), userId)) {
            throw new CustomExceptions("订单状态异常");
        }
        // CAS 闸门：待支付 -> 已取消，影响行数为 0 说明订单已被支付/取消/状态变更
        int rows = ordersMapper.casStatus(orderId, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus());
        if (rows == 0) {
            log.info("用户取消订单失败：订单状态已变更, orderId: {}, userId: {}", orderId, userId);
            throw new CustomExceptions("订单状态已变更，请刷新后重试");
        }
        log.info("订单已取消, orderId: {}, userId: {}", orderId, userId);
        //1.解除寄存柜占用（CAS 已清 locker_id，此处用查询留存的 ID 释放；失败仅告警）
        releaseLockerSafely(orders.getLockerId(), orderId);

        //2.取消超时任务（残留任务也会被 CAS 闸门拦截，此处提前清理）
        orderTimeoutManager.cancelTimeout(orderId);
        return true;
    }

    @Override
    public Map<String, OrderGroupVo> getOrderSummary(LoginUser loginUser, int size) {
        Map<String, OrderGroupVo> result = new HashMap<>();
        // 一次聚合查询取回该用户各状态订单数，替代原先 5 次逐状态 count（评审报告后端 #25）
        Map<String, Long> statusCountMap = countGroupByStatusMap(loginUser.getUserId());

        // 全部订单
        List<ShowOrderVo> allOrders = getOrderListByStatus(null, loginUser.getUserId(), 1, size);
        long allTotal = statusCountMap.values().stream().mapToLong(Long::longValue).sum();
        result.put(ORDER_GROUP_ALL, new OrderGroupVo(allOrders, allOrders.size() >= size, (int) allTotal));

        // 待支付
        putOrderGroup(result, statusCountMap, OrderStatus.PENDING_PAYMENT.getStatus(), loginUser.getUserId(), size);
        // 待寄件
        putOrderGroup(result, statusCountMap, OrderStatus.PENDING_SHIPMENT.getStatus(), loginUser.getUserId(), size);
        // 洗涤中
        putOrderGroup(result, statusCountMap, OrderStatus.WASHING.getStatus(), loginUser.getUserId(), size);
        // 待取件
        putOrderGroup(result, statusCountMap, OrderStatus.READY_FOR_PICKUP.getStatus(), loginUser.getUserId(), size);

        return result;
    }

    /**
     * 装配单个状态分组的摘要项：分组 key 即状态码（前端契约），数量取自聚合查询结果
     */
    private void putOrderGroup(Map<String, OrderGroupVo> result, Map<String, Long> statusCountMap, String status, Long userId, int size) {
        List<ShowOrderVo> orders = getOrderListByStatus(status, userId, 1, size);
        long total = statusCountMap.getOrDefault(status, 0L);
        result.put(status, new OrderGroupVo(orders, orders.size() >= size, (int) total));
    }

    private List<ShowOrderVo> getOrderListByStatus(String status, Long userId, int page, int size) {
        Page<ShowOrderVo> pageParam = new Page<>(page, size);
        return ordersMapper.getOrderList(pageParam, status, userId).getRecords();
    }

    /**
     * 聚合查询（GROUP BY status 单条 SQL）各状态订单数并转为 status -> count 映射
     */
    private Map<String, Long> countGroupByStatusMap(Long userId) {
        return ordersMapper.countGroupByStatus(userId).stream()
                .collect(Collectors.toMap(OrderStatusCountVo::getStatus, OrderStatusCountVo::getCount));
    }

    private Integer toStatusCount(Map<String, Long> statusCountMap, String status) {
        return Math.toIntExact(statusCountMap.getOrDefault(status, 0L));
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
        //2.条件更新订单状态（CAS 闸门：0 行说明状态已被并发变更，如管理员已退款/取消，禁止把终态覆写为已完成）
        int rows = ordersMapper.nextStatus(orders.getOrderId(), orders.getStatus(), nextStatus);
        if (rows == 0) {
            log.warn("订单状态并发变更，取件/寄件流转被拒绝, orderId: {}, expectStatus: {}, ip快照状态可能已过期", statusFrom.getOrderId(), orders.getStatus());
            throw new CustomExceptions("订单状态已变更，请刷新后重试");
        }

        //3.解除被占用的寄存柜（SQL 已清 locker_id，此处用读取时留存的 ID 释放；释放失败不影响完成态，仅告警）
        releaseLockerSafely(orders.getLockerId(), orders.getOrderId());
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
}
