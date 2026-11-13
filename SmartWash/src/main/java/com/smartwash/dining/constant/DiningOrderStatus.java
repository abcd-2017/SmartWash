package com.smartwash.dining.constant;

import lombok.Getter;

import java.util.Arrays;

/**
 * 家庭点单-点单订单状态。
 * ONGOING 进行中：可加入、可勾选/改备注/取消选菜（所有写操作均要求此状态）；
 * FINISHED 已结束：发起者锁定订单并生成购买清单快照（结束接口在 11 号票），此后拒绝一切选菜写入。
 */
@Getter
public enum DiningOrderStatus {

    /** 进行中 */
    ONGOING("ONGOING", "进行中"),

    /** 已结束 */
    FINISHED("FINISHED", "已结束");

    private final String code;

    private final String description;

    DiningOrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 校验状态是否在枚举白名单内（防御脏数据）。
     */
    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(s -> s.code.equals(code));
    }
}
