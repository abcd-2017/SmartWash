package com.smartwash.feature.user.api.model

/**
 * 登录态变化事件（[com.smartwash.feature.user.api.UserApi.loginEvents] 的载荷）。
 *
 * 事件形态调研结论（对齐 app 现有 SessionEvent 的 NeedLogin / Unauthorized 语义）：
 * 现有两事件在 UI 层的处理完全一致（popBackStack + navigate 登录页 + launchSingleTop），
 * 仅 Toast 文案（请登录 vs 请重新登录）与触感反馈不同；从登录态视角两者都是
 * "会话不可用"。因此归并为 [LoggedOut] 单一事件、以 [LogoutReason] 保留区分度——
 * T5.3 app 壳的 401 跳转切换到 loginEvents 时不丢文案/触感差异（行为不变）。
 */
sealed class LoginEvent {

    /** 会话建立：登录或注册成功（不带载荷，需要用户数据时调 getUserInfo） */
    data object LoggedIn : LoginEvent()

    /** 会话终止：主动登出 / 未登录请求被拦截 / 401 登录失效，原因见 [reason] */
    data class LoggedOut(val reason: LogoutReason) : LoginEvent()
}

/** 登出原因 */
enum class LogoutReason {
    /** 用户主动登出（现状 SettingPage 的 clearToken 路径，T5.2 桥接时补发事件） */
    LOGOUT,

    /** 未登录（token 为空）请求被拦截 —— 对齐 SessionEvent.NeedLogin */
    NEED_LOGIN,

    /** 登录失效（HTTP 401 / 业务码 401）—— 对齐 SessionEvent.Unauthorized */
    UNAUTHORIZED,
}
