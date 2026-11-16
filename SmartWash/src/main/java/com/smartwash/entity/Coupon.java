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

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon")
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "coupon_id", type = IdType.AUTO)
    private Long couponId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("discount")
    private BigDecimal discount;

    @TableField("threshold")
    private BigDecimal threshold;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("is_new_user")
    private Boolean isNewUser;

    /**
     * 优惠券状态 0-正常使用、1-已失效
     */
    @TableField("status")
    private String status;

    @TableField("valid_days")
    private Integer validDays;

    /**
     * 发放总量上限（NULL 表示不限量）
     */
    @TableField("total_limit")
    private Integer totalLimit;

    /**
     * 已发放数量（领取时原子递增，与领取操作同事务，防止超发）
     */
    @TableField("issued_count")
    private Integer issuedCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
