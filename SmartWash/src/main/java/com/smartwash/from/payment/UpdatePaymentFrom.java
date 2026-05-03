package com.smartwash.from.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePaymentFrom {
    @NotNull(message = "支付ID不能为空")
    private Long paymentId;

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotBlank(message = "支付状态不能为空")
    private String status;
}
