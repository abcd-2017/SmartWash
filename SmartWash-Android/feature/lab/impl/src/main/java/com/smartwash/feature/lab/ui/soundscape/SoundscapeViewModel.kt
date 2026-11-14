package com.smartwash.feature.lab.ui.soundscape

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
 * 预设配方：键为声道、值为该声道音量（0..100）；未列出的声道视为关闭。
 * 名称文案不进 VM（模块约定：VM 不持 UI 文案），由页面经 stringResource 映射。
 */
enum class SoundscapePreset(val channels: Map<SoundChannel, Int>) {
    /** 自习室：雨声 + 翻书 + 少量粉噪底 */
    STUDY(
        mapOf(
            SoundChannel.RAIN to 70,
            SoundChannel.PAGE_FLIP to 45,
            SoundChannel.PINK_NOISE to 20,
        ),
    ),

    /** 睡前：棕噪 + 风声 */
    SLEEP(
        mapOf(
            SoundChannel.BROWN_NOISE to 70,
            SoundChannel.WIND to 40,
        ),
    ),

    /** 宿舍夜：键盘 + 远处交谈 + 少量棕噪底 */
    DORM_NIGHT(
        mapOf(
            SoundChannel.KEYBOARD to 55,
            SoundChannel.MURMUR to 45,
            SoundChannel.BROWN_NOISE to 15,
        ),
    ),

    /** 篝火夜话：篝火 + 风 + 少量棕噪底 */
    CAMPFIRE(
        mapOf(
            SoundChannel.CAMPFIRE to 70,
            SoundChannel.WIND to 30,
            SoundChannel.BROWN_NOISE to 20,
        ),
    ),

    /** 海边：海浪 + 风 + 少量白噪底 */
    OCEAN(
        mapOf(
            SoundChannel.OCEAN to 75,
            SoundChannel.WIND to 25,
            SoundChannel.WHITE_NOISE to 10,
        ),
    ),
}

/** 单路声道的 UI 状态（开关 + 独立音量） */
data class SoundChannelUiState(
    val channel: SoundChannel,
    val enabled: Boolean = false,
    val volume: Int = SoundscapeEngine.DEFAULT_VOLUME,
)

/**
 * 声景混音器 UI 状态快照。
 *
 * [playing] 由引擎回投（[SoundscapeEngine.playing]），避免主线程轮询；
 * [timerRemainingMs] 为睡眠定时剩余毫秒（0 表示无定时）。
 */
data class SoundscapeUiState(
    val playing: Boolean = false,
    val channels: List<SoundChannelUiState> = defaultChannels(),
    val activePreset: SoundscapePreset? = null,
    val timerMinutes: Int = 0,
    val timerRemainingMs: Long = 0,
)

/**
 * 声景混音器 ViewModel：持有 [SoundscapeEngine]（程序化合成 + 多路混音）与预设配方。
 * 状态会话内即弃（模块约定：退出页面 onCleared 释放引擎）；组合期无副作用。
 *
 * 进入页面不自动播放，由用户主动开启；睡眠定时到点先 [SoundscapeEngine.fadeOut]
 * 淡出再停止，避免骤停惊扰。
 */
@HiltViewModel
class SoundscapeViewModel @Inject constructor() : ViewModel() {

    private val engine = SoundscapeEngine()

    private val _state = MutableStateFlow(SoundscapeUiState())
    val state: StateFlow<SoundscapeUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        // 引擎播放态回投到 UI 状态（start/stop/定时到点统一走引擎）
        viewModelScope.launch {
            engine.playing.collect { playing ->
                _state.value = _state.value.copy(playing = playing)
            }
        }
    }

    /** 播放 / 停止切换（用户主动操作；停止时同时取消睡眠定时与淡出） */
    fun togglePlay() {
        if (engine.playing.value) {
            cancelTimer()
            engine.stop()
        } else {
            engine.start()
        }
    }

    /** 开关某路声道（手动调整即脱离预设） */
    fun toggleChannel(channel: SoundChannel) {
        val current = _state.value
        val ui = current.channels.firstOrNull { it.channel == channel } ?: return
        val newEnabled = !ui.enabled
        _state.value = current.copy(
            channels = current.channels.map {
                if (it.channel == channel) it.copy(enabled = newEnabled) else it
            },
            activePreset = null,
        )
        engine.setChannelEnabled(channel, newEnabled)
    }

    /** 设置某路音量（0..100，越界自动夹取；手动调整即脱离预设） */
    fun setVolume(channel: SoundChannel, volume: Int) {
        val coerced = volume.coerceIn(0, 100)
        val current = _state.value
        val updated = current.channels.map { ui ->
            if (ui.channel == channel) ui.copy(volume = coerced) else ui
        }
        _state.value = current.copy(channels = updated, activePreset = null)
        engine.setVolume(channel, coerced)
    }

    /** 一键套用预设（同时设置开关与音量；未列出的声道关闭） */
    fun applyPreset(preset: SoundscapePreset) {
        val current = _state.value
        _state.value = current.copy(
            channels = current.channels.map { ui ->
                val volume = preset.channels[ui.channel]
                if (volume != null) {
                    ui.copy(enabled = true, volume = volume)
                } else {
                    ui.copy(enabled = false)
                }
            },
            activePreset = preset,
        )
        current.channels.forEach { ui ->
            val volume = preset.channels[ui.channel]
            engine.setChannelEnabled(ui.channel, volume != null)
            if (volume != null) engine.setVolume(ui.channel, volume)
        }
    }

    /**
     * 设置睡眠定时（分钟；0 = 关闭）。
     * 仅在播放中可设置新定时；到点先淡出 [FADE_OUT_MS] 再停止。
     */
    fun setTimer(minutes: Int) {
        timerJob?.cancel()
        timerJob = null
        engine.cancelFadeOut()

        if (minutes <= 0) {
            _state.value = _state.value.copy(timerMinutes = 0, timerRemainingMs = 0)
            return
        }
        if (!_state.value.playing) return

        val totalMs = minutes * 60_000L
        _state.value = _state.value.copy(timerMinutes = minutes, timerRemainingMs = totalMs)
        timerJob = viewModelScope.launch {
            try {
                var remaining = totalMs
                while (remaining > FADE_OUT_MS) {
                    _state.value = _state.value.copy(timerRemainingMs = remaining)
                    delay(TIMER_TICK_MS)
                    remaining -= TIMER_TICK_MS
                }
                // 到点：先淡出，淡出结束再停止
                engine.fadeOut(FADE_OUT_MS)
                _state.value = _state.value.copy(timerRemainingMs = FADE_OUT_MS.toLong())
                delay(FADE_OUT_MS.toLong())
                engine.stop()
                _state.value = _state.value.copy(
                    timerMinutes = 0,
                    timerRemainingMs = 0,
                )
            } catch (e: CancellationException) {
                // 取消原样 rethrow（手动停止 / 关闭定时 / 页面销毁），不能吞成异常
                throw e
            }
        }
    }

    /** 取消睡眠定时与进行中的淡出（恢复满增益） */
    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        engine.cancelFadeOut()
        _state.value = _state.value.copy(timerMinutes = 0, timerRemainingMs = 0)
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }

    companion object {
        /** 睡眠定时倒计时精度（ms） */
        private const val TIMER_TICK_MS = 1_000L

        /** 到点淡出时长（ms）：与 VM 定时收尾延迟一致 */
        private const val FADE_OUT_MS = 3_000

        /** 定时器可选档位（分钟）；0 = 关闭 */
        val TIMER_OPTIONS = listOf(0, 15, 30, 60)
    }
}

/** 默认声道表：全部关闭，音量取引擎默认值 */
private fun defaultChannels(): List<SoundChannelUiState> =
    SoundChannel.entries.map { channel ->
        SoundChannelUiState(
            channel = channel,
            enabled = false,
            volume = SoundscapeEngine.DEFAULT_VOLUME,
        )
    }
