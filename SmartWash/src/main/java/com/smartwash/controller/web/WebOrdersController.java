package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.EnumUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "用户端-订单", description = "用户端订单的创建、查询、状态变更等接口")
@Slf4j
@RestController
@RequestMapping("/web")
public class WebOrdersController {
    @Autowired
    private IOrdersService ordersService;

    @Operation(summary = "获取订单状态枚举", description = "获取所有订单状态的枚举值及描述")
    @GetMapping("/orders/status")
    public Result<Map<String, String>> getOrderStatus() {
        return Result.ok(EnumUtils.toMap(OrderStatus.values(), OrderStatus::getStatus, OrderStatus::getDescription));
    }

    @Operation(summary = "分页查询订单列表", description = "根据条件分页查询当前用户的订单列表")
    @GetMapping("/auth/orders/all")
    public Result<Page<OrdersVo>> getAllOrders(SearchOrderFrom searchOrderFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        searchOrderFrom.setUserId(loginUser.getUserId());
        return Result.ok(ordersService.getAllOrders(searchOrderFrom));
    }

    @Operation(summary = "预约洗衣", description = "用户创建洗衣预约订单，返回订单ID")
    @PostMapping("/auth/orders/reservation")
    public Result<Long> reservationLaundry(@RequestBody @Valid ReservationOrderFrom reservationOrderFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        Long orderId = ordersService.createOrder(reservationOrderFrom, loginUser);
        log.info("用户下单, userId: {}, orderId: {}", loginUser.getUserId(), orderId);
        return Result.ok(orderId);
    }

    @Operation(summary = "获取订单详情", description = "根据订单ID获取订单详细信息")
    @PostMapping("/auth/orders/getOrderInfo/{orderId}")
    public Result<OrdersVo> getOrderInfo(@PathVariable("orderId") @Parameter(description = "订单ID", required = true, example = "1") Long orderId) {
        OrdersVo order = ordersService.getOrderByOrderId(orderId);
        if (order == null) {
            log.warn("订单不存在, orderId: {}", orderId);
            return Result.failMsg("该订单不存在");
        }
        return Result.ok(order);
    }

    @Operation(summary = "计算订单价格", description = "根据订单ID和优惠券ID计算订单最终价格")
    @PostMapping("/auth/orders/calculationOrder/{orderId}/{userCouponId}")
    public Result<OrdersVo> calculationOrder(@PathVariable("orderId") @Parameter(description = "订单ID", required = true, example = "1") Long orderId, @PathVariable("userCouponId") @Parameter(description = "用户优惠券ID，不使用优惠券传0", required = true, example = "1") Long userCouponId) {
        LoginUser user = UserContextHolder.getUser();
        log.info("用户计价, userId: {}, orderId: {}, couponId: {}", user.getUserId(), orderId, userCouponId);
        return Result.ok(ordersService.calculationOrder(user.getUserId(), orderId, userCouponId));
    }

    @Operation(summary = "获取订单列表", description = "根据条件获取用户订单列表（支持游标分页）")
    @PostMapping("/auth/orders/getOrderList")
    public Result<List<ShowOrderVo>> getOrderList(@RequestBody OrderListFrom orderListFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        return Result.ok(ordersService.getOrderList(orderListFrom, loginUser));
    }

    @Operation(summary = "获取订单项目数量", description = "获取用户各状态订单的数量统计")
    @PostMapping("/auth/orders/getOrderItemCount")
    public Result<OrderItemCountVo> getOrderItemCount(@RequestBody OrderItemCountFrom itemCountFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        return Result.ok(ordersService.getOrderItemCount(itemCountFrom, loginUser.getUserId()));
    }

    @Operation(summary = "订单发货", description = "用户确认寄送衣物，需提供取件码")
    @PostMapping("/auth/orders/shipping")
    public Result<Boolean> shippingOrder(@RequestBody @Valid OrderNextStatusFrom statusFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        log.info("用户确认寄送, userId: {}, orderId: {}", loginUser.getUserId(), statusFrom.getOrderId());
        return Result.ok(ordersService.shippingOrder(statusFrom, loginUser));
    }

    @Operation(summary = "订单取件", description = "用户确认取件，需提供取件码")
    @PostMapping("/auth/orders/pickup")
    public Result<Boolean> pickupOrder(@RequestBody @Valid OrderNextStatusFrom statusFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        log.info("用户确认取件, userId: {}, orderId: {}", loginUser.getUserId(), statusFrom.getOrderId());
        return Result.ok(ordersService.pickupOrder(statusFrom, loginUser));
    }

    @Operation(summary = "获取正在清洗的订单", description = "获取用户当前正在清洗中的订单列表")
    @GetMapping("/auth/orders/getWashingOrder")
    public Result<List<Orders>> getWashingOrder(@RequestParam(value = "size", defaultValue = "5") @Parameter(description = "返回数量", example = "5") int size) {
        LoginUser loginUser = UserContextHolder.getUser();
        return Result.ok(ordersService.getWashingOrder(loginUser, size));
    }

    @Operation(summary = "取消订单", description = "用户取消指定订单")
    @PostMapping("/auth/orders/cancelOrder/{orderId}")
    public Result<Boolean> cancelOrder(@PathVariable("orderId") @Parameter(description = "订单ID", required = true, example = "1") Long orderId) {
        LoginUser loginUser = UserContextHolder.getUser();
        log.info("用户取消订单, userId: {}, orderId: {}", loginUser.getUserId(), orderId);
        return Result.ok(ordersService.cancelOrder(orderId, loginUser.getUserId()));
    }
}
