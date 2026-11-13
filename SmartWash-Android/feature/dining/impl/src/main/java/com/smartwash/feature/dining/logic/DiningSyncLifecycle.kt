package com.smartwash.feature.dining.logic

/**
 * 同步订阅生命周期状态机（纯 Kotlin，JVM 单测锚点）。
 * 把「首载完成 / 页面可见性」归一为启动或停止订阅的决策，ViewModel 只转发事件。
 *
 * 规则：
 * - 首载未完成时可见 → 暂不启动（等首载完成后由 [Event.FirstLoadReady] 触发）；
 * - 首载完成后可见 → [Command.Start]；
 * - 不可见 → [Command.Stop]（取消订阅即停止轮询，防后台请求与泄露）；
 * - 重复可见/重复首载不产生新决策（VM 侧启动幂等，重复指令无害）。
 */
class DiningSyncLifecycle {

    /** 首载是否完成（详情已就绪，可据其 version 发起带 knownVersion 的订阅） */
    private var ready = false

    /** 页面当前是否可见 */
    private var visible = false

    /** 事件输入；返回需要 VM 执行的指令 */
    fun onEvent(event: Event): Command {
        when (event) {
            Event.FirstLoadReady -> {
                val changed = !ready
                ready = true
                return if (changed && visible) Command.Start else Command.None
            }
            Event.PageVisible -> {
                visible = true
                return if (ready) Command.Start else Command.None
            }
            Event.PageInvisible -> {
                visible = false
                return Command.Stop
            }
            Event.Reset -> {
                ready = false
                visible = false
                return Command.Stop
            }
        }
    }

    /** 生命周期事件（VM 由首载回调与页面 LifecycleEventObserver 转发） */
    enum class Event {
        /** 首次全量拉取成功，详情已就绪 */
        FirstLoadReady,
        /** 页面可见（进入前台/导航回本页） */
        PageVisible,
        /** 页面不可见（退出前台/导航离开本页） */
        PageInvisible,
        /** ViewModel 销毁，重置状态 */
        Reset,
    }

    /** VM 指令 */
    enum class Command {
        /** 无动作 */
        None,
        /** 启动订阅 */
        Start,
        /** 停止订阅 */
        Stop,
    }
}
