package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.entity.Coupon;
import com.smartwash.from.coupon.AddCouponFrom;
import com.smartwash.from.coupon.SearchCouponFrom;
import com.smartwash.from.coupon.UpdateCouponFrom;
import com.smartwash.service.ICouponService;
import com.smartwash.vo.coupon.CouponVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "优惠券管理", description = "优惠券模板的增删改查")
@Slf4j
@RestController
@RequestMapping("/admin/coupon")
public class CouponController {
    @Autowired
    private ICouponService couponService;

    @Operation(summary = "分页查询优惠券列表", description = "根据条件分页查询优惠券模板列表")
    @GetMapping("/all")
    public Result<Page<CouponVo>> getAll(SearchCouponFrom couponFrom) {
        return Result.ok(couponService.getAllCoupon(couponFrom));
    }

    @Operation(summary = "新增优惠券", description = "新增优惠券模板，标题不可重复")
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddCouponFrom addCouponFrom) {
        if (couponService.getSearchByName(addCouponFrom.getTitle()) != null) {
            return Result.failMsg("该种类优惠券已经存在了");
        }
        couponService.addCoupon(addCouponFrom);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新优惠券", description = "修改优惠券模板信息")
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateCouponFrom couponFrom) {
        Coupon coupon = couponService.getById(couponFrom.getCouponId());
        if (!Objects.equals(coupon.getTitle(), couponFrom.getTitle()) && couponService.getSearchByName(couponFrom.getTitle()) != null) {
            return Result.failMsg("该种类优惠券已经存在了");
        }
        couponService.updateCoupon(couponFrom);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除优惠券", description = "根据ID批量删除优惠券模板，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteCoupon(@PathVariable("ids") @Parameter(description = "优惠券ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(couponService.deleteCoupon(ids));
    }
}
