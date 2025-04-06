package com.smartwash.controller.web;


import com.smartwash.common.Result;
import com.smartwash.service.ICouponService;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import com.smartwash.vo.coupon.CouponVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
@RestController
@RequestMapping("/web")
public class WebCouponController {
    @Autowired
    private ICouponService couponService;

    @GetMapping("/auth/coupon/allCoupon")
    public Result<List<CouponVo>> getAllCoupon() {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(couponService.getAllValidCoupon(user.getUserId()));
    }
}
