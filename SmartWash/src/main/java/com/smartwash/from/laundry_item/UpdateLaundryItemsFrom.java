package com.smartwash.from.laundry_item;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateLaundryItemsFrom {
    @NotNull(message = "请选择套餐")
    private Long itemId;

    private String itemName;

    private BigDecimal basePrice;

    private String description;
}
