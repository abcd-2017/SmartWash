package com.smartwash.vo.laudry;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LaundryPackageVo {
    private Long itemId;

    private String itemName;

    private BigDecimal basePrice;

    private String description;

    private LocalDateTime createdAt;
}
