package com.smartwash.from.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentOrderFrom {
    @NotNull(message = "订单号不能为空")
    private Long orderId;
    @NotNull(message = "支付金额不能为空")
    private Float amount;
    @NotBlank(message = "支付方式不能为空")
    private String paymentType;
}
