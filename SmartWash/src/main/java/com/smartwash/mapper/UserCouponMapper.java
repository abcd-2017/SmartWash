package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.UserCoupon;
import com.smartwash.from.user_coupon.SearchUserCouponFrom;
import com.smartwash.vo.user_coupon.UserCouponVo;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券记录表 Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    Page<UserCouponVo> searchUserCoupon(Page<UserCouponVo> page, SearchUserCouponFrom couponFrom);

    List<Long> getCouponIdsByUserId(Long userId);

    List<UserCouponVo> searchUserCouponByStatus(Page<UserCouponVo> page, String status, Long userId);
}
