package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "用户优惠券管理", description = "后台管理用户领取的优惠券记录")
@Slf4j
@RestController
@RequestMapping("/admin/userCoupon")
public class UserCouponController {
    @Autowired
    private IUserCouponService userCouponService;

    @Operation(summary = "分页查询用户优惠券列表", description = "根据条件分页查询用户领取的优惠券记录")
    @GetMapping("/all")
    public Result<Page<UserCouponVo>> getAll(SearchUserCouponFrom couponFrom) {
        return Result.ok(userCouponService.getAllUserCoupon(couponFrom));
    }

    @Operation(summary = "批量删除用户优惠券", description = "根据ID批量删除用户优惠券记录，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteCoupon(@PathVariable("ids") @Parameter(description = "用户优惠券ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(userCouponService.deleteCoupon(ids));
    }

}
