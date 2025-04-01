package com.smartwash.from.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderStatus {
    @NotNull(message = "订单id不能为空")
    private Long orderId;
    @NotBlank(message = "状态不能为空")
    private String status;
}
