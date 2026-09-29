package com.smartwash.feature.user.impl

import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.model.LoginEvent
import com.smartwash.feature.user.api.model.LogoutReason
import com.smartwash.feature.user.api.model.UserInfo
import com.smartwash.feature.user.impl.network.vo.user.UserInfoVo
import com.smartwash.feature.user.impl.repository.UserRepository
import com.smartwash.feature.user.impl.session.SessionEvent
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UserApi] 实现（T5.2，api/impl 服务化样板的 impl 侧门面）。
 *
 * 三块职责：
 * 1. [isLogin]：SessionManager token 内存同步读，无 IO 无挂起；
 * 2. [getUserInfo]：**内存缓存** —— 登录后首次走网络（经 UserRepository），
 *    之后进程内直接命中，替代此前每个页面各自网络拉取的散落写法；
 *    任何会话事件（登录/登出）都使缓存失效，下次调用重新拉取；
 * 3. [loginEvents]：桥接 SessionEventBus 的四类会话事件 → LoginEvent
 *    （NeedLogin→LoggedOut(NEED_LOGIN)、Unauthorized→LoggedOut(UNAUTHORIZED)、
 *    LoggedIn→LoggedIn、LoggedOut→LoggedOut(LOGOUT)）。
 *
 * 缓冲策略对齐 SessionEventBus（extraBufferCapacity=16 + DROP_OLDEST）：
 * 登录态变化是离散事件，订阅者只需"最新事件"，慢订阅者不反压发射方。
 * 桥接协程随单例生命周期常驻（SupervisorJob，异常互不拖垮）。
 */
@Singleton
class UserApiImpl @Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
    private val userRepository: UserRepository,
) : UserApi {

    /** 用户信息内存缓存；任何会话事件（登录/登出）置 null */
    @Volatile
    private var cachedUserInfo: UserInfo? = null

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _loginEvents = MutableSharedFlow<LoginEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val loginEvents: SharedFlow<LoginEvent> = _loginEvents.asSharedFlow()

    init {
        // 桥接 SessionEventBus → loginEvents；任何会话事件同时使用户信息缓存失效
        bridgeScope.launch {
            sessionEventBus.events.collect { event ->
                cachedUserInfo = null
                val loginEvent = when (event) {
                    SessionEvent.NeedLogin -> LoginEvent.LoggedOut(LogoutReason.NEED_LOGIN)
                    SessionEvent.Unauthorized -> LoginEvent.LoggedOut(LogoutReason.UNAUTHORIZED)
                    SessionEvent.LoggedIn -> LoginEvent.LoggedIn
                    SessionEvent.LoggedOut -> LoginEvent.LoggedOut(LogoutReason.LOGOUT)
                }
                _loginEvents.emit(loginEvent)
            }
        }
    }

    override fun isLogin(): Boolean = sessionManager.currentToken().isNotEmpty()

    override suspend fun getUserInfo(): UserInfo? {
        // 命中缓存直接返回（登录后首次拉取走网络）
        cachedUserInfo?.let { return it }
        // 未登录不发起请求（契约：未登录或获取失败返回 null）
        if (!isLogin()) return null
        return try {
            userRepository.getUserInfo().toUserInfo().also { cachedUserInfo = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 契约语义：获取失败返回 null，调用方无需区分失败原因
            null
        }
    }

    /**
     * 网络层 VO → 对外领域模型（user-api 的 UserInfo）。
     *
     * school 字段逐项容错：后端对未绑定学校的用户返回 schoolVo 空对象（字段全 null，
     * Gson 经 Unsafe 绕过非空检查置 null），直接映射会触发构造参数非空检查 NPE，
     * 使 getUserInfo() 对这类用户恒返回 null——调用方（首页绑定引导）将无法区分
     * 「无学校」与「获取失败」。schoolId 落 -1 对齐原 getUserSchoolId 接口的空值语义。
     */
    private fun UserInfoVo.toUserInfo(): UserInfo = UserInfo(
        phoneNumber = phoneNumber,
        studentId = studentId,
        campusCard = campusCard,
        balance = balance,
        avatar = avatar,
        school = UserInfo.School(
            schoolId = schoolVo.schoolId ?: -1L,
            schoolName = schoolVo.schoolName ?: "",
            location = schoolVo.location ?: "",
            lockerCount = schoolVo.lockerCount ?: 0,
        ),
    )
}
