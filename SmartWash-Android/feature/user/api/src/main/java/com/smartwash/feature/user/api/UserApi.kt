package com.smartwash.feature.user.api

import com.smartwash.feature.user.api.model.LoginEvent
import com.smartwash.feature.user.api.model.UserInfo
import kotlinx.coroutines.flow.SharedFlow

/**
 * 用户域对外服务契约（api/impl 服务化样板的 api 侧）。
 *
 * 消费方（order / payment / laundry 等跨模块场景与 app 壳）一律注入本接口，
 * 不得反向触碰 :feature:user:impl 的 SessionManager / UserRepository 实现细节。
 * 实现：UserApiImpl（T5.2 迁入 user-impl，Hilt `@Binds UserApi → UserApiImpl`）。
 */
interface UserApi {

    /**
     * 当前是否已登录。
     *
     * 语义 = 会话 token 非空（SessionManager.currentToken().isNotEmpty() 的内存同步读，
     * 无 IO、无挂起）。注意预热完成前 token 可能尚未从 DataStore 载入——
     * 启动早期判断登录态的场景应由实现方保证预热时序（SessionInitTask，T5.2）。
     */
    fun isLogin(): Boolean

    /**
     * 获取当前用户信息；未登录或获取失败返回 null。
     *
     * 实现方（T5.2）加内存缓存：登录后首次拉取走网络，之后进程内直接命中，
     * 替代现状每个页面各自经 UserRepository 网络拉取的散落写法；
     * 登出（[LoginEvent.LoggedOut]）时缓存随之失效。
     */
    suspend fun getUserInfo(): UserInfo?

    /**
     * 登录态变化广播（登录成功 / 登出），桥接 user-impl 的 SessionEventBus。
     *
     * - 特意选用 SharedFlow 而非 StateFlow：登录态变化是离散事件而非连续状态，
     *   订阅时不应回放"上一次登录态"（页面按需调 [isLogin] / [getUserInfo] 取现值）。
     * - 缓冲与去重策略由实现方定（对齐 SessionEventBus 的 DROP_OLDEST + 去重窗口）。
     */
    val loginEvents: SharedFlow<LoginEvent>
}
