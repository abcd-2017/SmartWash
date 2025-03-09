package com.smartwash.common;

import lombok.Getter;

@Getter
public enum DeleteType {
    IS_DELETE(1, "逻辑已删除"),
    NOT_DELETE(0, "逻辑未删除");

    private final Integer type;

    private final String message;

    private DeleteType(Integer type, String message) {
        this.type = type;
        this.message = message;
    }
}
