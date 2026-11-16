package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.UserCoupon;
import com.smartwash.from.user_coupon.SearchUserCouponFrom;
import com.smartwash.vo.user_coupon.UserCouponVo;

import java.util.List;

public interface IUserCouponService extends IService<UserCoupon> {

    Page<UserCouponVo> getAllUserCoupon(SearchUserCouponFrom couponFrom);

    Boolean deleteCoupon(String ids);

    Page<UserCouponVo> getUserCouponByStatus(String status, Long userId, Integer page, Integer pageSize);

    Boolean receiveCoupon(Long couponId, Long userId);

    List<UserCouponVo> getCanUseCoupon(Long userId, Long orderId);

    List<UserCouponVo> getAllUserCoupons(Long userId);
}
