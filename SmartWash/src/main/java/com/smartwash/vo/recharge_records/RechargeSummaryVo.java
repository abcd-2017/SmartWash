package com.smartwash.vo.recharge_records;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值记录汇总统计
 */
@Data
public class RechargeSummaryVo {

    /** 充值记录总笔数（含待支付/失败/处理中） */
    private Long totalCount;

    /** 充值成功笔数（status = PaymentStatus.SUCCESS） */
    private Long successCount;

    /** 全部充值金额合计（元） */
    private BigDecimal totalAmount;

    /** 充值成功金额合计（元） */
    private BigDecimal successAmount;
}
