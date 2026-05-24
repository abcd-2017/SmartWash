package com.smartwash.from.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReservationOrderFrom {
    @NotNull(message = "请选择套餐")
    private Long itemsId;
    private BigDecimal totalPrice;
}
