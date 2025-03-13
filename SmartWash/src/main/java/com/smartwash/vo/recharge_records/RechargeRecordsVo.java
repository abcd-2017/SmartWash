package com.smartwash.vo.recharge_records;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RechargeRecordsVo {
    private Long recordId;

    private Long userId;

    private BigDecimal amount;

    private LocalDateTime rechargeTime;

    private String rechargeType;
}
