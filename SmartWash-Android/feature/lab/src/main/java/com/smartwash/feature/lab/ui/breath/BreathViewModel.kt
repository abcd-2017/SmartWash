package com.smartwash.feature.lab.ui.breath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 呼吸相位类型：吸气（圆放大）/ 屏息（保持）/ 呼气（圆缩小）/ 呼气后保持
 * （仅 Box 模式的第四相位，即 Box 4-4-4-4 的尾保持）。
 */
enum class BreathPhaseType { INHALE, HOLD, EXHALE, HOLD_TAIL }

/** 单相位定义（类型 + 时长 ms；时长为产品规格数字，非动效时长） */
data class BreathPhase(val type: BreathPhaseType, val durationMs: Long)

/**
 * 呼吸模式：4-7-8 睡眠（吸 4 / 屏 7 / 呼 8）与 Box 4-4-4-4 专注（四相位各 4）。
 */
enum class BreathMode {
    SLEEP,
    BOX;

    companion object {

        /** 模式相位表（单一事实来源，页面只消费不携带时长） */
        fun phasesOf(mode: BreathMode): List<BreathPhase> = when (mode) {
            SLEEP -> listOf(
                BreathPhase(BreathPhaseType.INHALE, 4_000L),
                BreathPhase(BreathPhaseType.HOLD, 7_000L),
                BreathPhase(BreathPhaseType.EXHALE, 8_000L),
            )
            BOX -> listOf(
                BreathPhase(BreathPhaseType.INHALE, 4_000L),
                BreathPhase(BreathPhaseType.HOLD, 4_000L),
                BreathPhase(BreathPhaseType.EXHALE, 4_000L),
                BreathPhase(BreathPhaseType.HOLD_TAIL, 4_000L),
            )
        }
    }
}

/**
 * 引导状态机快照。[mode] 为 null = 未选择；[running] 区分选择态与引导态；
 * [paused] 时 [remainingMs] 冻结（协程取消，恢复时以剩余量继续）。
 */
data class BreathUiState(
    val mode: BreathMode? = null,
    val running: Boolean = false,
    val paused: Boolean = false,
    val phaseIndex: Int = 0,
    val remainingMs: Long = 0,
    val cycles: Int = 0,
) {
    /** 当前相位（未开始返回 null） */
    val phase: BreathPhase?
        get() = mode?.let { BreathMode.phasesOf(it).getOrNull(phaseIndex) }
}

/**
 * 呼吸引导 ViewModel：相位状态机由协程 tick 驱动（[TICK_MS] 粒度），
 * 相位时长/推进/轮次全部收敛在此；页面只消费快照渲染（圆环缩放的
 * 平滑插值由页面 Animatable 承担，时长取 [BreathUiState.remainingMs]）。
 */
@HiltViewModel
class BreathViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BreathUiState())
    val state: StateFlow<BreathUiState> = _state.asStateFlow()

    private var ticker: Job? = null

    /** 选择态下切换模式（未开始不推进状态机；切换会重置轮次与相位） */
    fun selectMode(mode: BreathMode) {
        if (_state.value.running) return
        _state.value = BreathUiState(mode = mode)
    }

    /** 开始引导（引导中重复调用忽略；切模式 = 先 stop 再 start） */
    fun start(mode: BreathMode) {
        if (_state.value.running) return
        ticker?.cancel()
        val phases = BreathMode.phasesOf(mode)
        _state.value = BreathUiState(
            mode = mode,
            running = true,
            remainingMs = phases.first().durationMs,
        )
        launchTicker()
    }

    /** 暂停：取消 tick 协程，remainingMs 冻结（圆环由页面停在当前帧） */
    fun pause() {
        if (!_state.value.running || _state.value.paused) return
        ticker?.cancel()
        _state.value = _state.value.copy(paused = true)
    }

    /** 继续：以剩余时长续起 tick（圆环动画由页面按 remainingMs 恢复） */
    fun resume() {
        if (!_state.value.running || !_state.value.paused) return
        _state.value = _state.value.copy(paused = false)
        launchTicker()
    }

    /** 停止引导：回模式选择态（保留所选模式高亮，清零相位/轮次） */
    fun stop() {
        ticker?.cancel()
        _state.value = BreathUiState(mode = _state.value.mode)
    }

    private fun launchTicker() {
        ticker = viewModelScope.launch {
            try {
                while (true) {
                    delay(TICK_MS)
                    onTick()
                }
            } catch (e: CancellationException) {
                // 取消原样 rethrow（暂停/停止/页面销毁），不能吞成异常
                throw e
            }
        }
    }

    /** tick 推进：倒计时递减，跨零切相位（尾相位回卷即完成一轮 cycles+1） */
    private fun onTick() {
        val s = _state.value
        val phases = BreathMode.phasesOf(s.mode ?: return)
        var remaining = s.remainingMs - TICK_MS
        var phaseIndex = s.phaseIndex
        var cycles = s.cycles
        while (remaining <= 0) {
            phaseIndex += 1
            if (phaseIndex >= phases.size) {
                phaseIndex = 0
                cycles += 1
            }
            remaining += phases[phaseIndex].durationMs
        }
        _state.value = s.copy(remainingMs = remaining, phaseIndex = phaseIndex, cycles = cycles)
    }

    companion object {

        /** 状态机节拍（倒计时精度；圆环缩放的平滑由页面 Animatable 插值承担） */
        const val TICK_MS = 100L
    }
}
