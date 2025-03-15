package com.smartwash.common;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    LOADING("0", "待支付"),
    SUCCESS("1", "已支付"),
    FAIL("2", "失败");

    private final String status;
    private final String description;

    PaymentStatus(String status, String description) {
        this.status = status;
        this.description = description;
    }
}
