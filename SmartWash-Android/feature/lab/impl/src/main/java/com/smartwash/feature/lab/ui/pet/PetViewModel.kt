package com.smartwash.feature.lab.ui.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.SharePreferenceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 泡泡精灵 UI 状态：引擎快照 + 放置回报档位 + 进化代际 + 是否已完成持久化装载。
 * [evolveGeneration] 单调递增，页面据此触发**一次性**进化动画（代际不变则不重复播）。
 */
data class PetUiState(
    val snapshot: PetSnapshot = PetSnapshot(),
    val absenceBucket: AbsenceBucket = AbsenceBucket.FIRST_MEET,
    val evolveGeneration: Int = 0,
    val loaded: Boolean = false,
)

/**
 * 泡泡精灵 ViewModel：持有 [PetEngine]（纯内核），经 [SharePreferenceUtils]（DataStore）
 * 持久化「进化阶段 + 累计互动次数 + 上次见面时刻」三项。
 *
 * 持久化策略：
 * - 装配在 init 异步完成（不阻塞进页面），期间 [interact]/[tick] 静默丢弃，避免半装配态写盘；
 * - 写入走 [viewModelScope]，全程无 runBlocking；
 * - 见面时刻（lastSeenAt）在装配时刷新一次，放置回报档位取**刷新前**的值。
 *
 * 组合期无副作用：状态迁移只发生在 [interact]/[tick]，均由页面事件或 LaunchedEffect 驱动。
 */
@HiltViewModel
class PetViewModel @Inject constructor(
    private val prefs: SharePreferenceUtils,
) : ViewModel() {

    private val engine = PetEngine()

    private val _state = MutableStateFlow(PetUiState())
    val state: StateFlow<PetUiState> = _state.asStateFlow()

    /** 持久化装配完成前，互动与 tick 一律丢弃（页面此时也已渲染默认快照） */
    @Volatile
    private var loaded = false

    init {
        viewModelScope.launch {
            val stageName = runCatching { prefs.getData(KEY_STAGE, PetStage.BUBBLE.name) }
                .onFailure { AppLogger.warn(TAG, "宠物阶段读取失败，按初始阶段降级", it) }
                .getOrDefault(PetStage.BUBBLE.name)
            val stage = runCatching { PetStage.valueOf(stageName) }
                .onFailure { AppLogger.warn(TAG, "宠物阶段枚举还原失败 stageName=$stageName，按初始阶段降级", it) }
                .getOrDefault(PetStage.BUBBLE)
            val count = runCatching { prefs.getData(KEY_COUNT, 0) }
                .onFailure { AppLogger.warn(TAG, "宠物互动计数读取失败，按 0 降级", it) }
                .getOrDefault(0)
            val lastSeen = runCatching { prefs.getData(KEY_LAST_SEEN, 0L) }
                .onFailure { AppLogger.warn(TAG, "宠物上次见面时刻读取失败，按 0 降级", it) }
                .getOrDefault(0L)

            engine.restore(stage, count, lastSeen)
            // 先取放置回报（刷新前），再标记本次见面
            val bucket = engine.absenceBucket()
            val now = System.currentTimeMillis()
            engine.markSeen(now)

            _state.value = PetUiState(
                snapshot = engine.snapshot(),
                absenceBucket = bucket,
                evolveGeneration = 0,
                loaded = true,
            )
            persistAsync()
            loaded = true
        }
    }

    /**
     * 一次互动。引擎同步迁移后发布新快照；返回结果供页面发触觉
     * （进化 SUCCESS / 抚摸 LIGHT / 喂食·玩耍 SELECTION）。
     */
    fun interact(action: PetAction): PetInteractResult {
        if (!loaded) return PetInteractResult.Debounced
        val result = engine.interact(action)
        if (result is PetInteractResult.Accepted) {
            _state.value = _state.value.copy(
                snapshot = result.snapshot,
                evolveGeneration = if (result.evolved) {
                    _state.value.evolveGeneration + 1
                } else {
                    _state.value.evolveGeneration
                },
            )
            persistAsync()
        }
        return result
    }

    /** 时间推进（页面 LaunchedEffect 周期调用）：仅在发生状态迁移时发布 */
    fun tick() {
        if (!loaded) return
        val result = engine.tick()
        if (result.changed) {
            _state.value = _state.value.copy(snapshot = result.snapshot)
        }
    }

    private fun persistAsync() {
        val snapshot = engine.snapshot()
        viewModelScope.launch {
            runCatching {
                prefs.saveData(KEY_STAGE, snapshot.stage.name)
                prefs.saveData(KEY_COUNT, snapshot.interactionCount)
                prefs.saveData(KEY_LAST_SEEN, snapshot.lastSeenAt)
            }.onFailure {
                AppLogger.warn(TAG, "宠物进度落盘失败，进度回退至上次持久化", it)
            }
        }
    }

    companion object {
        // 存储 key 归本玩具自持（SharePreferenceUtils 不感知业务 key）
        private const val KEY_STAGE = "lab_pet_stage"
        private const val KEY_COUNT = "lab_pet_interaction_count"
        private const val KEY_LAST_SEEN = "lab_pet_last_seen"

        private const val TAG = "PetViewModel"
    }
}
