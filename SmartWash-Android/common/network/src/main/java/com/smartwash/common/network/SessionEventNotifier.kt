package com.smartwash.common.network

/**
 * 跨模块会话事件通知契约：拦截器检测到未登录/登录失效时经此通知 UI 层跳登录页。
 * 实现方为 :feature:user:impl 的 SessionEventBus（其 UserImplModule @Binds 绑定），
 * 实现须做同类事件去重，避免并发多请求同时失败时堆叠多个登录页。
 */
interface SessionEventNotifier {

    /** 请求被拦截：未登录（token 为空），需引导用户去登录 */
    fun notifyNeedLogin()

    /** 登录失效：HTTP 401 / 业务码 401，需重新登录 */
    fun notifyUnauthorized()
}
