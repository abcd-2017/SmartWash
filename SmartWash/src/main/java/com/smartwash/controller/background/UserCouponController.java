package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.user_coupon.SearchUserCouponFrom;
import com.smartwash.service.IUserCouponService;
import com.smartwash.vo.user_coupon.UserCouponVo;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/admin/userCoupon")
public class UserCouponController {
    @Autowired
    private IUserCouponService userCouponService;

    //获取所有用户领取的优惠券
    @GetMapping("/all")
    public Result<Page<UserCouponVo>> getAll(SearchUserCouponFrom couponFrom) {
        return Result.ok(userCouponService.getAllUserCoupon(couponFrom));
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteCoupon(@PathVariable("ids") String ids) {
        return Result.ok(userCouponService.deleteCoupon(ids));
    }

}
