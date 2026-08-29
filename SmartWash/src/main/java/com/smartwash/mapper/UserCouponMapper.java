package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.entity.UserCoupon;
import org.apache.ibatis.annotations.Param;
import com.smartwash.from.user_coupon.SearchUserCouponFrom;
import com.smartwash.vo.user_coupon.UserCouponVo;

import java.math.BigDecimal;
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

    List<UserCouponVo> getCanUseCoupon(@Param("userId") Long userId, @Param("totalPrice") BigDecimal totalPrice);

    List<UserCouponVo> getAllUserCoupons(Long userId);

    /**
     * 核销优惠券（条件更新，防并发重复核销）。
     * 仅当该券属于指定用户且处于未使用状态时才更新，返回影响行数；
     * 调用方必须以影响行数 == 1 判定核销成功，== 0 表示券已核销/不存在/不属于该用户。
     * 本方法在支付事务中调用，禁止先 getById 检查再 updateById（check-then-act）。
     *
     * @param userCouponId 用户优惠券记录 ID
     * @param userId       领取该券的用户 ID（归属校验，防止核销他人优惠券）
     * @param orderId      核销该券的订单 ID
     * @return 影响行数：1=核销成功，0=核销失败（已核销或归属不符）
     */
    int markUsed(@Param("userCouponId") Long userCouponId, @Param("userId") Long userId, @Param("orderId") Long orderId);
}
