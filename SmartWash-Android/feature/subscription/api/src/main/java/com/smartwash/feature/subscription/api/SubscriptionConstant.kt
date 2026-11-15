package com.smartwash.feature.subscription.api

/**
 * 订阅模块内常量（非用户可见；用户可见文案一律进 res/values/strings.xml）。
 * 业务边界值对齐后端 SaveSubscription 校验（docs/subscription-api.md §三）。
 */
object SubscriptionConstant {

    /** 订阅列表/打卡列表分页每页条数（后端 BaseSearchFrom 上限 100） */
    const val PAGE_SIZE = 10

    /** 提醒列表接口单次返回上限（后端固定 200 条） */
    const val REMINDER_LIMIT = 200

    /** autoRenew / isRead 的 0|1 布尔编码（对齐后端 tinyint） */
    const val FLAG_ON = 1
    const val FLAG_OFF = 0

    /** 名称长度上限 */
    const val NAME_MAX = 100

    /** 备注长度上限 */
    const val NOTE_MAX = 500

    /** 取消步骤备忘长度上限 */
    const val CANCEL_STEPS_MAX = 1000

    /** 取消网址长度上限 */
    const val CANCEL_URL_MAX = 500

    /** 间隔期数范围 */
    const val CYCLE_INTERVAL_MIN = 1
    const val CYCLE_INTERVAL_MAX = 36

    /** 提醒提前天数范围 */
    const val REMIND_DAYS_MIN = 0
    const val REMIND_DAYS_MAX = 60
}
