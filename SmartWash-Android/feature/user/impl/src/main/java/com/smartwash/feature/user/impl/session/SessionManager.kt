package com.smartwash.feature.user.impl.session

import com.smartwash.common.network.TokenProvider
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.feature.user.impl.UserImplConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录会话管理：token/refreshToken/role 的进程内内存缓存 + DataStore 持久化。
 *
 * 设计动机（评审 #10/#18）：拦截器原实现每个请求 runBlocking 读 DataStore，
 * 既阻塞线程又有 ANR 风险。现在拦截器只同步读内存缓存（[currentToken]），
 * 登录/注册/登出/401 清除时经本类同步更新内存并异步落盘。
 *
 * 实现 [TokenProvider]（common:network 的跨模块契约，T2.3）：拦截器经接口取/清 token，
 * 绑定见 di/UserImplModule（T5.2 自 app/di 的 SessionNetworkBindingModule 随迁）。
 *
 * role/refreshToken 来自登录响应 {token, refreshToken, role}（后端 LoginVo，双 token 契约）；
 * refreshToken 为续签凭据，401 时由 TokenRefresherImpl 经 /auth/refresh 换新双 token
 * 并经 [updateTokens] 覆盖（一次性轮换，旧值立即失效）。登录/注册响应均为 LoginVo
 * 对象（注册已升级为注册即自动登录），两条路径保存会话的结构一致。
 *
 * 启动预热：原 warmUp() 已由 SessionInitTask 接管（T5.2，InitEngine 调度，
 * 挂起读 token/role/refreshToken 保证阻塞语义真实成立），本类不再提供 fire-and-forget 入口。
 */
@Singleton
class SessionManager @Inject constructor(
    private val sharePreferenceUtils: SharePreferenceUtils,
) : TokenProvider {

    /** token 内存缓存；null 表示尚未从 DataStore 加载 */
    @Volatile
    private var cachedToken: String? = null

    /** role 内存缓存（登录/注册响应携带） */
    @Volatile
    private var cachedRole: String? = null

    /** refreshToken 内存缓存（续签预留凭据，仅存不用） */
    @Volatile
    private var cachedRefreshToken: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 同步读取内存缓存的 token（供拦截器使用，无 IO）；未预热完成时返回空串 */
    override fun currentToken(): String = cachedToken ?: ""

    /** 同步读取内存缓存的 role（当前 UI 未使用，仅供后续扩展） */
    fun currentRole(): String = cachedRole ?: ""

    /** 挂起读取 token：优先内存缓存，未命中读 DataStore 并回填 */
    suspend fun getToken(): String {
        cachedToken?.let { return it }
        val token = sharePreferenceUtils.getData(UserImplConstant.TOKEN, "")
        if (token.isNotBlank()) {
            cachedToken = token
        }
        return token
    }

    /** 挂起读取 role：优先内存缓存，未命中读 DataStore 并回填 */
    suspend fun getRole(): String {
        cachedRole?.let { return it }
        val role = sharePreferenceUtils.getData(UserImplConstant.USER_ROLE, "")
        if (role.isNotBlank()) {
            cachedRole = role
        }
        return role
    }

    /** 挂起读取 refreshToken：优先内存缓存，未命中读 DataStore 并回填 */
    suspend fun getRefreshToken(): String {
        cachedRefreshToken?.let { return it }
        val refreshToken = sharePreferenceUtils.getData(UserImplConstant.REFRESH_TOKEN, "")
        if (refreshToken.isNotBlank()) {
            cachedRefreshToken = refreshToken
        }
        return refreshToken
    }

    /**
     * 保存会话（登录/注册成功）：同步更新内存 + 持久化。
     * [refreshToken] 为双 token 契约的续签预留凭据，登录/注册响应均携带；缺省空串。
     */
    suspend fun saveToken(token: String, role: String? = null, refreshToken: String? = null) {
        cachedToken = token
        cachedRole = role
        cachedRefreshToken = refreshToken
        sharePreferenceUtils.saveData(UserImplConstant.TOKEN, token)
        sharePreferenceUtils.saveData(UserImplConstant.USER_ROLE, role ?: "")
        sharePreferenceUtils.saveData(UserImplConstant.REFRESH_TOKEN, refreshToken ?: "")
        // 登录态变更基建节点（低频；token 值绝不入日志，业务动作的 info 由页面层记录）
        AppLogger.debug(TAG, "会话已持久化（内存缓存 + DataStore）")
    }

    /**
     * 续签成功后更新双 token（TokenRefresherImpl 专用，role 保持不变）：
     * 同步更新内存（拦截器重试请求经 currentToken 立即可见新值）+ 异步落盘。
     * 后端 refreshToken 为一次性轮换，成功后旧值立即失效，须双键一起覆盖。
     */
    suspend fun updateTokens(token: String, refreshToken: String) {
        cachedToken = token
        cachedRefreshToken = refreshToken
        sharePreferenceUtils.saveData(UserImplConstant.TOKEN, token)
        sharePreferenceUtils.saveData(UserImplConstant.REFRESH_TOKEN, refreshToken)
        AppLogger.debug(TAG, "双 token 已续签更新（内存缓存 + DataStore）")
    }

    /**
     * 清除会话（登出/401/改密后的登录失效）：幂等 —— 已为空时不重复写盘，
     * 避免并发多请求同时 401 时重复清 token/role/refreshToken。
     */
    override fun clearToken() {
        val hadToken = cachedToken?.isNotEmpty() == true
        val hadRole = cachedRole?.isNotEmpty() == true
        val hadRefreshToken = cachedRefreshToken?.isNotEmpty() == true
        cachedToken = ""
        cachedRole = ""
        cachedRefreshToken = ""
        if (!hadToken && !hadRole && !hadRefreshToken) {
            // 幂等命中（并发多请求同时 401 时的预期去重路径，debug 便于排查 401 风暴）
            AppLogger.debug(TAG, "clearToken 幂等命中，跳过重复清除")
            return
        }
        // 登录态变更基建节点（低频；业务动作的 info 由页面层记录，401 路径的事件流另有 debug）
        AppLogger.debug(TAG, "会话已清除（登出/登录失效），异步落盘")
        scope.launch {
            sharePreferenceUtils.saveData(UserImplConstant.TOKEN, "")
            sharePreferenceUtils.saveData(UserImplConstant.USER_ROLE, "")
            sharePreferenceUtils.saveData(UserImplConstant.REFRESH_TOKEN, "")
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "SessionManager"
    }
}
