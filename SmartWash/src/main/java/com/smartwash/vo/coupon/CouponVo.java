package com.smartwash.vo.coupon;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponVo {
    private Long couponId;

    private String title;

    private String description;

    private BigDecimal discount;

    private BigDecimal threshold;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer validDays;

    private Boolean isNewUser;

    private String status;

    /** 已发放数量：管理端列表/删除确认需展示，实体已有该字段，BeanUtils 自动拷贝 */
    private Integer issuedCount;
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    private LocalDateTime createdAt;
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    private LocalDateTime updatedAt;
}
