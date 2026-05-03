package com.smartwash.controller.web;


import com.smartwash.common.Result;
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
@Slf4j
@RestController
@RequestMapping("/web")
public class WebUserCouponController {
    @Autowired
    private IUserCouponService userCouponService;

    //获取已经领取的优惠券。status-> 0代表当前可用的优惠券，1代表已经使用过或过期优惠券
    @GetMapping("/auth/userCoupon/getUserCoupon")
    public Result<List<UserCouponVo>> getUserCoupon(@RequestParam(value = "status", defaultValue = "0") String status,
                                                    @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userCouponService.getUserCouponByStatus(status, user.getUserId(), page, pageSize));
    }

    @PostMapping("/auth/userCoupon/receiveCoupon/{couponId}")
    public Result<Boolean> receiveCoupon(@PathVariable("couponId") Long couponId) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userCouponService.receiveCoupon(couponId, user.getUserId()));
    }

    @PostMapping("/auth/userCoupon/getCanUseCoupon/{orderId}")
    public Result<List<UserCouponVo>> getCanUseCoupon(@PathVariable("orderId") Long orderId) {
        LoginUser user = UserContextHolder.getUser();
        return Result.ok(userCouponService.getCanUseCoupon(user.getUserId(), orderId));
    }
}
