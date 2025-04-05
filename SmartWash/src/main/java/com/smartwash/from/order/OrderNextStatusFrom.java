package com.smartwash.from.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderNextStatusFrom {
    @NotNull(message = "订单id不能为空")
    private Long orderId;
    @NotBlank(message = "取件/寄件码不能为空")
    private String pickupCode;
}
