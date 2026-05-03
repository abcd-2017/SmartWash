package com.smartwash.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.common.OrderStatus;
import com.smartwash.entity.Orders;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.mapper.OrdersMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 订单支付超时管理器 — 基于 TaskScheduler 一次性调度，零轮询
 */
@Component
@Slf4j
public class OrderTimeoutManager {

    private static final long TIMEOUT_MINUTES = 30;

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Autowired
    private TaskScheduler taskScheduler;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private LockersMapper lockersMapper;

    /**
     * 订单创建后调用，调度超时取消任务
     */
    public void scheduleTimeout(Long orderId) {
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> cancelIfUnpaid(orderId),
                Instant.now().plus(TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        );
        scheduledTasks.put(orderId, future);
        log.info("已调度订单超时任务, orderId: {}, 超时时间: {} 分钟", orderId, TIMEOUT_MINUTES);
    }

    /**
     * 支付成功或手动取消后调用，移除已调度的超时任务
     */
    public void cancelTimeout(Long orderId) {
        ScheduledFuture<?> future = scheduledTasks.remove(orderId);
        if (future != null) {
            future.cancel(false);
            log.info("已取消订单超时任务, orderId: {}", orderId);
        }
    }

    /**
     * 超时回调：检查订单是否仍未支付，是则取消
     */
    private void cancelIfUnpaid(Long orderId) {
        try {
            Orders order = ordersMapper.selectById(orderId);
            if (order != null && OrderStatus.PENDING_PAYMENT.getStatus().equals(order.getStatus())) {
                // 释放寄存柜
                if (order.getLockerId() != null) {
                    lockersMapper.unLocker(order.getLockerId(), LockerStatusEnum.FREE.getValue());
                }
                // 更新订单状态为已取消
                ordersMapper.nextStatus(orderId, OrderStatus.CANCELED.getStatus());
                log.info("订单超时自动取消, orderId: {}", orderId);
            }
        } catch (Exception e) {
            log.error("取消超时订单失败, orderId: {}", orderId, e);
        } finally {
            scheduledTasks.remove(orderId);
        }
    }

    /**
     * 应用启动时，重载数据库中所有待支付订单，重新调度超时任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reloadPendingOrders() {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getStatus, OrderStatus.PENDING_PAYMENT.getStatus());
        List<Orders> pendingOrders = ordersMapper.selectList(wrapper);

        int reloadCount = 0;
        int immediateCancelCount = 0;

        for (Orders order : pendingOrders) {
            long minutesElapsed = ChronoUnit.MINUTES.between(order.getCreatedAt(), LocalDateTime.now());
            long remainingMinutes = TIMEOUT_MINUTES - minutesElapsed;

            if (remainingMinutes <= 0) {
                // 已经超时，直接取消
                cancelIfUnpaid(order.getOrderId());
                immediateCancelCount++;
            } else {
                // 未超时，按剩余时间重新调度
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> cancelIfUnpaid(order.getOrderId()),
                        Instant.now().plus(remainingMinutes, ChronoUnit.MINUTES)
                );
                scheduledTasks.put(order.getOrderId(), future);
                reloadCount++;
            }
        }

        log.info("启动重载待支付订单超时任务完成, 重新调度: {} 笔, 立即取消: {} 笔", reloadCount, immediateCancelCount);
    }
}