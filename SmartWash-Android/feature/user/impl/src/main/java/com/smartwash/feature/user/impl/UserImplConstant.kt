package com.smartwash.feature.user.impl

/**
 * 用户域模块内常量（T5.2 随 SessionManager/页面自 app 的 AppConstant 拆出）。
 *
 * ⚠️ [TOKEN]/[USER_ROLE] 是 DataStore 持久化键，取值与 app 单模块时期完全一致
 * （user_token / user_role），**不得改动**——改了等于丢登录态，需要数据迁移。
 */
internal object UserImplConstant {

    /** 日志 TAG（对齐 app 的 AppConstant.APP_NAME） */
    const val APP_NAME = "SmartWash"

    /** token 的 DataStore 键（沿用 app 时期取值，无数据迁移） */
    const val TOKEN = "user_token"

    /** role 的 DataStore 键（沿用 app 时期取值，无数据迁移） */
    const val USER_ROLE = "user_role"

    /** 发送验证码的倒计时秒数 */
    const val SEND_CAPTCHA = 60
}
