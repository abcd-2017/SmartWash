package com.smartwash.common.init

/**
 * 初始化任务基类
 *
 * 各模块通过继承本类定义启动时执行的初始化逻辑，并在自己的 Hilt Module 中
 * 以 `@Provides @IntoSet` 注册（或 `@Inject` 构造 + `@IntoSet`），
 * [InitEngine] 会自动收集全部任务，按依赖拓扑 + 优先级统一执行。
 */
abstract class InitTask {

    /** 任务唯一标识（用于依赖声明、日志与报告） */
    abstract val taskId: String

    /** 优先级（越小越先执行），默认 100 */
    open val priority: Int = 100

    /** 依赖的其他任务 taskId，用于拓扑排序（被依赖者先执行） */
    open val dependencies: List<String> = emptyList()

    /**
     * 是否阻塞启动：
     * - true  = 按序串行执行，任一失败短路终止（后续阻塞任务不再执行）
     * - false = 异步执行、不等待完成（失败仅记日志与回调）
     */
    open val blocking: Boolean = true

    /** 单任务超时（毫秒），默认 10 秒 */
    open val timeoutMs: Long = 10_000L

    /** 执行初始化逻辑（所需依赖经 Hilt 构造注入，含 @ApplicationContext，无需参数传递） */
    abstract suspend fun execute()

    /** 成功回调（可选） */
    open fun onComplete() {}

    /** 失败回调（可选），默认仅记日志 */
    open fun onFailed(e: Throwable) {
        android.util.Log.e(TAG, "InitTask failed: $taskId", e)
    }

    private companion object {
        const val TAG = "InitTask"
    }
}
