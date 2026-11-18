package com.smartwash.dining.constant;

import lombok.Getter;

import java.util.Arrays;

/**
 * 吃啥-点单订单状态。
 * DRAFT 草稿：边点边邀的编辑态——标题/菜单可空，全部成员可加菜/移除菜，可邀请加入；
 * ONGOING 进行中：已定稿（publish），可加入、可勾选/改备注/取消选菜（所有选菜写操作均要求此状态）；
 * FINISHED 已结束：发起者锁定订单并生成购买清单快照（结束接口在 11 号票），此后拒绝一切选菜写入。
 * 迁移只允许 DRAFT→ONGOING（publish）与 ONGOING→FINISHED（finish），无逆向。
 */
@Getter
public enum DiningOrderStatus {

    DRAFT("DRAFT", "草稿"),

    ONGOING("ONGOING", "进行中"),

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

    /** 是否允许加入（草稿与进行中均可邀请加入，已结束拒绝）。 */
    public static boolean isJoinable(String code) {
        return DRAFT.code.equals(code) || ONGOING.code.equals(code);
    }
}
