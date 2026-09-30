package com.smartwash.feature.user.impl.ui.setting

import androidx.lifecycle.ViewModel
import com.smartwash.feature.user.impl.session.SessionEventBus
import com.smartwash.feature.user.impl.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 设置页 ViewModel（T5.3）：承载登出的会话操作。
 *
 * 原实现由宿主 MainActivity 向 SettingPage 传 SessionManager/SessionEventBus 参数——
 * app 壳因此被迫触碰用户域实现类型。现依赖改为模块内 Hilt 自取，页面签名不再
 * 暴露 impl 类型，宿主只传导航与插槽。
 */
@HiltViewModel
class SettingViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionEventBus: SessionEventBus,
) : ViewModel() {

    /**
     * 登出：同步清内存缓存并异步清 DataStore（幂等，不 runBlocking 阻塞主线程），
     * 随后广播主动登出（UserApiImpl 桥接为 LoginEvent.LoggedOut(LOGOUT)，
     * 并使用户信息缓存失效）。
     */
    fun logout() {
        sessionManager.clearToken()
        sessionEventBus.notifyLoggedOut()
    }
}
