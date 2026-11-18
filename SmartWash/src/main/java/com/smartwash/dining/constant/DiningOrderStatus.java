package com.smartwash.dining.constant;

import lombok.Getter;

import java.util.Arrays;

/**
 * 吃啥-点单订单状态。
 * 产品模型：菜单即已选清单，全员共同编辑，发起者一步「提交」→ 订单直接结束。
 * DRAFT 草稿：编辑中——标题/菜单可空，全部成员可加/移除菜，可邀请加入；
 * FINISHED 已结束：发起者提交并结束，已生成购买清单（食材）快照，此后拒绝一切写入。
 * 迁移只允许 DRAFT→FINISHED（submit），无逆向。
 */
@Getter
public enum DiningOrderStatus {

    DRAFT("DRAFT", "草稿"),

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

    /** 是否允许加入（仅草稿可邀请加入，已结束拒绝）。 */
    public static boolean isJoinable(String code) {
        return DRAFT.code.equals(code);
    }
}
