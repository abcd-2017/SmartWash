package com.smartwash.from.coupon;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AddCouponFrom {
    @NotBlank(message = "优惠券名字不能为空")
    private String title;

    private String description;

    @NotNull(message = "优惠券金额不能为空")
    private BigDecimal discount;

    @NotNull(message = "优惠券使用门槛不能为空")
    private BigDecimal threshold;

    @NotNull(message = "优惠券开始使用时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "优惠券结束使用时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @NotNull(message = "有效天数不能为空")
    private Integer validDays;

    private Boolean isNewUser;

    private String status;
}
