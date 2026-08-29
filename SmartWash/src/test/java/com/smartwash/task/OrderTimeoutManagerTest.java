package com.smartwash.task;

import com.smartwash.common.LockerStatusEnum;
import com.smartwash.common.OrderStatus;
import com.smartwash.entity.Orders;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.mapper.OrdersMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单超时取消 CAS 闸门回归测试（评审报告后端 P0 #1）。
 * 闸门语义：超时回调仅当 casStatus 条件更新（待支付→已取消）影响行数为 1 时才允许取消并释放柜子，
 * 影响行数为 0 说明订单已被支付/手动取消并发完成，必须放弃取消且不得释放柜子。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutManager 超时取消 CAS 闸门测试")
class OrderTimeoutManagerTest {

    private static final Long ORDER_ID = 1L;
    private static final Long LOCKER_ID = 55L;

    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private LockersMapper lockersMapper;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private OrderTimeoutManager manager;

    @BeforeEach
    void setUp() {
        manager = new OrderTimeoutManager();
        ReflectionTestUtils.setField(manager, "ordersMapper", ordersMapper);
        ReflectionTestUtils.setField(manager, "lockersMapper", lockersMapper);
        ReflectionTestUtils.setField(manager, "taskScheduler", taskScheduler);
    }

    /** 通过反射调用私有方法 cancelIfUnpaid（超时任务回调体） */
    private void invokeCancelIfUnpaid(Long orderId) {
        ReflectionTestUtils.invokeMethod(manager, "cancelIfUnpaid", orderId);
    }

    private Orders pendingOrder(Long orderId, Long lockerId) {
        Orders order = new Orders();
        order.setOrderId(orderId);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getStatus());
        order.setLockerId(lockerId);
        return order;
    }

    @Test
    @DisplayName("CAS 命中（影响 1 行）：超时取消生效并释放寄存柜")
    void cancelIfUnpaid_casHit_releasesLocker() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(pendingOrder(ORDER_ID, LOCKER_ID));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(1);

        invokeCancelIfUnpaid(ORDER_ID);

        // 影响行数为 1 说明本线程抢到取消权，必须释放柜子（状态置空闲）
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
    }

    @Test
    @DisplayName("CAS 未命中（影响 0 行，订单已被支付/取消）：必须放弃取消且绝不释放寄存柜")
    void cancelIfUnpaid_casMiss_mustNotReleaseLocker() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(pendingOrder(ORDER_ID, LOCKER_ID));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(0);

        invokeCancelIfUnpaid(ORDER_ID);

        // 关键回归点：CAS 闸门未抢到时释放柜子会把已支付订单占用的柜子错误释放
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("订单不存在：静默跳过，不触发 CAS 也不释放柜子")
    void cancelIfUnpaid_orderMissing_skipsSilently() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(null);

        assertDoesNotThrow(() -> invokeCancelIfUnpaid(ORDER_ID), "订单不存在时应静默跳过，不向调度器抛异常");

        verify(ordersMapper, never()).casStatus(anyLong(), anyString(), anyString());
        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("CAS 命中但订单无寄存柜：正常取消，不调用释放柜子")
    void cancelIfUnpaid_casHitWithoutLocker_skipsRelease() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(pendingOrder(ORDER_ID, null));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(1);

        assertDoesNotThrow(() -> invokeCancelIfUnpaid(ORDER_ID));

        verify(lockersMapper, never()).unLocker(anyLong(), anyString());
    }

    @Test
    @DisplayName("CAS 命中但释放柜子异常：不向调度器传播异常（柜子残留可人工修复）")
    void cancelIfUnpaid_releaseLockerFails_swallowException() {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(pendingOrder(ORDER_ID, LOCKER_ID));
        when(ordersMapper.casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus()))
                .thenReturn(1);
        doThrow(new RuntimeException("DB down")).when(lockersMapper).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());

        assertDoesNotThrow(() -> invokeCancelIfUnpaid(ORDER_ID), "释放柜子失败不应让超时任务向调度器抛异常");
    }

    @Test
    @DisplayName("并发超时回调同一订单：仅 CAS 抢闸成功的线程释放寄存柜一次")
    void cancelIfUnpaid_concurrent_onlyCasWinnerReleasesLocker() throws Exception {
        when(ordersMapper.selectById(ORDER_ID)).thenReturn(pendingOrder(ORDER_ID, LOCKER_ID));
        // 用 AtomicInteger 的 CAS 模拟数据库条件更新的行级原子性：全部线程中只有 1 个拿到影响行数 1
        AtomicInteger remainingHits = new AtomicInteger(1);
        when(ordersMapper.casStatus(eq(ORDER_ID), eq(OrderStatus.PENDING_PAYMENT.getStatus()), eq(OrderStatus.CANCELED.getStatus())))
                .thenAnswer(inv -> remainingHits.compareAndSet(1, 0) ? 1 : 0);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    invokeCancelIfUnpaid(ORDER_ID);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发回调应在 10 秒内全部结束");
        pool.shutdownNow();

        // 每个线程都发起了 CAS（对应真实场景中并发回调都会尝试条件更新）
        verify(ordersMapper, times(threads)).casStatus(ORDER_ID, OrderStatus.PENDING_PAYMENT.getStatus(), OrderStatus.CANCELED.getStatus());
        // 关键断言：只有闸门赢家（1 个）释放柜子，绝不出现多次释放
        verify(lockersMapper, times(1)).unLocker(LOCKER_ID, LockerStatusEnum.FREE.getValue());
    }

    @Test
    @DisplayName("scheduleTimeout 注册任务，cancelTimeout 注销并取消 Future")
    void scheduleAndCancelTimeout() {
        // TaskScheduler.schedule 的泛型返回值存在捕获问题，用 thenAnswer 绕开
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(inv -> scheduledFuture);

        manager.scheduleTimeout(100L);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));

        manager.cancelTimeout(100L);
        // 支付成功/手动取消后清理超时任务，必须以不中断方式取消
        verify(scheduledFuture, times(1)).cancel(false);
    }

    @Test
    @DisplayName("cancelTimeout 对未注册订单静默无异常")
    void cancelTimeout_unknownOrder_noop() {
        assertDoesNotThrow(() -> manager.cancelTimeout(999L));
        verifyNoInteractions(scheduledFuture);
    }
}
