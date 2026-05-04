package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.EnumUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.common.OrderStatus;
import com.smartwash.common.Result;
import com.smartwash.from.order.SearchOrderFrom;
import com.smartwash.from.order.UpdateOrderStatus;
import com.smartwash.service.IOrdersService;
import com.smartwash.vo.order.OrdersVo;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "订单管理", description = "后台订单的查询、删除及状态更新")
@Slf4j
@RestController
@RequestMapping("/admin/orders")
public class OrdersController {
    @Autowired
    private IOrdersService ordersService;

    @Operation(summary = "获取订单状态枚举", description = "获取所有订单状态的枚举值及描述")
    @GetMapping("/status")
    public Result<Map<String, String>> getOrderStatus() {
        return Result.ok(EnumUtils.toMap(OrderStatus.values(), OrderStatus::getStatus, OrderStatus::getDescription));
    }

    @Operation(summary = "分页查询订单列表", description = "根据条件分页查询订单列表")
    @GetMapping("/all")
    public Result<Page<OrdersVo>> getAllOrders(SearchOrderFrom searchOrderFrom) {
        return Result.ok(ordersService.getAllOrders(searchOrderFrom));
    }

    @Operation(summary = "批量删除订单", description = "根据ID批量删除订单，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteOrders(@PathVariable("ids") @Parameter(description = "订单ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        log.info("管理员删除订单, ids: {}", ids);
        return Result.ok(ordersService.deleteOrders(ids));
    }

    @Operation(summary = "更新订单状态", description = "手动更新指定订单的状态")
    @PostMapping("updateOrderStatus")
    public Result<Boolean> updateOrderStatus(@RequestBody UpdateOrderStatus orderStatus) {
        return Result.ok(ordersService.updateOrderStatus(orderStatus));
    }
}
