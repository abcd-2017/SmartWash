package com.smartwash.feature.subscription

/**
 * 订阅域枚举映射（契约 docs/subscription-api.md §一/§三）。
 *
 * DTO 一律携带后端原值（String/Int），由本文件枚举在 UI 层转换：
 * 未知值防御回落（列表展示不因脏数据崩溃），提交时回写 raw 原值，两端零语义漂移。
 */
enum class SubscriptionStatus(val raw: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    CANCELED("canceled"),
    FINISHED("finished");

    /** 终态只读：PUT 编辑与使用打卡均被后端拒绝 */
    val isTerminal: Boolean get() = this == CANCELED || this == FINISHED

    companion object {
        fun fromRaw(raw: String?): SubscriptionStatus? =
            raw?.let { r -> entries.firstOrNull { it.raw == r } }

        /** 防御回落：未知/空按 active 处理（VO 默认值语义） */
        fun fromRawOrDefault(raw: String?): SubscriptionStatus = fromRaw(raw) ?: ACTIVE
    }
}

enum class SubscriptionCycleType(val raw: String) {
    MONTH("month"),
    YEAR("year");

    companion object {
        /** 防御回落：未知按月付处理（与后端 month 默认一致） */
        fun fromRaw(raw: String?): SubscriptionCycleType =
            entries.firstOrNull { it.raw == raw } ?: MONTH
    }
}

enum class SubscriptionReminderType(val raw: String) {
    RENEW("renew"),
    TRIAL_END("trial_end");

    companion object {
        /** 防御回落：未知按 renew 处理（枚举展示不至于空白） */
        fun fromRawOrDefault(raw: String?): SubscriptionReminderType =
            entries.firstOrNull { it.raw == raw } ?: RENEW
    }
}
