package com.smartwash.common;

import lombok.Getter;

@Getter
public enum LockerStatusEnum {
    FREE("0", "空闲"),
    USE("1", "使用中"),
    FAULT("2", "故障");

    private final String value;
    private final String description;

    LockerStatusEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }
}
