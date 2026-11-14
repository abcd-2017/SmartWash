package com.smartwash.feature.lab.ui.drum

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 预设节奏：BPM + 8 行 × 16 步谱面（'x' 触发 / '.' 空）。
 * 名称文案不进 VM（模块约定：VM 不持 UI 文案），由页面经 stringResource 映射。
 */
enum class DrumPreset(val bpm: Int, val rows: List<String>) {
    BASIC(
        120,
        listOf(
            "x...x...x...x...", // 底鼓
            "....x.......x...", // 军鼓
            "x.x.x.x.x.x.x.x.", // 踩镲
            "................", // 拍手
            "................", // 通鼓
            "................", // 牛铃
            "................", // 边击
            "................", // 镲片
        ),
    ),
    GROOVE(
        124,
        listOf(
            "x..x....x..x....",
            "....x.......x...",
            "x.x.x.xxx.x.x.xx",
            "....x.......x..x",
            "................",
            "................",
            "................",
            "................",
        ),
    ),
    BREAKBEAT(
        140,
        listOf(
            "x.....x...x.....",
            "....x..x....x...",
            "xxxxxxxxxxxxxxx.",
            "....x.......x...",
            "..............x.",
            "................",
            "................",
            "x...............",
        ),
    ),
    LATIN(
        110,
        listOf(
            "x..x....x..x....",
            "....x..x....x..x",
            "x.x.x.x.x.x.x.x.",
            "....x.......x...",
            "........x.......",
            "x.x..x.xx.x..x.x",
            "................",
            "................",
        ),
    ),
    ROBOT(
        128,
        listOf(
            "x...x...x...x..x",
            "....x.......x...",
            "x.xxx.xxx.xxx.xx",
            "....x.......x...",
            "............x..x",
            "..x...x...x...x.",
            "x.............x.",
            "...............x",
        ),
    ),
    LAUNDRY(
        100,
        listOf(
            "x.....x.x.......",
            "....x.......x...",
            "x.x.x.x.x.x.x.x.",
            "............x...",
            "..x.......x.....",
            "................",
            "....x.......x...",
            "..............x.",
        ),
    ),
}

/** 节拍机 UI 状态快照（patterns 为不可变列表，DrumPattern 为不可变位图） */
data class DrumUiState(
    val playing: Boolean = false,
    val bpm: Int = DrumPreset.BASIC.bpm,
    val activePattern: Int = 0,
    val patterns: List<DrumPattern> = emptyList(),
)

/**
 * 滚筒节拍机 ViewModel：持有 [DrumEngine]（音频合成 + 步进调度）与 4 个节奏型。
 * 状态会话内即弃（模块约定：退出页面 onCleared 释放引擎）；组合期无副作用。
 *
 * 初始 4 个节奏型分别预置 4 个预设（A/B/C/D 变奏链），其余 2 个预设可一键套用。
 */
@HiltViewModel
class DrumViewModel @Inject constructor() : ViewModel() {

    private val engine = DrumEngine()

    /** 引擎回放的当前步（0..15；停止时 -1），驱动网格播放头 */
    val currentStep: StateFlow<Int> = engine.currentStep

    private val _state = MutableStateFlow(
        DrumUiState(
            patterns = listOf(
                DrumPattern.fromRows(DrumPreset.BASIC.rows),
                DrumPattern.fromRows(DrumPreset.GROOVE.rows),
                DrumPattern.fromRows(DrumPreset.BREAKBEAT.rows),
                DrumPattern.fromRows(DrumPreset.LATIN.rows),
            ),
        ),
    )
    val state: StateFlow<DrumUiState> = _state.asStateFlow()

    init {
        // 引擎侧异常中断（createTrack 失败 / 调度线程崩溃）时回写播放态，
        // 避免 UI 停在假「播放中」（ui-check P1-2：引擎 playing 与 VM 状态脱钩）。
        // 回调在 drum-engine 线程触发，MutableStateFlow 写入本身线程安全。
        engine.onPlaybackStopped = {
            if (_state.value.playing) {
                _state.value = _state.value.copy(playing = false)
            }
        }
    }

    /** 播放/停止切换（引擎即时起停；pattern/bpm 在启动前同步） */
    fun togglePlay() {
        val current = _state.value
        if (current.playing) {
            engine.stop()
            _state.value = current.copy(playing = false)
        } else {
            engine.setBpm(current.bpm)
            engine.setPattern(current.patterns[current.activePattern])
            engine.start()
            _state.value = current.copy(playing = true)
        }
    }

    fun setBpm(bpm: Int) {
        val coerced = bpm.coerceIn(DrumEngine.MIN_BPM, DrumEngine.MAX_BPM)
        if (coerced == _state.value.bpm) return
        _state.value = _state.value.copy(bpm = coerced)
        engine.setBpm(coerced)
    }

    /** 切换节奏型（播放中切换在下一步边界生效，引擎读快照无锁） */
    fun selectPattern(index: Int) {
        if (index !in 0 until PATTERN_COUNT) return
        val current = _state.value
        if (index == current.activePattern) return
        _state.value = current.copy(activePattern = index)
        if (current.playing) engine.setPattern(current.patterns[index])
    }

    /** 切换某声部某步的触发状态（拷贝写入新节奏型，引擎下一 step 读到新快照） */
    fun toggleStep(voice: Int, step: Int) {
        val current = _state.value
        val pattern = current.patterns[current.activePattern]
        val updated = pattern.withStep(voice, step, !pattern.isEnabled(voice, step))
        val patterns = current.patterns.toMutableList().also { it[current.activePattern] = updated }
        _state.value = current.copy(patterns = patterns)
        if (current.playing) engine.setPattern(updated)
    }

    /** 一键套用预设到当前节奏型（同时设 BPM） */
    fun applyPreset(preset: DrumPreset) {
        val current = _state.value
        val pattern = DrumPattern.fromRows(preset.rows)
        val patterns = current.patterns.toMutableList().also { it[current.activePattern] = pattern }
        _state.value = current.copy(patterns = patterns, bpm = preset.bpm)
        engine.setBpm(preset.bpm)
        if (current.playing) engine.setPattern(pattern)
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }

    companion object {
        const val PATTERN_COUNT = 4
    }
}
