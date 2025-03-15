package com.smartwash.vo.recharge_records;

import com.smartwash.vo.users.UserVo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RechargeRecordsVo {
    private Long recordId;

    private UserVo users;

    private BigDecimal amount;

    private LocalDateTime rechargeTime;

    private String rechargeType;
}
