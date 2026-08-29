package com.smartwash.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.PaymentStatus;
import com.smartwash.entity.Lockers;
import com.smartwash.entity.Orders;
import com.smartwash.entity.Payments;
import com.smartwash.entity.UserCoupon;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.order.OrderItemCountFrom;
import com.smartwash.from.order.UpdateOrderStatus;
import com.smartwash.mapper.CouponMapper;
import com.smartwash.mapper.LaundryItemsMapper;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.mapper.OrdersMapper;
import com.smartwash.mapper.PaymentsMapper;
import com.smartwash.mapper.UserCouponMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.task.OrderTimeoutManager;
import com.smartwash.utils.LoginUser;
import com.smartwash.vo.order.OrderGroupVo;
import com.smartwash.vo.order.OrderItemCountVo;
import com.smartwash.vo.order.OrderStatusCountVo;
import com.smartwash.vo.order.ShowOrderVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单取消 / 管理员退款链路 CAS 闸门回归测试（评审报告后端 P0 #1、#2、#7）。
 * 闸门语义：
 * 1) 用户取消与超时取消：casStatus 影响行数 0 → 抛异常且不得释放柜子；
 * 2) 管理员取消已支付订单：CAS（已支付→已退款）作防重复退款闸门，成功时依次退余额、还券、释放柜子；
 * 3) 状态机白名单外的流转必须被拒绝。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrdersServiceImpl 取消/退款 CAS 闸门与状态机测试")
class OrdersServiceImplTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long LOCKER_ID = 55L;
    private static final Long USER_COUPON_ID = 77L;

    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private UsersMapper usersMapper;
    @Mock
    private LockersMapper lockersMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private CouponMapper couponMapper;
    @Mock
    private LaundryItemsMapper laundryItemsMapper;
    @Mock
    private PaymentsMapper paymentsMapper;
    @Mock
    private OrderTimeoutManager orderTimeoutManager;

    private OrdersServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrdersServiceImpl(ordersMapper, usersMapper, lockersMapper, userCouponMapper,
                couponMapper, laundryItemsMapper, paymentsMapper, orderTimeoutManager);
        // ServiceImpl.getById/save 等内置方法走父类 baseMapper 字段，构造注入不会填充，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", ordersMapper);
    }

    private Orders order(String status) {
        Orders order = new Orders();
        order.setOrderId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setSchoolId(100L);
        order.setLockerId(LOCKER_ID);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("50"));
        order.setPayPrice(new BigDecimal("50"));
        return order;
    }

    private LoginUser loginUser() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_user"));
        return new LoginUser(USER_ID, "user-13800138000", "pwd", "13800138000", "user", authorities);
    }

    // ==================== 用户取消订单 ====================

    @Test
    @DisplayName("用户取消：CAS 未命中（已被支付/取消）时抛异常，且不释放柜子、不清理超时任务")
    void cancelOrder_casMiss_throwsAndKeepsLocker() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_PAYMENT.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.cancelOrder(ORDER_ID, USER_ID),
                "CAS 影响行数为 0 时用户取消必须失败");

        assertEquals("订单状态已变更，请刷新后重试", ex.getMessage(), "提示应引导用户刷新后重试");
        // 关键回归点：闸门未命中时释放柜子会把已支付订单占用的柜子错误释放
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
        verify(orderTimeoutManager, never()).cancelTimeout(anyLong());
    }

    @Test
    @DisplayName("用户取消：CAS 命中时释放寄存柜并清理超时任务")
    void cancelOrder_casHit_releasesLockerAndCancelsTimeout() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_PAYMENT.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(1);

        Boolean result = service.cancelOrder(ORDER_ID, USER_ID);

        assertTrue(result, "CAS 命中时用户取消应成功");
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
        verify(orderTimeoutManager, times(1)).cancelTimeout(ORDER_ID);
    }

    @Test
    @DisplayName("用户取消他人订单：直接拒绝，不触发 CAS")
    void cancelOrder_notOwner_rejectedWithoutCas() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_PAYMENT.getStatus()));

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.cancelOrder(ORDER_ID, 999L));

        assertEquals("订单状态异常", ex.getMessage());
        verify(ordersMapper, never()).casStatus(anyLong(), anyString(), anyString());
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("用户取消不存在的订单：拒绝且不触发 CAS")
    void cancelOrder_orderMissing_rejected() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(null);

        assertThrows(CustomExceptions.class, () -> service.cancelOrder(ORDER_ID, USER_ID));
        verify(ordersMapper, never()).casStatus(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("并发用户取消同一订单：仅 CAS 抢闸成功的 1 个线程取消成功并释放柜子")
    void cancelOrder_concurrent_onlyWinnerSucceeds() throws Exception {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_PAYMENT.getStatus()));
        AtomicInteger remainingHits = new AtomicInteger(1);
        when(ordersMapper.casStatus(eq(ORDER_ID), eq(OrderStatus.PENDING_PAYMENT.getStatus()), eq(OrderStatus.CANCELED.getStatus())))
                .thenAnswer(inv -> remainingHits.compareAndSet(1, 0) ? 1 : 0);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (service.cancelOrder(ORDER_ID, USER_ID)) {
                        successCount.incrementAndGet();
                    }
                } catch (CustomExceptions e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发取消应在 10 秒内全部结束");
        pool.shutdownNow();

        assertEquals(1, successCount.get(), "同一订单并发取消只能有 1 个赢家（其余必须被 CAS 闸门拒绝）");
        assertEquals(threads - 1, failCount.get(), "未抢到闸门的取消请求必须全部抛业务异常");
        verify(ordersMapper, times(threads)).casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus());
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
        verify(orderTimeoutManager, times(1)).cancelTimeout(ORDER_ID);
    }

    // ==================== 管理员取消订单 ====================

    private UpdateOrderStatus cancelRequest() {
        UpdateOrderStatus from = new UpdateOrderStatus();
        from.setOrderId(ORDER_ID);
        from.setStatus(OrderStatus.CANCELED.getStatus());
        return from;
    }

    @Test
    @DisplayName("管理员取消待支付订单：CAS 命中时释放柜子并清理超时任务")
    void adminCancelPending_casHit_releasesLocker() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_PAYMENT.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(1);

        Boolean result = service.updateOrderStatus(cancelRequest());

        assertTrue(result, "管理员取消待支付订单应成功");
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
        verify(orderTimeoutManager, times(1)).cancelTimeout(ORDER_ID);
        // 待支付订单无支付流水，不得触发任何退款入账
        verify(usersMapper, never()).incrUserBalance(anyLong(), any());
    }

    @Test
    @DisplayName("管理员取消待支付订单：CAS 未命中（与支付并发）时抛异常且不释放柜子")
    void adminCancelPending_casMiss_throwsAndKeepsLocker() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_PAYMENT.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.updateOrderStatus(cancelRequest()));

        assertEquals("订单状态已变更，请刷新后重试", ex.getMessage());
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
        verify(orderTimeoutManager, never()).cancelTimeout(anyLong());
    }

    // ==================== 管理员取消已支付订单（退款链路） ====================

    private Payments successPayment(BigDecimal amount) {
        Payments payment = new Payments();
        payment.setOrderId(ORDER_ID);
        payment.setUserId(USER_ID);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.SUCCESS.getStatus());
        return payment;
    }

    private UserCoupon usedCoupon() {
        UserCoupon coupon = new UserCoupon();
        coupon.setUserCouponId(USER_COUPON_ID);
        coupon.setUserId(USER_ID);
        coupon.setCouponId(8L);
        coupon.setIsUsed(true);
        return coupon;
    }

    @Test
    @DisplayName("管理员取消已支付订单：CAS 抢闸成功后依次 退余额→还券(isUsed=false)→释放柜子")
    void adminRefund_success_executesFullChainInOrder() {
        Orders paidOrder = order(OrderStatus.PENDING_SHIPMENT.getStatus());
        paidOrder.setUserCouponId(USER_COUPON_ID);
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(paidOrder);
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_SHIPMENT.getStatus(), OrderStatus.REFUNDED.getStatus()))
                .thenReturn(1);
        when(paymentsMapper.selectOne(any())).thenReturn(successPayment(new BigDecimal("50")));
        when(usersMapper.incrUserBalance(USER_ID, new BigDecimal("50"))).thenReturn(1);
        when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(usedCoupon());

        Boolean result = service.updateOrderStatus(cancelRequest());

        assertTrue(result, "管理员取消已支付订单（退款）应成功");
        // 顺序断言：CAS 闸门 → 退余额 → 还券 → 释放柜子
        InOrder inOrder = inOrder(ordersMapper, usersMapper, userCouponMapper, lockersMapper);
        inOrder.verify(ordersMapper).casStatus(ORDER_ID, OrderStatus.PENDING_SHIPMENT.getStatus(), OrderStatus.REFUNDED.getStatus());
        inOrder.verify(usersMapper).incrUserBalance(USER_ID, new BigDecimal("50"));
        inOrder.verify(userCouponMapper).updateById(any(UserCoupon.class));
        inOrder.verify(lockersMapper).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());

        // 还券必须把 isUsed 置回 false（券恢复可用）
        org.mockito.ArgumentCaptor<UserCoupon> captor = org.mockito.ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper).updateById(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getIsUsed(), "退款还券后优惠券必须恢复未使用状态");
        // 退款链路不清理超时任务：该任务在支付成功回调时已被清理，此处不得重复操作
        verify(orderTimeoutManager, never()).cancelTimeout(anyLong());
    }

    @Test
    @DisplayName("管理员取消已支付订单：退款 CAS 未命中（已被并发退款/取消）时抛异常，不得重复退余额")
    void adminRefund_casMiss_throwsAndNoDoubleRefund() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_SHIPMENT.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_SHIPMENT.getStatus(), OrderStatus.REFUNDED.getStatus()))
                .thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.updateOrderStatus(cancelRequest()));

        assertEquals("订单状态已变更，请刷新后重试", ex.getMessage());
        verify(usersMapper, never()).incrUserBalance(anyLong(), any());
        verify(userCouponMapper, never()).updateById(any(UserCoupon.class));
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("管理员取消已支付订单：无 SUCCESS 支付流水时拒绝退款，不退余额")
    void adminRefund_noSuccessPayment_rejected() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_SHIPMENT.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_SHIPMENT.getStatus(), OrderStatus.REFUNDED.getStatus()))
                .thenReturn(1);
        when(paymentsMapper.selectOne(any())).thenReturn(null);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.updateOrderStatus(cancelRequest()));

        assertEquals("支付流水异常，无法退款", ex.getMessage(), "退款金额必须以 SUCCESS 支付流水为准，缺失时拒绝退款");
        verify(usersMapper, never()).incrUserBalance(anyLong(), any());
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("管理员取消已支付订单：余额退还影响 0 行时抛异常（调用方事务整体回滚）")
    void adminRefund_incrBalanceFails_throws() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_SHIPMENT.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_SHIPMENT.getStatus(), OrderStatus.REFUNDED.getStatus()))
                .thenReturn(1);
        when(paymentsMapper.selectOne(any())).thenReturn(successPayment(new BigDecimal("50")));
        when(usersMapper.incrUserBalance(USER_ID, new BigDecimal("50"))).thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.updateOrderStatus(cancelRequest()));

        assertEquals("余额退还失败，请稍后重试", ex.getMessage());
    }

    @Test
    @DisplayName("管理员取消已支付订单：支付金额为 0 时跳过入账，但仍需还券并释放柜子")
    void adminRefund_zeroAmount_skipsBalanceButRestoresCouponAndLocker() {
        Orders paidOrder = order(OrderStatus.PENDING_SHIPMENT.getStatus());
        paidOrder.setUserCouponId(USER_COUPON_ID);
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(paidOrder);
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_SHIPMENT.getStatus(), OrderStatus.REFUNDED.getStatus()))
                .thenReturn(1);
        when(paymentsMapper.selectOne(any())).thenReturn(successPayment(BigDecimal.ZERO));
        when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(usedCoupon());

        Boolean result = service.updateOrderStatus(cancelRequest());

        assertTrue(result, "0 元退款应视为取消成功");
        verify(usersMapper, never()).incrUserBalance(anyLong(), any());
        verify(userCouponMapper, times(1)).updateById(any(UserCoupon.class));
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
    }

    // ==================== 状态机白名单 ====================

    @Test
    @DisplayName("非法状态流转（待支付→已完成，白名单外）必须被拒绝")
    void updateOrderStatus_illegalTransition_rejected() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING_PAYMENT.getStatus()));
        UpdateOrderStatus from = new UpdateOrderStatus();
        from.setOrderId(ORDER_ID);
        from.setStatus(OrderStatus.COMPLETED.getStatus());

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.updateOrderStatus(from));

        assertEquals("非法的状态变更：待支付 -> 已完成", ex.getMessage(),
                "状态机白名单外的 nextStatus 必须被拒绝，防止跳过支付/清洗环节");
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("终态订单（已完成）不可取消")
    void updateOrderStatus_cancelTerminalOrder_rejected() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.COMPLETED.getStatus()));

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.updateOrderStatus(cancelRequest()));

        assertEquals("当前订单状态不可取消：已完成", ex.getMessage());
        verify(ordersMapper, never()).casStatus(anyLong(), anyString(), anyString());
    }

    // ==================== 管理员普通流转 CAS（批次三：普通流转也走条件更新闸门） ====================

    private UpdateOrderStatus transitionRequest(String targetStatus) {
        UpdateOrderStatus from = new UpdateOrderStatus();
        from.setOrderId(ORDER_ID);
        from.setStatus(targetStatus);
        return from;
    }

    @Test
    @DisplayName("管理员普通流转：条件更新命中时返回 true，且不释放柜子（在途流转柜子仍被持有）")
    void updateOrderStatus_normalTransition_casHit() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.RECEIVED.getStatus()));
        when(ordersMapper.casStatusKeepLocker(ORDER_ID, OrderStatus.RECEIVED.getStatus(), OrderStatus.WASHING.getStatus()))
                .thenReturn(1);

        Boolean result = service.updateOrderStatus(transitionRequest(OrderStatus.WASHING.getStatus()));

        assertTrue(result, "普通流转条件更新命中应成功");
        verify(ordersMapper, times(1)).casStatusKeepLocker(ORDER_ID, OrderStatus.RECEIVED.getStatus(), OrderStatus.WASHING.getStatus());
        // 清洗中流转不涉及柜子释放
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("管理员普通流转：条件更新 0 行（已被并发取消/退款）时抛异常，不得静默覆写终态")
    void updateOrderStatus_normalTransition_casMiss_throws() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.RECEIVED.getStatus()));
        when(ordersMapper.casStatusKeepLocker(ORDER_ID, OrderStatus.RECEIVED.getStatus(), OrderStatus.WASHING.getStatus()))
                .thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.updateOrderStatus(transitionRequest(OrderStatus.WASHING.getStatus())));

        assertEquals("订单状态已变更，请刷新后重试", ex.getMessage());
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("管理员流转到待取件：分配取件柜并条件写入，命中时释放原寄件柜防泄漏")
    void updateOrderStatus_transitionToReadyForPickup_casHit() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.WASHING.getStatus()));
        Lockers freeLocker = new Lockers();
        freeLocker.setLockerId(99L);
        when(lockersMapper.getFreeLockerBySchoolIdForUpdate(100L)).thenReturn(freeLocker);
        when(ordersMapper.casStatusAssignPickup(eq(ORDER_ID), eq(OrderStatus.WASHING.getStatus()),
                eq(OrderStatus.READY_FOR_PICKUP.getStatus()), eq(99L), anyString())).thenReturn(1);

        Boolean result = service.updateOrderStatus(transitionRequest(OrderStatus.READY_FOR_PICKUP.getStatus()));

        assertTrue(result, "流转到待取件的条件更新命中应成功");
        // 原寄件柜（夹具 lockerId=LOCKER_ID）在分配取件柜后必须释放，否则永久停留"使用中"
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
    }

    @Test
    @DisplayName("管理员流转到待取件：条件更新 0 行时抛异常（已分配柜子随事务回滚，此处验证不释放任何柜）")
    void updateOrderStatus_transitionToReadyForPickup_casMiss_throws() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.WASHING.getStatus()));
        Lockers freeLocker = new Lockers();
        freeLocker.setLockerId(99L);
        when(lockersMapper.getFreeLockerBySchoolIdForUpdate(100L)).thenReturn(freeLocker);
        when(ordersMapper.casStatusAssignPickup(eq(ORDER_ID), eq(OrderStatus.WASHING.getStatus()),
                eq(OrderStatus.READY_FOR_PICKUP.getStatus()), eq(99L), anyString())).thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.updateOrderStatus(transitionRequest(OrderStatus.READY_FOR_PICKUP.getStatus())));

        assertEquals("订单状态已变更，请刷新后重试", ex.getMessage());
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("管理员订单完成：CAS 命中时释放寄存柜（清空 locker_id 闸门口径）")
    void updateOrderStatus_complete_casHit_releasesLocker() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.READY_FOR_PICKUP.getStatus()));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.READY_FOR_PICKUP.getStatus(), OrderStatus.COMPLETED.getStatus()))
                .thenReturn(1);

        Boolean result = service.updateOrderStatus(transitionRequest(OrderStatus.COMPLETED.getStatus()));

        assertTrue(result, "订单完成 CAS 命中应成功");
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
    }

    // ==================== 聚合查询：订单摘要与条目计数（批次三 SQL 合并回归） ====================

    @Test
    @DisplayName("getOrderItemCount：单条 GROUP BY 聚合装配各状态计数，缺失状态按 0 返回")
    void getOrderItemCount_usesGroupByAggregate() {
        when(ordersMapper.countGroupByStatus(USER_ID)).thenReturn(List.of(
                new OrderStatusCountVo(OrderStatus.PENDING_PAYMENT.getStatus(), 2L),
                new OrderStatusCountVo(OrderStatus.WASHING.getStatus(), 1L),
                new OrderStatusCountVo(OrderStatus.READY_FOR_PICKUP.getStatus(), 3L)
        ));
        OrderItemCountFrom from = new OrderItemCountFrom();
        from.setPendingPaymentStatus(OrderStatus.PENDING_PAYMENT.getStatus());
        from.setProcessingStatus(OrderStatus.WASHING.getStatus());
        from.setPendingPickupStatus(OrderStatus.READY_FOR_PICKUP.getStatus());
        from.setShippedStatus(OrderStatus.PENDING_SHIPMENT.getStatus());

        OrderItemCountVo vo = service.getOrderItemCount(from, USER_ID);

        assertEquals(2, vo.getPendingPaymentCount(), "待支付计数取自聚合查询");
        assertEquals(1, vo.getProcessingCount(), "待清洗计数取自聚合查询");
        assertEquals(3, vo.getPendingPickupCount(), "待取件计数取自聚合查询");
        assertEquals(0, vo.getShippedCount(), "聚合结果缺失的状态应计 0，与原逐状态 count 语义一致");
        // 仅 1 次聚合查询，不得再逐状态 count
        verify(ordersMapper, times(1)).countGroupByStatus(USER_ID);
    }

    @Test
    @DisplayName("getOrderSummary：单条聚合提供各分组 total，分组 key 与原契约一致（001/0/1/3/6）")
    void getOrderSummary_usesGroupByAggregate_withContractKeys() {
        when(ordersMapper.countGroupByStatus(USER_ID)).thenReturn(List.of(
                new OrderStatusCountVo(OrderStatus.PENDING_PAYMENT.getStatus(), 2L),
                new OrderStatusCountVo(OrderStatus.PENDING_SHIPMENT.getStatus(), 1L),
                new OrderStatusCountVo(OrderStatus.WASHING.getStatus(), 3L),
                new OrderStatusCountVo(OrderStatus.READY_FOR_PICKUP.getStatus(), 4L)
        ));
        Page<ShowOrderVo> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        when(ordersMapper.getOrderList(any(), any(), eq(USER_ID))).thenReturn(emptyPage);

        Map<String, OrderGroupVo> summary = service.getOrderSummary(loginUser(), 10);

        assertEquals(5, summary.size(), "分组数与原实现一致：全部+4 个状态分组");
        assertEquals(10, summary.get("001").getTotal(), "全部订单 total 应为各状态计数之和");
        assertEquals(2, summary.get(OrderStatus.PENDING_PAYMENT.getStatus()).getTotal());
        assertEquals(1, summary.get(OrderStatus.PENDING_SHIPMENT.getStatus()).getTotal());
        assertEquals(3, summary.get(OrderStatus.WASHING.getStatus()).getTotal());
        assertEquals(4, summary.get(OrderStatus.READY_FOR_PICKUP.getStatus()).getTotal());
        assertFalse(summary.get("001").isHasMore(), "空列表 hasMore 应为 false，与原语义一致");
        assertNull(summary.get(OrderStatus.COMPLETED.getStatus()), "不得出现契约外的分组 key");
        verify(ordersMapper, times(1)).countGroupByStatus(USER_ID);
    }
}
