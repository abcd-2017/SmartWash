package com.smartwash.from.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReservationOrderFrom {
    @NotNull(message = "请选择套餐")
    private Long itemsId;
    private Float totalPrice;
}
