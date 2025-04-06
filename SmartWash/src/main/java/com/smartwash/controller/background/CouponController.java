package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.entity.Coupon;
import com.smartwash.from.coupon.AddCouponFrom;
import com.smartwash.from.coupon.SearchCouponFrom;
import com.smartwash.from.coupon.UpdateCouponFrom;
import com.smartwash.service.ICouponService;
import com.smartwash.vo.coupon.CouponVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * <p>
 * 优惠券模板 前端控制器
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
@RestController
@RequestMapping("/admin/coupon")
public class CouponController {
    @Autowired
    private ICouponService couponService;

    //获取所有优惠券
    @GetMapping("/all")
    public Result<Page<CouponVo>> getAll(SearchCouponFrom couponFrom) {
        return Result.ok(couponService.getAllCoupon(couponFrom));
    }

    //添加优惠券
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddCouponFrom addCouponFrom) {
        if (couponService.getSearchByName(addCouponFrom.getTitle()) != null) {
            return Result.failMsg("该种类优惠券已经存在了");
        }
        couponService.addCoupon(addCouponFrom);
        return Result.ok("添加成功");
    }

    //修改优惠券
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateCouponFrom couponFrom) {
        Coupon coupon = couponService.getById(couponFrom.getCouponId());
        if (!Objects.equals(coupon.getTitle(), couponFrom.getTitle()) && couponService.getSearchByName(couponFrom.getTitle()) != null) {
            return Result.failMsg("该种类优惠券已经存在了");
        }
        couponService.updateCoupon(couponFrom);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteCoupon(@PathVariable("ids") String ids) {
        return Result.ok(couponService.deleteCoupon(ids));
    }
}
