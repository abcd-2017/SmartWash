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

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "user_coupon_id", type = IdType.AUTO)
    private Long userCouponId;

    @TableField("user_id")
    private Long userId;

    @TableField("coupon_id")
    private Long couponId;


    @TableField("is_used")
    private Boolean isUsed;

    @TableField("expired_at")
    private LocalDateTime expiredAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    /**
     * 使用该优惠券的订单 ID
     */
    @TableField("order_id")
    private Long orderId;
}
