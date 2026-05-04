package com.smartwash.controller.web;


import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.service.ICouponService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.coupon.CouponVo;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 优惠券模板 前端控制器
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
@Tag(name = "用户端-优惠券", description = "用户端优惠券相关接口")
@Slf4j
@RestController
@RequestMapping("/web")
public class WebCouponController {
    @Autowired
    private ICouponService couponService;

    @Operation(summary = "获取可领取优惠券列表", description = "获取当前用户可领取的有效优惠券列表")
    @GetMapping("/auth/coupon/allCoupon")
    public Result<List<CouponVo>> getAllCoupon() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(couponService.getAllValidCoupon(user.getUserId()));
    }
}
