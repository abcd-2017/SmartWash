package com.smartwash.common;

import lombok.Getter;

@Getter
public enum CouponStatus {
    EXPIRED(1, "已失效"),
    ACTIVE(0, "生效中"),
    RECEIVE(2, "已领取");

    private final Integer status;

    private final String message;

    private CouponStatus(Integer status, String message) {
        this.status = status;
        this.message = message;
    }
}
