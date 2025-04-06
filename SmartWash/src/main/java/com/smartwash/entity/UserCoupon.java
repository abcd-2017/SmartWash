package com.smartwash.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户领取优惠券记录表
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户优惠券记录 ID
     */
    @TableId(value = "user_coupon_id", type = IdType.AUTO)
    private Long userCouponId;

    @TableField("user_id")
    private Long userId;

    @TableField("coupon_id")
    private Long couponId;


    /**
     * 是否已使用
     */
    @TableField("is_used")
    private Boolean isUsed;

    /**
     * 优惠券实际过期时间
     */
    @TableField("expired_at")
    private LocalDateTime expiredAt;

    /**
     * 使用时间
     */
    @TableField("used_at")
    private LocalDateTime usedAt;

    /**
     * 领取时间
     */
    @TableField("received_at")
    private LocalDateTime receivedAt;

    /**
     * 使用该优惠券的订单 ID
     */
    @TableField("order_id")
    private Long orderId;
}
