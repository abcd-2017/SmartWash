package com.smartwash.common.init

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.PriorityQueue

/**
 * 初始化引擎：收集全部 [InitTask] 并统一执行。
 *
 * 执行语义：
 * 1. 拓扑排序（[InitTask.dependencies]）+ 优先级排序（[InitTask.priority]，仅在同层就绪任务间生效，依赖关系始终优先）
 * 2. 阻塞任务按序串行执行，任一失败或超时短路终止
 * 3. 非阻塞任务 launch 后立即返回，**不等待完成**（结果经各任务 onFailed 回调自行处理）
 *
 * 进度与报告经 StateFlow 暴露，供启动页 / 日志消费。
 */
class InitEngine(
    tasks: List<InitTask>,
) {

    private val allTasks = tasks.toList()

    /** 非阻塞任务的宿主协程：Application 级，存活至进程结束 */
    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _progress = MutableStateFlow<InitProgress>(InitProgress.Idle)
    val progress: StateFlow<InitProgress> = _progress.asStateFlow()

    private val _report = MutableStateFlow<InitReport?>(null)
    val report: StateFlow<InitReport?> = _report.asStateFlow()

    /**
     * 执行全部初始化任务。
     * 阻塞任务全部成功后即返回；非阻塞任务已在后台启动。
     */
    suspend fun executeAll() {
        val sorted = topologicalSort(allTasks)
        val blockingTasks = sorted.filter { it.blocking }
        val nonBlockingTasks = sorted.filter { !it.blocking }

        val results = mutableListOf<TaskResult>()

        blockingTasks.forEachIndexed { index, task ->
            _progress.value = InitProgress.Running(index + 1, blockingTasks.size)
            val result = executeTaskWithTimeout(task)
            results.add(result)
            // 失败与超时同样属于启动失败，统一短路：后续阻塞任务不再执行
            val failure: Throwable? = when (result) {
                is TaskResult.Success -> null
                is TaskResult.Failed -> result.error
                is TaskResult.Timeout -> result.error
            }
            if (failure != null) {
                _progress.value = InitProgress.Failed(task.taskId, failure)
                _report.value = InitReport(results, success = false)
                return
            }
        }

        // 非阻塞任务：启动即忘，不挂起 executeAll
        nonBlockingTasks.forEach { task ->
            asyncScope.launch {
                val result = executeTaskWithTimeout(task)
                if (result !is TaskResult.Success) {
                    Log.w(TAG, "Non-blocking init task did not succeed: $result")
                }
            }
        }

        _progress.value = InitProgress.Completed
        _report.value = InitReport(results, success = true)
        Log.i(TAG, "Init completed: ${_report.value?.summary}")
    }

    /** 执行单个任务（带超时保护）；协程取消必须向上传播 */
    private suspend fun executeTaskWithTimeout(task: InitTask): TaskResult {
        return try {
            withTimeout(task.timeoutMs) {
                task.execute()
            }
            task.onComplete()
            TaskResult.Success(task.taskId)
        } catch (e: TimeoutCancellationException) {
            task.onFailed(e)
            TaskResult.Timeout(task.taskId, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            task.onFailed(e)
            TaskResult.Failed(task.taskId, e)
        }
    }

    /**
     * 拓扑排序（Kahn 算法）：保证被依赖的任务先执行。
     *
     * 就绪队列按 [InitTask.priority] 出队（同优先级保持注册顺序）——优先级只在
     * 「同时就绪」的任务间生效，依赖关系始终优先于优先级（依赖者必须等全部依赖出队）。
     * 检测到循环依赖时记错误日志并按原注册顺序降级执行。
     */
    private fun topologicalSort(tasks: List<InitTask>): List<InitTask> {
        val taskMap = tasks.associateBy { it.taskId }
        val inDegree = mutableMapOf<String, Int>()
        val adjacency = mutableMapOf<String, MutableList<String>>()

        tasks.forEach { task ->
            inDegree[task.taskId] = 0
            adjacency[task.taskId] = mutableListOf()
        }

        tasks.forEach { task ->
            task.dependencies.forEach { depId ->
                if (taskMap.containsKey(depId)) {
                    adjacency[depId]?.add(task.taskId)
                    inDegree[task.taskId] = (inDegree[task.taskId] ?: 0) + 1
                } else {
                    Log.w(TAG, "Task ${task.taskId} depends on unknown task: $depId")
                }
            }
        }

        // 同优先级的 tie-break 用注册顺序，保证排序确定性
        val registrationIndex = tasks.withIndex().associate { (index, task) -> task.taskId to index }
        val ready = PriorityQueue<InitTask>(
            compareBy<InitTask> { it.priority }.thenBy { registrationIndex[it.taskId] ?: 0 }
        )
        inDegree.filter { it.value == 0 }.keys.forEach { id -> taskMap[id]?.let(ready::add) }

        val sorted = mutableListOf<InitTask>()
        while (ready.isNotEmpty()) {
            val current = ready.poll()
            sorted.add(current)
            adjacency[current.taskId]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 0) - 1
                if (inDegree[neighbor] == 0) {
                    taskMap[neighbor]?.let(ready::add)
                }
            }
        }

        if (sorted.size != tasks.size) {
            Log.e(TAG, "Circular dependency detected! Sorted ${sorted.size} of ${tasks.size} tasks, falling back to original order")
            return tasks
        }
        return sorted
    }

    private companion object {
        const val TAG = "InitEngine"
    }
}

// ========== 状态与结果 ==========

sealed class InitProgress {
    data object Idle : InitProgress()
    data class Running(val current: Int, val total: Int) : InitProgress()
    data object Completed : InitProgress()
    data class Failed(val taskId: String, val error: Throwable) : InitProgress()
}

sealed class TaskResult {
    abstract val taskId: String

    data class Success(override val taskId: String) : TaskResult()
    data class Failed(override val taskId: String, val error: Throwable) : TaskResult()
    data class Timeout(override val taskId: String, val error: TimeoutCancellationException) : TaskResult()
}

data class InitReport(
    val results: List<TaskResult>,
    val success: Boolean,
) {
    val summary: String
        get() = buildString {
            append("InitReport(success=$success, tasks=${results.size}): ")
            results.forEach { result ->
                when (result) {
                    is TaskResult.Success -> append("✓ ${result.taskId} ")
                    is TaskResult.Failed -> append("✗ ${result.taskId}(${result.error.javaClass.simpleName}) ")
                    is TaskResult.Timeout -> append("⏱ ${result.taskId} ")
                }
            }
        }
}
