package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.EnumUtils;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.Result;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.from.order.UpdateOrderStatus;
import com.smartwash.service.IOrdersService;
import com.smartwash.vo.order.OrdersVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/admin/orders")
public class OrdersController {
    @Autowired
    private IOrdersService ordersService;

    @GetMapping("/status")
    public Result<Map<String, String>> getOrderStatus() {
        return Result.ok(EnumUtils.toMap(OrderStatus.values(), OrderStatus::getStatus, OrderStatus::getDescription));
    }

    @GetMapping("/all")
    public Result<Page<OrdersVo>> getAllOrders(SearchOrderFrom searchOrderFrom) {
        return Result.ok(ordersService.getAllOrders(searchOrderFrom));
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteOrders(@PathVariable String ids) {
        return Result.ok(ordersService.deleteOrders(ids));
    }

    @PostMapping("updateOrderStatus")
    public Result<Boolean> updateOrderStatus(@RequestBody UpdateOrderStatus orderStatus) {
        return Result.ok(ordersService.updateOrderStatus(orderStatus));
    }
}
