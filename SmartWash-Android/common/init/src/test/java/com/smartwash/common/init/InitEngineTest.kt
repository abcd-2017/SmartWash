package com.smartwash.common.init

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * [InitEngine] JVM 单测。
 *
 * 时间语义说明：
 * - 阻塞任务在 executeAll 调用方协程内串行执行，用 runTest 虚拟时间（delay/withTimeout 即时跳转）；
 * - 非阻塞任务运行在 InitEngine 内部真实 scope（SupervisorJob + Dispatchers.Default），
 *   不受 runTest 虚拟时间控制，相关用例改用 runBlocking 以真实时间验证「不阻塞返回」。
 */
class InitEngineTest {

    /** 可编排行为的测试任务：记录执行顺序、捕获成功/失败回调 */
    private class TestInitTask(
        override val taskId: String,
        override val priority: Int = DEFAULT_PRIORITY,
        override val dependencies: List<String> = emptyList(),
        override val blocking: Boolean = DEFAULT_BLOCKING,
        override val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        private val executionOrder: MutableList<String>? = null,
        private val behavior: suspend () -> Unit = {},
    ) : InitTask() {

        var completed = false
            private set

        var failedWith: Throwable? = null
            private set

        override suspend fun execute() {
            executionOrder?.add(taskId)
            behavior()
        }

        override fun onComplete() {
            completed = true
        }

        override fun onFailed(e: Throwable) {
            failedWith = e
        }
    }

    @Test
    fun `拓扑排序 依赖任务先于依赖者执行`() = runTest {
        val order = mutableListOf<String>()
        val taskC = TestInitTask(TASK_C, dependencies = listOf(TASK_B), executionOrder = order)
        val taskB = TestInitTask(TASK_B, dependencies = listOf(TASK_A), executionOrder = order)
        val taskA = TestInitTask(TASK_A, executionOrder = order)
        // 注册顺序故意倒序（C、B、A），验证排序不依赖注册顺序
        InitEngine(listOf(taskC, taskB, taskA)).executeAll()

        assertEquals(listOf(TASK_A, TASK_B, TASK_C), order)
    }

    @Test
    fun `拓扑排序 依赖顺序优先于优先级`() = runTest {
        val order = mutableListOf<String>()
        // B 依赖 A，但 B 优先级数值更小（更急）；依赖约束必须赢，否则 dependsOn 形同虚设
        val taskA = TestInitTask(TASK_A, priority = PRIORITY_LATE, executionOrder = order)
        val taskB = TestInitTask(
            TASK_B,
            priority = PRIORITY_EARLY,
            dependencies = listOf(TASK_A),
            executionOrder = order,
        )

        InitEngine(listOf(taskA, taskB)).executeAll()

        assertEquals(listOf(TASK_A, TASK_B), order)
    }

    @Test
    fun `循环依赖 降级按原顺序执行且不死锁`() = runTest {
        val order = mutableListOf<String>()
        val taskA = TestInitTask(TASK_A, dependencies = listOf(TASK_B), executionOrder = order)
        val taskB = TestInitTask(TASK_B, dependencies = listOf(TASK_A), executionOrder = order)

        val engine = InitEngine(listOf(taskA, taskB))
        engine.executeAll() // Kahn 检测到环即退出，不得死锁

        assertEquals(listOf(TASK_A, TASK_B), order)
        assertTrue("循环依赖降级后任务应全部执行", engine.report.value!!.success)
    }

    @Test
    fun `优先级排序 无依赖时数值小的先执行`() = runTest {
        val order = mutableListOf<String>()
        val taskEarly = TestInitTask(TASK_EARLY, priority = PRIORITY_EARLY, executionOrder = order)
        val taskMiddle = TestInitTask(TASK_MIDDLE, priority = PRIORITY_MIDDLE, executionOrder = order)
        val taskLate = TestInitTask(TASK_LATE, priority = PRIORITY_LATE, executionOrder = order)
        // 注册顺序故意与优先级相反
        InitEngine(listOf(taskLate, taskMiddle, taskEarly)).executeAll()

        assertEquals(listOf(TASK_EARLY, TASK_MIDDLE, TASK_LATE), order)
    }

    @Test
    fun `阻塞任务失败 短路后续阻塞任务`() = runTest {
        val order = mutableListOf<String>()
        val failure = IllegalStateException(SIMULATED_FAILURE_MESSAGE)
        val taskA = TestInitTask(TASK_A, executionOrder = order, behavior = { throw failure })
        val taskB = TestInitTask(TASK_B, executionOrder = order)

        val engine = InitEngine(listOf(taskA, taskB))
        engine.executeAll()

        assertEquals("失败任务之后的阻塞任务不得执行", listOf(TASK_A), order)
        // 注意：withTimeout 跨协程边界会经 stack trace recovery 复制异常（同类型同 message、新实例），
        // 故此处断言类型与 message 而非实例同一
        val received = taskA.failedWith
        assertTrue("onFailed 应收到 IllegalStateException，实际: $received", received is IllegalStateException)
        assertEquals(SIMULATED_FAILURE_MESSAGE, received?.message)
        assertFalse(taskA.completed)
        assertFalse(taskB.completed)
        val progress = engine.progress.value
        assertTrue("progress 应为 Failed，实际: $progress", progress is InitProgress.Failed)
        progress as InitProgress.Failed
        assertEquals(TASK_A, progress.taskId)
        assertTrue(progress.error is IllegalStateException)
        assertEquals(SIMULATED_FAILURE_MESSAGE, progress.error.message)
        assertFalse(engine.report.value!!.success)
        assertEquals(listOf(TASK_A), engine.report.value!!.results.map { it.taskId })
    }

    @Test
    fun `非阻塞任务 不阻塞 executeAll 返回`() = runBlocking {
        // 非阻塞任务在引擎内部真实 scope 执行，本用例必须用真实时间（runBlocking），不能用 runTest
        val gate = CompletableDeferred<Unit>() // 放行前非阻塞任务永远挂起
        val finished = CompletableDeferred<Unit>() // 非阻塞任务 execute 真正跑完的信号
        val order = Collections.synchronizedList(mutableListOf<String>()) // 阻塞/非阻塞任务在不同线程写
        val blockingTask = TestInitTask(TASK_A, executionOrder = order)
        val nonBlockingTask = TestInitTask(
            TASK_B,
            blocking = false,
            executionOrder = order,
            behavior = {
                gate.await()
                finished.complete(Unit)
            },
        )
        val engine = InitEngine(listOf(blockingTask, nonBlockingTask))

        // 真实时间兜底：executeAll 若被非阻塞任务挂起，超时失败而非无限挂起
        withTimeout(REAL_WAIT_TIMEOUT_MS) {
            engine.executeAll()
        }

        assertTrue("阻塞任务全部成功即应报告成功", engine.report.value!!.success)
        assertTrue(engine.progress.value is InitProgress.Completed)
        assertTrue("阻塞任务应已执行", order.contains(TASK_A))
        // 非阻塞任务可能尚未被调度（launch 异步），但无论调度与否都不得已完成
        assertFalse("executeAll 返回时非阻塞任务不得已完成", finished.isCompleted)

        // 收尾：放行闸门并等待后台任务真正结束，避免协程泄漏
        gate.complete(Unit)
        withTimeout(REAL_WAIT_TIMEOUT_MS) { finished.await() }
        assertTrue(nonBlockingTask.completed)
    }

    @Test
    fun `阻塞任务超时 产生 Timeout 结果并回调 onFailed`() = runTest {
        val order = mutableListOf<String>()
        val task = TestInitTask(
            TASK_A,
            timeoutMs = TINY_TIMEOUT_MS,
            executionOrder = order,
            behavior = { delay(LONG_DELAY_MS) }, // 虚拟时间远超超时阈值
        )

        val engine = InitEngine(listOf(task))
        engine.executeAll()

        assertTrue("onFailed 应收到超时异常", task.failedWith is TimeoutCancellationException)
        val result = engine.report.value!!.results.single()
        assertTrue("结果应为 Timeout，实际: $result", result is TaskResult.Timeout)
        // 超时同样属于启动失败：进度应为 Failed、报告 success=false
        assertTrue("超时后 progress 应为 Failed，实际: ${engine.progress.value}", engine.progress.value is InitProgress.Failed)
        assertFalse(engine.report.value!!.success)
    }

    @Test
    fun `全部成功 进度到 Completed 且报告包含任务`() = runTest {
        val order = mutableListOf<String>()
        val observedProgress = AtomicReference<InitProgress>()
        lateinit var engine: InitEngine
        val taskA = TestInitTask(
            TASK_A,
            executionOrder = order,
            behavior = { observedProgress.set(engine.progress.value) },
        )
        val taskB = TestInitTask(TASK_B, dependencies = listOf(TASK_A), executionOrder = order)
        engine = InitEngine(listOf(taskA, taskB))
        engine.executeAll()

        assertEquals(listOf(TASK_A, TASK_B), order)
        assertEquals("首个任务执行期间进度应为 Running(1/2)", InitProgress.Running(FIRST_STEP, TOTAL_TWO), observedProgress.get())
        assertTrue(taskA.completed)
        assertTrue(taskB.completed)
        assertNull(taskA.failedWith)
        assertTrue(engine.progress.value is InitProgress.Completed)
        val report = engine.report.value!!
        assertTrue(report.success)
        assertEquals(TOTAL_TWO, report.results.size)
        assertTrue(report.results.all { it is TaskResult.Success })
        assertTrue("summary 应包含任务 id", report.summary.contains(TASK_A) && report.summary.contains(TASK_B))
    }

    private companion object {
        // 任务 id
        const val TASK_A = "taskA"
        const val TASK_B = "taskB"
        const val TASK_C = "taskC"
        const val TASK_EARLY = "taskEarly"
        const val TASK_MIDDLE = "taskMiddle"
        const val TASK_LATE = "taskLate"

        // 优先级（越小越先执行）
        const val PRIORITY_EARLY = 10
        const val PRIORITY_MIDDLE = 75
        const val PRIORITY_LATE = 200

        // 与 InitTask 默认值对齐
        const val DEFAULT_PRIORITY = 100
        const val DEFAULT_BLOCKING = true
        const val DEFAULT_TIMEOUT_MS = 10_000L

        // 超时用例（runTest 虚拟时间）
        const val TINY_TIMEOUT_MS = 1L
        const val LONG_DELAY_MS = 10_000L

        // 非阻塞用例的真实时间兜底（引擎内部 scope 不受虚拟时间控制）
        const val REAL_WAIT_TIMEOUT_MS = 5_000L

        const val SIMULATED_FAILURE_MESSAGE = "simulated init failure"

        // 进度断言（第 1 步 / 共 2 个阻塞任务）
        const val FIRST_STEP = 1
        const val TOTAL_TWO = 2
    }
}
