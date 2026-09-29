package com.smartwash.feature.user.impl.session

import com.smartwash.common.network.TokenProvider
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.feature.user.impl.UserImplConstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录会话管理：token/role 的进程内内存缓存 + DataStore 持久化。
 *
 * 设计动机（评审 #10/#18）：拦截器原实现每个请求 runBlocking 读 DataStore，
 * 既阻塞线程又有 ANR 风险。现在拦截器只同步读内存缓存（[currentToken]），
 * 登录/注册/登出/401 清除时经本类同步更新内存并异步落盘。
 *
 * 实现 [TokenProvider]（common:network 的跨模块契约，T2.3）：拦截器经接口取/清 token，
 * 绑定见 di/UserImplModule（T5.2 自 app/di 的 SessionNetworkBindingModule 随迁）。
 *
 * role 来自登录响应 {token, role}（后端 LoginVo），当前 UI 暂未使用，仅暂存；
 * 注册接口只返回 token 字符串，注册保存会话时 role 会被置空，两条路径不互相污染。
 *
 * 启动预热：原 warmUp() 已由 SessionInitTask 接管（T5.2，InitEngine 调度，
 * 挂起读 token/role 保证阻塞语义真实成立），本类不再提供 fire-and-forget 入口。
 */
@Singleton
class SessionManager @Inject constructor(
    private val sharePreferenceUtils: SharePreferenceUtils,
) : TokenProvider {

    /** token 内存缓存；null 表示尚未从 DataStore 加载 */
    @Volatile
    private var cachedToken: String? = null

    /** role 内存缓存（登录响应携带；注册路径无 role，保存时置空） */
    @Volatile
    private var cachedRole: String? = null

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

    /**
     * 保存会话（登录/注册成功）：同步更新内存 + 持久化。
     * [role] 仅登录响应携带；注册路径传 null（默认），会把旧 role 一并置空，防止跨路径污染。
     */
    suspend fun saveToken(token: String, role: String? = null) {
        cachedToken = token
        cachedRole = role
        sharePreferenceUtils.saveData(UserImplConstant.TOKEN, token)
        sharePreferenceUtils.saveData(UserImplConstant.USER_ROLE, role ?: "")
    }

    /**
     * 清除会话（登出/401 登录失效）：幂等 —— 已为空时不重复写盘，
     * 避免并发多请求同时 401 时重复清 token/role。
     */
    override fun clearToken() {
        val hadToken = cachedToken?.isNotEmpty() == true
        val hadRole = cachedRole?.isNotEmpty() == true
        cachedToken = ""
        cachedRole = ""
        if (!hadToken && !hadRole) return
        scope.launch {
            sharePreferenceUtils.saveData(UserImplConstant.TOKEN, "")
            sharePreferenceUtils.saveData(UserImplConstant.USER_ROLE, "")
        }
    }
}
