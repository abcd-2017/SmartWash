package com.smartwash.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 优惠券模板
 * </p>
 *
 * @author
 * @since 2025-04-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon")
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券 ID
     */
    @TableId(value = "coupon_id", type = IdType.AUTO)
    private Long couponId;

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 优惠券说明
     */
    @TableField("description")
    private String description;

    /**
     * 优惠金额
     */
    @TableField("discount")
    private BigDecimal discount;

    /**
     * 使用门槛
     */
    @TableField("threshold")
    private BigDecimal threshold;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 是否仅限新用户使用
     */
    @TableField("is_new_user")
    private Boolean isNewUser;

    /**
     * 优惠券状态 0-正常使用、1-已失效
     */
    @TableField("status")
    private String status;

    /**
     * 领取后有效天数
     */
    @TableField("valid_days")
    private Integer validDays;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
