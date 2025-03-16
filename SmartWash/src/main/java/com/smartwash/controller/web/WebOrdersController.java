package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.Result;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.service.IOrdersService;
import com.smartwash.vo.order.OrdersVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
@RequestMapping("/web/orders")
public class WebOrdersController {
    @Autowired
    private IOrdersService ordersService;

    //获取支付状态
    @GetMapping("/status")
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

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteLockers(@PathVariable("ids") String ids) {
        return Result.ok(ordersService.deleteOrders(ids));
    }
}
