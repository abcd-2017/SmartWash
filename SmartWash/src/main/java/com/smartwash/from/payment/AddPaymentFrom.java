package com.smartwash.from.payment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AddPaymentFrom {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "支付金额不能为空")
    private BigDecimal amount;

    @NotNull(message = "支付方式不能为空")
    private String paymentMethod;
}
