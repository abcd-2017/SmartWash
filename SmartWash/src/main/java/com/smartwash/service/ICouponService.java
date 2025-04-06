package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Coupon;
import com.smartwash.from.coupon.AddCouponFrom;
import com.smartwash.from.coupon.SearchCouponFrom;
import com.smartwash.from.coupon.UpdateCouponFrom;
import com.smartwash.vo.coupon.CouponVo;

import java.util.List;

/**
 * <p>
 * 优惠券模板 服务类
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
public interface ICouponService extends IService<Coupon> {
    Page<CouponVo> getAllCoupon(SearchCouponFrom searchCouponFrom);

    void addCoupon(AddCouponFrom addCouponFrom);

    Coupon getSearchByName(String title);

    void updateCoupon(UpdateCouponFrom couponFrom);

    Boolean deleteCoupon(String ids);

    List<CouponVo> getAllValidCoupon(Long userId);
}
