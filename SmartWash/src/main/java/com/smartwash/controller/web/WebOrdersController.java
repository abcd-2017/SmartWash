package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.Result;
import com.smartwash.from.order.OrderItemCountFrom;
import com.smartwash.from.order.OrderListFrom;
import com.smartwash.from.order.ReservationOrderFrom;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.service.IOrdersService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.order.OrderItemCountVo;
import com.smartwash.vo.order.OrdersVo;
import com.smartwash.vo.order.ShowOrderVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
@RestController
@RequestMapping("/web")
public class WebOrdersController {
    @Autowired
    private IOrdersService ordersService;

    //获取支付状态
    @GetMapping("/orders/status")
    public Result<Map<String, String>> getOrderStatus() {
        OrderStatus[] values = OrderStatus.values();
        Map<String, String> map = new HashMap<>();
        for (OrderStatus status : values) {
            map.put(status.getStatus(), status.getDescription());
        }
        return Result.ok(map);
    }

    @GetMapping("/all")
    public Result<Page<OrdersVo>> getAllLockers(SearchOrderFrom searchOrderFrom) {
        return Result.ok(ordersService.getAllOrders(searchOrderFrom));
    }

    @PostMapping("/auth/orders/reservation")
    public Result<Long> reservationLaundry(@RequestBody @Valid ReservationOrderFrom reservationOrderFrom) {
        LoginUser loginUser = UserContextHolder.getUser();
        return Result.ok(ordersService.createOrder(reservationOrderFrom, loginUser));
    }

    @PostMapping("/auth/orders/getOrderInfo/{orderId}")
    public Result<OrdersVo> getOrderInfo(@PathVariable("orderId") Long orderId) {
        if (orderId == null || ordersService.getById(orderId) == null) {
            return Result.failMsg("该订单不存在");
        }
        return Result.ok(ordersService.getOrderByOrderId(orderId));
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
}
