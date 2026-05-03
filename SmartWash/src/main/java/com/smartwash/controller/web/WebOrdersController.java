package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.EnumUtils;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.Result;
import com.smartwash.entity.Orders;
import com.smartwash.from.order.*;
import com.smartwash.service.IOrdersService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.order.OrderItemCountVo;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Slf4j
@RestController
@RequestMapping("/web")
public class WebOrdersController {
    @Autowired
    private IOrdersService ordersService;

    @GetMapping("/orders/status")
    public Result<Map<String, String>> getOrderStatus() {
        return Result.ok(EnumUtils.toMap(OrderStatus.values(), OrderStatus::getStatus, OrderStatus::getDescription));
    }

    @GetMapping("/all")
    public Result<Page<OrdersVo>> getAllOrders(SearchOrderFrom searchOrderFrom) {
        return Result.ok(ordersService.getAllOrders(searchOrderFrom));
    }

    @PostMapping("/auth/orders/reservation")
    public Result<Long> reservationLaundry(@RequestBody @Valid ReservationOrderFrom reservationOrderFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        Long orderId = ordersService.createOrder(reservationOrderFrom, loginUser);
        log.info("用户下单, userId: {}, orderId: {}", loginUser.getUserId(), orderId);
        return Result.ok(orderId);
    }

    @PostMapping("/auth/orders/getOrderInfo/{orderId}")
    public Result<OrdersVo> getOrderInfo(@PathVariable("orderId") Long orderId) {
        OrdersVo order = ordersService.getOrderByOrderId(orderId);
        if (order == null) {
            log.warn("订单不存在, orderId: {}", orderId);
            return Result.failMsg("该订单不存在");
        }
        return Result.ok(order);
    }

    @PostMapping("/auth/orders/calculationOrder/{orderId}/{userCouponId}")
    public Result<OrdersVo> calculationOrder(@PathVariable("orderId") Long orderId, @PathVariable("userCouponId") Long userCouponId) {
        LoginUser user = UserContextHolder.getUser();
        log.info("用户计价, userId: {}, orderId: {}, couponId: {}", user.getUserId(), orderId, userCouponId);
        return Result.ok(ordersService.calculationOrder(user.getUserId(), orderId, userCouponId));
    }

    @PostMapping("/auth/orders/getOrderList")
    public Result<List<ShowOrderVo>> getOrderList(@RequestBody OrderListFrom orderListFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        return Result.ok(ordersService.getOrderList(orderListFrom, loginUser));
    }

    @PostMapping("/auth/orders/getOrderItemCount")
    public Result<OrderItemCountVo> getOrderItemCount(@RequestBody OrderItemCountFrom itemCountFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        return Result.ok(ordersService.getOrderItemCount(itemCountFrom, loginUser.getUserId()));
    }

    @PostMapping("/auth/orders/shipping")
    public Result<Boolean> shippingOrder(@RequestBody @Valid OrderNextStatusFrom statusFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        log.info("用户确认寄送, userId: {}, orderId: {}", loginUser.getUserId(), statusFrom.getOrderId());
        return Result.ok(ordersService.shippingOrder(statusFrom, loginUser));
    }

    @PostMapping("/auth/orders/pickup")
    public Result<Boolean> pickupOrder(@RequestBody @Valid OrderNextStatusFrom statusFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        log.info("用户确认取件, userId: {}, orderId: {}", loginUser.getUserId(), statusFrom.getOrderId());
        return Result.ok(ordersService.pickupOrder(statusFrom, loginUser));
    }

    @GetMapping("/auth/orders/getWashingOrder")
    public Result<List<Orders>> getWashingOrder(@RequestParam(value = "size", defaultValue = "5") int size) {
        LoginUser loginUser = UserContextHolder.getUser();
        return Result.ok(ordersService.getWashingOrder(loginUser, size));
    }

    @PostMapping("/auth/orders/cancelOrder/{orderId}")
    public Result<Boolean> cancelOrder(@PathVariable("orderId") Long orderId) {
        LoginUser loginUser = UserContextHolder.getUser();
        log.info("用户取消订单, userId: {}, orderId: {}", loginUser.getUserId(), orderId);
        return Result.ok(ordersService.cancelOrder(orderId, loginUser.getUserId()));
    }
}
