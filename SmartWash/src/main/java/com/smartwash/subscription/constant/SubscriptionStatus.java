package com.smartwash.subscription.constant;

import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 订阅状态机。
 * active ↔ paused 可互转；active/paused → canceled/finished 为终态（保留数据，不可编辑/打卡/再流转）。
 * 所有流转必须经 casTransition 条件更新闸门，禁止 check-then-act。
 */
@Getter
public enum SubscriptionStatus {
    ACTIVE("active", "生效中"),
    PAUSED("paused", "已暂停"),
    CANCELED("canceled", "已取消"),
    FINISHED("finished", "已结束");

    private final String status;
    private final String description;

    /** 合法流转表：终态对应空集合 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            ACTIVE.status, Set.of(PAUSED.status, CANCELED.status, FINISHED.status),
            PAUSED.status, Set.of(ACTIVE.status, CANCELED.status, FINISHED.status),
            CANCELED.status, Set.of(),
            FINISHED.status, Set.of());

    SubscriptionStatus(String status, String description) {
        this.status = status;
        this.description = description;
    }

    /** 状态流转是否合法（终态流转任何目标均非法） */
    public static boolean isLegal(String from, String to) {
        Set<String> targets = TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    /** 终态判断：canceled/finished 保留数据但只读 */
    public static boolean isTerminal(String status) {
        return CANCELED.status.equals(status) || FINISHED.status.equals(status);
    }

    /** 状态值是否有效（筛选参数校验用） */
    public static boolean isValid(String status) {
        return TRANSITIONS.containsKey(status);
    }
}
