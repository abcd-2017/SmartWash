package com.smartwash.feature.user.impl.session

import androidx.test.core.app.ApplicationProvider
import com.smartwash.common.utils.SharePreferenceUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SessionManager 内存缓存语义 JVM 单测（Robolectric 供 Context 构造 SharePreferenceUtils）。
 *
 * 注意：DataStore 实例为进程级单例（preferencesDataStore 委托），落盘路径绑定首个访问者的
 * filesDir——本类把「写盘路径」用例收敛为单个方法顺序执行（save → update → clear），
 * 其余用例只走纯内存路径，规避 Robolectric 每用例临时目录与进程级 DataStore 的路径错位。
 *
 * 锚点契约：拦截器经 currentToken() 同步读内存缓存（无 IO）；未加载/已清除返回空串；
 * 续签 updateTokens 覆盖双 token 但 role 不变；clearToken 幂等。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionManagerTest {

    private fun newSessionManager(): SessionManager =
        SessionManager(SharePreferenceUtils(ApplicationProvider.getApplicationContext()))

    @Test
    fun `未加载与未登录时同步读返回空串`() {
        val manager = newSessionManager()
        assertEquals("", manager.currentToken())
        assertEquals("", manager.currentRole())
        // TokenProvider 契约（拦截器取值路径）：空会话读空串、清除幂等
        val provider: com.smartwash.common.network.TokenProvider = manager
        assertEquals("", provider.currentToken())
        provider.clearToken()
        assertEquals("", provider.currentToken())
    }

    @Test
    fun `会话全链路 内存态先行生效且清除幂等`() {
        val manager = newSessionManager()

        // 登录保存：内存缓存同步可见（拦截器重试无需等落盘）
        runBlocking { manager.saveToken(token = "jwt-token", role = "user", refreshToken = "refresh-1") }
        assertEquals("jwt-token", manager.currentToken())
        assertEquals("user", manager.currentRole())

        // 续签覆盖双 token：role 保持不变（一次性轮换，旧值立即失效）
        runBlocking { manager.updateTokens(token = "new-token", refreshToken = "new-refresh") }
        assertEquals("new-token", manager.currentToken())
        assertEquals("user", manager.currentRole())
        runBlocking { assertEquals("new-refresh", manager.getRefreshToken()) }

        // 清除：内存态全清空
        manager.clearToken()
        assertEquals("", manager.currentToken())
        assertEquals("", manager.currentRole())
        runBlocking { assertEquals("", manager.getRefreshToken()) }

        // 幂等：重复清除不抛异常、状态不变
        manager.clearToken()
        assertEquals("", manager.currentToken())
    }
}
