package com.smartwash.from.coupon;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateCouponFrom {
    @NotNull(message = "请选择优惠券")
    private Long couponId;

    private String title;

    private String description;

    private BigDecimal discount;

    private BigDecimal threshold;

    private Integer validDays;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Boolean isNewUser;

    private String status;
}
