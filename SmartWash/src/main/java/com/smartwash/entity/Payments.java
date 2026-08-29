package com.smartwash.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

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
@TableName("payments")
public class Payments implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "payment_id", type = IdType.AUTO)
    private Long paymentId;

    private Long orderId;

    private Long userId;

    private BigDecimal amount;

    private String paymentMethod;

    /**
     * 网关统一订单号（幂等键）：PAY + yyyyMMdd + 雪花ID，唯一索引 uk_payments_out_trade_no 防重
     */
    private String outTradeNo;

    private String status;

    private LocalDateTime paidAt;
}
