package com.smartwash.common;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    LOADING("0", "待支付"),
    SUCCESS("1", "已支付"),
    FAIL("2", "失败"),
    // 处理中：支付/充值单已创建、等待网关回调确认；注意 SUCCESS 状态码 "1" 被 Dashboard 收入统计依赖，不可变更
    PROCESSING("3", "处理中");

    private final String status;
    private final String description;

    PaymentStatus(String status, String description) {
        this.status = status;
        this.description = description;
    }
}
