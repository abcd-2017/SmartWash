package com.smartwash.notice.constant;

import lombok.Getter;

import java.util.Arrays;

/**
 * 站内信类型枚举（v1：运营通知 + 公告）。
 * v2 预留：业务事件自动入箱（签到到账/认领审核结果等）可新增枚举值，表结构已留扩展位。
 */
@Getter
public enum NoticeType {

    /** 系统通知（维护/版本/安全类） */
    SYSTEM("SYSTEM", "系统通知"),

    /** 运营公告（活动/规则类） */
    ANNOUNCEMENT("ANNOUNCEMENT", "运营公告");

    private final String type;
    private final String description;

    NoticeType(String type, String description) {
        this.type = type;
        this.description = description;
    }

    /**
     * 校验类型是否在枚举白名单内（@Valid 之外的服务端兜底，防绕过校验器）。
     */
    public static boolean isValid(String type) {
        return Arrays.stream(values()).anyMatch(t -> t.type.equals(type));
    }
}
