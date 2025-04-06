package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.UserCoupon;
import com.smartwash.from.user_coupon.SearchUserCouponFrom;
import com.smartwash.vo.user_coupon.UserCouponVo;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券记录表 服务类
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
public interface IUserCouponService extends IService<UserCoupon> {

    Page<UserCouponVo> getAllUserCoupon(SearchUserCouponFrom couponFrom);

    Boolean deleteCoupon(String ids);

    List<UserCouponVo> getUserCouponByStatus(String status, Long userId, Integer page, Integer pageSize);

    Boolean receiveCoupon(Long couponId, Long userId);
}
