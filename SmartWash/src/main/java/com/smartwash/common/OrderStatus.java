package com.smartwash.common;

import lombok.Getter;

@Getter
public enum OrderStatus {
    CANCELED("-2", "已取消"),
    REFUNDED("-1", "已退款"),
    PENDING_PAYMENT("0", "待支付"),
    PENDING_SHIPMENT("1", "待寄件"),
    RECEIVED("2", "已收取"),
    WASHING("3", "清洗中"),
    DRIED("4", "已烘干"),
    IN_DELIVERY("5", "配送中"),
    READY_FOR_PICKUP("6", "待取件"),
    COMPLETED("7", "已完成");

    private final String status;
    private final String description;

    OrderStatus(String status, String description) {
        this.status = status;
        this.description = description;
    }
}
