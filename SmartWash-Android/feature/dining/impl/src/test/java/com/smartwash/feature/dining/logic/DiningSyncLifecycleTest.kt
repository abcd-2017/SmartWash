package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.logic.DiningSyncLifecycle.Command
import com.smartwash.feature.dining.logic.DiningSyncLifecycle.Event
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 同步订阅生命周期状态机 JVM 单测（锚点）：首载门禁、可见启动、不可见停止、重置。
 */
class DiningSyncLifecycleTest {

    @Test
    fun `首载未完成时可见不启动`() {
        val lifecycle = DiningSyncLifecycle()
        assertEquals(Command.None, lifecycle.onEvent(Event.PageVisible))
    }

    @Test
    fun `首载完成后按可见性启动`() {
        val lifecycle = DiningSyncLifecycle()
        // 首载时页面不可见 → 不启动
        assertEquals(Command.None, lifecycle.onEvent(Event.FirstLoadReady))
        // 重复首载幂等
        assertEquals(Command.None, lifecycle.onEvent(Event.FirstLoadReady))
        // 进入前台 → 启动
        assertEquals(Command.Start, lifecycle.onEvent(Event.PageVisible))
    }

    @Test
    fun `先见后载以首载事件启动`() {
        val lifecycle = DiningSyncLifecycle()
        assertEquals(Command.None, lifecycle.onEvent(Event.PageVisible))
        assertEquals(Command.Start, lifecycle.onEvent(Event.FirstLoadReady))
    }

    @Test
    fun `重复首载幂等`() {
        val lifecycle = DiningSyncLifecycle()
        lifecycle.onEvent(Event.PageVisible)
        assertEquals(Command.Start, lifecycle.onEvent(Event.FirstLoadReady))
        assertEquals(Command.None, lifecycle.onEvent(Event.FirstLoadReady))
    }

    @Test
    fun `不可见停止且回前台再启动`() {
        val lifecycle = DiningSyncLifecycle()
        lifecycle.onEvent(Event.FirstLoadReady)
        lifecycle.onEvent(Event.PageVisible)

        assertEquals(Command.Stop, lifecycle.onEvent(Event.PageInvisible))
        assertEquals(Command.Start, lifecycle.onEvent(Event.PageVisible))
    }

    @Test
    fun `重置后需重新首载才启动`() {
        val lifecycle = DiningSyncLifecycle()
        lifecycle.onEvent(Event.FirstLoadReady)
        lifecycle.onEvent(Event.PageVisible)

        lifecycle.onEvent(Event.Reset)

        assertEquals(Command.None, lifecycle.onEvent(Event.PageVisible))
        assertEquals(Command.Start, lifecycle.onEvent(Event.FirstLoadReady))
    }
}
