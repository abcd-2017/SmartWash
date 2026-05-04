package com.smartwash.controller.web;


import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.service.IUserCouponService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.user_coupon.UserCouponVo;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券记录表 前端控制器
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
@Tag(name = "用户端-用户优惠券", description = "用户端优惠券领取和使用相关接口")
@Slf4j
@RestController
@RequestMapping("/web")
public class WebUserCouponController {
    @Autowired
    private IUserCouponService userCouponService;

    @Operation(summary = "获取用户优惠券列表", description = "获取当前用户已领取的优惠券列表，按状态筛选")
    @GetMapping("/auth/userCoupon/getUserCoupon")
    public Result<List<UserCouponVo>> getUserCoupon(@RequestParam(value = "status", defaultValue = "0") @Parameter(description = "优惠券状态：0-可用，1-已使用或过期", example = "0") String status,
                                                    @RequestParam(value = "page", defaultValue = "1") @Parameter(description = "页码", example = "1") Integer page,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") @Parameter(description = "每页数量", example = "10") Integer pageSize) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userCouponService.getUserCouponByStatus(status, user.getUserId(), page, pageSize));
    }

    @Operation(summary = "领取优惠券", description = "用户领取指定优惠券")
    @PostMapping("/auth/userCoupon/receiveCoupon/{couponId}")
    public Result<Boolean> receiveCoupon(@PathVariable("couponId") @Parameter(description = "优惠券ID", required = true, example = "1") Long couponId) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userCouponService.receiveCoupon(couponId, user.getUserId()));
    }

    @Operation(summary = "获取可用优惠券列表", description = "获取当前用户可用于指定订单的优惠券列表")
    @PostMapping("/auth/userCoupon/getCanUseCoupon/{orderId}")
    public Result<List<UserCouponVo>> getCanUseCoupon(@PathVariable("orderId") @Parameter(description = "订单ID", required = true, example = "1") Long orderId) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userCouponService.getCanUseCoupon(user.getUserId(), orderId));
    }
}
