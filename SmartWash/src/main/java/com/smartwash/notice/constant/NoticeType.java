package com.smartwash.notice.constant;

import lombok.Getter;

import java.util.Arrays;

/**
 * 站内信类型枚举（v1：运营通知 + 公告；v2：业务事件定向通知）。
 * 业务事件类型（DINING_*）由服务层定向发送入口（NoticeService#publishTargeted）使用，
 * 管理端 HTTP 发送仍仅开放 SYSTEM/ANNOUNCEMENT（AddNoticeFrom 白名单未放开）。
 */
@Getter
public enum NoticeType {

    /** 系统通知（维护/版本/安全类） */
    SYSTEM("SYSTEM", "系统通知"),

    /** 运营公告（活动/规则类） */
    ANNOUNCEMENT("ANNOUNCEMENT", "运营公告"),

    /** 吃啥：有人加入点单订单（定向通知发起者） */
    DINING_JOIN("DINING_JOIN", "点单加入通知"),

    /** 吃啥：发起者结束点单、购买清单定稿（定向通知参与者） */
    DINING_FINISH("DINING_FINISH", "点单结束通知");

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

    /**
     * 管理端可发送的类型白名单（广播语义类型）。
     * DINING_* 等业务事件类型只允许经服务层定向入口（publishTargeted）发出，
     * 不开放给管理端 HTTP 发送（定向必有接收人，广播发业务事件类型语义不成立）。
     */
    public static boolean isValidBroadcast(String type) {
        return SYSTEM.type.equals(type) || ANNOUNCEMENT.type.equals(type);
    }
}
