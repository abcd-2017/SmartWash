package com.smartwash.vo.users;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录视图对象（充值+消费）
 */
@Data
public class TransactionVo {

    private String type; // "recharge" 或 "payment"

    private BigDecimal amount;

    private String description;

    private LocalDateTime transactionTime;

    private String status;
}
