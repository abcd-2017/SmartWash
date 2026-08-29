package com.smartwash.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 *
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("recharge_records")
public class RechargeRecords implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "record_id", type = IdType.AUTO)
    private Long recordId;

    private Long userId;

    private BigDecimal amount;

    private LocalDateTime rechargeTime;

    private String rechargeType;

    /**
     * 充值状态：0-待支付，1-充值成功，2-充值失败，3-处理中（复用 PaymentStatus 语义）
     */
    private String status;

    /**
     * 网关统一订单号（幂等键）：RCH + yyyyMMdd + 雪花ID，唯一索引 uk_recharge_records_out_trade_no 防重
     */
    private String outTradeNo;
}
