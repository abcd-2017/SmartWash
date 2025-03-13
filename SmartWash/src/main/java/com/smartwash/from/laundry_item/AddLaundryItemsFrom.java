package com.smartwash.from.laundry_item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddLaundryItemsFrom {
    @NotBlank(message = "套餐名字不能为空")
    private String itemName;

    @NotNull(message = "价格不能为空")
    private BigDecimal basePrice;

    private String description;
}
