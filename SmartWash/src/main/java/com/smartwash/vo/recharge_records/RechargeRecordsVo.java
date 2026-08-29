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

    /**
     * 充值状态：0-待支付，1-充值成功，2-充值失败，3-处理中（复用 PaymentStatus 语义）。
     * 供三端区分处理中/失败充值，处理中与失败的记录不得展示为到账。
     */
    private String status;

    /** 网关统一订单号（幂等键），供三端对账与查询支付结果 */
    private String outTradeNo;
}
