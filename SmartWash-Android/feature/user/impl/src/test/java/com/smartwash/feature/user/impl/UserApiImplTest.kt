package com.smartwash.feature.user.impl

import androidx.test.core.app.ApplicationProvider
import com.smartwash.common.utils.SharePreferenceUtils
import com.smartwash.feature.user.api.model.LoginEvent
import com.smartwash.feature.user.api.model.LogoutReason
import com.smartwash.feature.user.impl.repository.UserRepository
import com.smartwash.feature.user.impl.session.SessionManager
import com.smartwash.feature.user.impl.session.SessionEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * UserApiImpl 门面 JVM 单测（Robolectric 供 Context 构造 SessionManager；
 * 桥接协程日志经 AppLogger 触发 android.util.Log，需 Robolectric 环境兜底）。
 *
 * 锚点契约：
 * - isLogin 读 SessionManager token 内存缓存（无 IO）；
 * - 未登录不发起 getUserInfo 网络请求，按契约返回 null；
 * - SessionEventBus 四类事件桥接为 user-api LoginEvent：
 *   NeedLogin→LoggedOut(NEED_LOGIN)、Unauthorized→LoggedOut(UNAUTHORIZED)、
 *   LoggedIn→LoggedIn、LoggedOut→LoggedOut(LOGOUT)。
 *
 * 时序说明：桥接协程在 init 于 Dispatchers.Default 异步订阅事件总线（无 replay，
 * 订阅前的发射会被丢弃）。各用例先经 notifyLoggedIn（用户操作路径，无去重，可安全
 * 重试）预热至首个事件送达，证明桥接已订阅，再测目标事件——网络层事件走去重窗口，
 * 不允许重试，必须在桥接就绪后单次发射。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserApiImplTest {

    private fun newUserApi(
        fakeApi: FakeUserAccountApi = FakeUserAccountApi(),
    ): Triple<UserApiImpl, SessionEventBus, FakeUserAccountApi> {
        val sessionManager = SessionManager(SharePreferenceUtils(ApplicationProvider.getApplicationContext()))
        val bus = SessionEventBus()
        val userApi = UserApiImpl(sessionManager, bus, UserRepository(fakeApi))
        return Triple(userApi, bus, fakeApi)
    }

    /** 后台订阅 loginEvents，onSubscription 回调确认注册完成 */
    private fun subscribeLoginEvents(userApi: UserApiImpl): Pair<CoroutineScope, CopyOnWriteArrayList<LoginEvent>> {
        val received = CopyOnWriteArrayList<LoginEvent>()
        val subscribed = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            userApi.loginEvents
                .onSubscription { subscribed.countDown() }
                .collect { received += it }
        }
        check(subscribed.await(5, TimeUnit.SECONDS)) { "loginEvents 订阅应在 5s 内注册" }
        return scope to received
    }

    /** 用 notifyLoggedIn 重试预热桥接（用户操作事件无去重），返回首个送达是否为 LoggedIn */
    private fun primeBridge(bus: SessionEventBus, received: CopyOnWriteArrayList<LoginEvent>): Boolean {
        val deadline = System.currentTimeMillis() + 5000
        while (received.isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "桥接协程应在 5s 内订阅事件总线" }
            bus.notifyLoggedIn()
            Thread.sleep(25)
        }
        // 等待在途事件收集完（缓冲到收集器是异步分发）
        Thread.sleep(100)
        return received.isNotEmpty() && received.all { it is LoginEvent.LoggedIn }
    }

    private fun awaitSize(received: List<LoginEvent>, expected: Int) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && received.size < expected) Thread.sleep(10)
        assertEquals("LoginEvent 收集数量不符", expected, received.size)
    }

    @Test
    fun `未登录时同步态为 false`() {
        val (userApi, _, _) = newUserApi()
        assertFalse(userApi.isLogin())
    }

    @Test
    fun `未登录不发起网络请求 getUserInfo 返回 null`() = runBlocking {
        val (userApi, _, fakeApi) = newUserApi()
        assertNull(userApi.getUserInfo())
        assertEquals(0, fakeApi.getUserInfoCalls) // 契约：未登录不发起请求
        assertNull(userApi.refreshUserInfo())     // 强刷路径同样不发起
        assertEquals(0, fakeApi.getUserInfoCalls)
        Unit
    }

    @Test
    fun `LoggedIn 与 LoggedOut 桥接为对应用户事件`() {
        val (userApi, bus, _) = newUserApi()
        val (scope, received) = subscribeLoginEvents(userApi)
        try {
            // 预热送达的应全为 LoggedIn（桥接映射正确性的直接证据）
            assertTrue("预热事件应全为 LoggedIn", primeBridge(bus, received))
            val primed = received.size
            bus.notifyLoggedOut()
            awaitSize(received, primed + 1)
            assertEquals(LoginEvent.LoggedOut(LogoutReason.LOGOUT), received.last())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `NeedLogin 桥接为 LoggedOut NEED_LOGIN`() {
        val (userApi, bus, _) = newUserApi()
        val (scope, received) = subscribeLoginEvents(userApi)
        try {
            assertTrue(primeBridge(bus, received))
            received.clear()
            bus.notifyNeedLogin() // 桥接已就绪，网络层事件单次发射（去重窗口不重试）
            awaitSize(received, 1)
            assertEquals(LoginEvent.LoggedOut(LogoutReason.NEED_LOGIN), received[0])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Unauthorized 桥接为 LoggedOut UNAUTHORIZED`() {
        val (userApi, bus, _) = newUserApi()
        val (scope, received) = subscribeLoginEvents(userApi)
        try {
            assertTrue(primeBridge(bus, received))
            received.clear()
            bus.notifyUnauthorized()
            awaitSize(received, 1)
            assertEquals(LoginEvent.LoggedOut(LogoutReason.UNAUTHORIZED), received[0])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `会话事件到达使用户信息缓存失效（不抛异常即可重查）`() = runBlocking {
        val (userApi, bus, _) = newUserApi()
        val (scope, received) = subscribeLoginEvents(userApi)
        try {
            primeBridge(bus, received)
            // 未登录态下缓存本为空；事件到达后 invalidate 路径不应抛异常，门面继续可用
            assertNull(userApi.getUserInfo())
            userApi.invalidateUserInfoCache()
            assertFalse(userApi.isLogin())
        } finally {
            scope.cancel()
        }
    }
}
