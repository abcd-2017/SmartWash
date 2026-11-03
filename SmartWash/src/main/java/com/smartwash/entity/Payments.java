package com.smartwash.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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

    /**
     * 逻辑删除标记：0-未删除，1-已删除。
     * 资金流水为审计凭证，禁止物理删除；MyBatis-Plus 据此在所有查询/更新自动追加 is_delete = 0，
     * removeById 会转为 update is_delete = 1。
     */
    @TableLogic
    private Integer isDelete;
}
