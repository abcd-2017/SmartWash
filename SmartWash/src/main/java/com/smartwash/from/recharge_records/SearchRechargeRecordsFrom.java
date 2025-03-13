package com.smartwash.from.recharge_records;


import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SearchRechargeRecordsFrom extends BaseSearchFrom {
    private Long recordId;

    private Long userId;

    private BigDecimal amount;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String rechargeType;
}
