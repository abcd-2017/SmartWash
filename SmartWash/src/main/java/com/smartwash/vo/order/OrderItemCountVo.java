package com.smartwash.vo.order;

import lombok.Data;

@Data
public class OrderItemCountVo {
    private Integer pendingPaymentCount;
    private Integer processingCount;
    private Integer pendingPickupCount;
    private Integer shippedCount;
}
