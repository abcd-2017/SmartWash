package com.smartwash.common;

import lombok.Getter;

@Getter
public enum PayType {
    PURSE("1", "钱包支付"),
    ALI_PAY("2", "支付宝"),
    WECHAT_PAY("3", "微信");

    private final String type;
    private final String description;

    PayType(String type, String description) {
        this.type = type;
        this.description = description;
    }
}
