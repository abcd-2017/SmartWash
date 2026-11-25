package com.smartwash.lite.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.notice.api.NoticeApi
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.model.UserInfo
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * lite 设置页 ViewModel（占位骨架）。
 *
 * 复用 `:feature:user:impl` 的会话能力：
 * - 账户信息经 [UserApi.getUserInfo]（user-api 契约，带内存缓存）；
 * - 退出登录经 [SessionManager.clearToken] + [SessionEventBus.notifyLoggedOut]
 *   （与 full 设置页同一条会话链路，壳层监听 LoggedOut(LOGOUT) 不响应，由页面自理导航）。
 *
 * 不复用 full 的 [com.smartwash.feature.user.impl.ui.setting.SettingViewModel]：其构造依赖
 * 观象台 BYOK 仓库且 init 即拉取解读模型配置，对 lite 是无效网络开销。
 */
@HiltViewModel
class LiteSettingViewModel @Inject constructor(
    private val userApi: UserApi,
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
    private val noticeApi: NoticeApi,
) : ViewModel() {

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo = _userInfo.asStateFlow()

    /** 消息中心未读数（角标；失败按 0 隐藏） */
    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount = _unreadCount.asStateFlow()

    init {
        refreshUserInfo()
        refreshUnreadCount()
    }

    /** 未读数（点缀数据，失败静默置 0） */
    fun refreshUnreadCount() {
        viewModelScope.launch {
            _unreadCount.value = runCatching { noticeApi.getUnreadCount() }.getOrDefault(0L)
        }
    }

    /** 拉取账户信息（只读展示；失败静默置空，页面显示占位） */
    fun refreshUserInfo() {
        viewModelScope.launch {
            try {
                _userInfo.value = userApi.getUserInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "lite 设置页账户信息拉取失败: ${e.message}")
                _userInfo.value = null
            }
        }
    }

    /** 退出登录：清会话（内存 + DataStore，幂等）+ 主动登出广播 */
    fun logout() {
        AppLogger.info(TAG, "用户主动登出（lite）")
        sessionManager.clearToken()
        sessionEventBus.notifyLoggedOut()
    }

    private companion object {
        const val TAG = "LiteSettingViewModel"
    }
}
