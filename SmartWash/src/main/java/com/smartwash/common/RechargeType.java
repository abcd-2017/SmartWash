package com.smartwash.common;

import lombok.Getter;

@Getter
public enum RechargeType {
    Wechat_Pay("1", "微信支付"),
    Ali_Pay("2", "支付宝支付");

    private final String type;
    private final String message;

    RechargeType(String type, String message) {
        this.type = type;
        this.message = message;
    }
}
