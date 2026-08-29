package com.smartwash.mapper;

import com.smartwash.entity.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 优惠券模板 Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 领取限量：原子递增优惠券已发放数量（防并发超发）。
     * 仅当未设置发放总量（total_limit 为 NULL，表示不限量）或已发放数量未达上限时才递增；
     * 调用方必须以影响行数 == 1 判定发放成功，== 0 表示已领完或券不存在。
     * 该语句必须在 insert user_coupon 之前执行，与领取插入处于同一事务，
     * 插入失败（如唯一索引冲突）时整体回滚，保证 issued_count 不残留脏增量。
     *
     * @param couponId 优惠券 ID
     * @return 影响行数：1=发放量递增成功，0=已达限量或券不存在
     */
    int increaseIssuedCount(@Param("couponId") Long couponId);
}
