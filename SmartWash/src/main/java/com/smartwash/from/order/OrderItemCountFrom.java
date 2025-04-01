package com.smartwash.from.order;

import lombok.Data;

@Data
public class OrderItemCountFrom {
    private String pendingPaymentStatus;
    private String processingStatus;
    private String pendingPickupStatus;
    private String shippedStatus;

}
