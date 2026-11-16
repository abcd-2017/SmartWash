package com.smartwash.feature.user.impl

/**
 * 用户域模块内常量（T5.2 随 SessionManager/页面自 app 的 AppConstant 拆出）。
 *
 * ⚠️ [TOKEN]/[USER_ROLE] 是 DataStore 持久化键，取值与 app 单模块时期完全一致
 * （user_token / user_role），**不得改动**——改了等于丢登录态，需要数据迁移。
 *
 * public（原 internal）：[SEND_CAPTCHA] 被 app 两个变体的注册页消费
 * （full 的 RegisterPage 与 lite 的 LiteRegisterPage），跨源集/跨模块引用需 public。
 */
object UserImplConstant {

    const val APP_NAME = "SmartWash"

    /** token 的 DataStore 键（沿用 app 时期取值，无数据迁移） */
    const val TOKEN = "user_token"

    /** role 的 DataStore 键（沿用 app 时期取值，无数据迁移） */
    const val USER_ROLE = "user_role"

    /** refreshToken 的 DataStore 键（双 token 契约新增；新 key 无历史数据，无迁移问题） */
    const val REFRESH_TOKEN = "user_refresh_token"

    const val SEND_CAPTCHA = 60
}
