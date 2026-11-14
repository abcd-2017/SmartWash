package com.smartwash.dining.controller;

import com.smartwash.common.Result;
import com.smartwash.dining.from.AddDiningOrderItemFrom;
import com.smartwash.dining.from.UpdateDiningOrderItemFrom;
import com.smartwash.dining.service.DiningOrderItemService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 吃啥-选菜用户端控制器（共享购物车：勾选/改备注/取消）。
 * 路径前缀 /web/auth/dining/orders/{orderId}/items，需 ROLE_USER。
 * 乐观并发契约：写请求携带客户端已知 version；与服务端不一致返回业务错误码 409
 * （message「订单已被他人更新，请刷新后重试」），客户端拉取最新详情后重放。
 */
@Tag(name = "吃啥-选菜用户端", description = "勾选菜品/修改备注/取消选菜（version 乐观并发）")
@Slf4j
@RestController
@RequestMapping("/web/auth/dining/orders")
@RequiredArgsConstructor
public class WebDiningOrderItemController {

    private final DiningOrderItemService diningOrderItemService;

    @Operation(summary = "勾选菜品", description = "菜须在本单菜单内；一人一菜一条（重复勾选拒绝，引导走改备注）；version 冲突返回 409")
    @PostMapping("/{orderId}/items")
    public Result<Void> addItem(@PathVariable("orderId") Long orderId,
                                @RequestBody @Valid AddDiningOrderItemFrom from) {
        diningOrderItemService.addItem(orderId, from, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "修改选菜备注", description = "仅本人选菜记录；note 不传即清空备注；version 冲突返回 409")
    @PutMapping("/{orderId}/items")
    public Result<Void> updateNote(@PathVariable("orderId") Long orderId,
                                   @RequestBody @Valid UpdateDiningOrderItemFrom from) {
        diningOrderItemService.updateItemNote(orderId, from, currentUserId());
        return Result.ok(null);
    }

    @Operation(summary = "取消选菜", description = "删除本人该菜的选菜记录；version 冲突返回 409")
    @DeleteMapping("/{orderId}/items/{recipeId}")
    public Result<Void> removeItem(
            @PathVariable("orderId") Long orderId,
            @PathVariable("recipeId") Long recipeId,
            @Parameter(description = "客户端已知的订单 version（乐观并发闸门），缺失按业务失败返回")
            @RequestParam(value = "version", required = false) Long version) {
        diningOrderItemService.removeItem(orderId, recipeId, version, currentUserId());
        return Result.ok(null);
    }

    private Long currentUserId() {
        LoginUser user = UserContextHolder.getUser();
        return user.getUserId();
    }
}
