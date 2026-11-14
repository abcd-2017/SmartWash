package com.smartwash.feature.lab.ui.fluid

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 流体配色预设（3 套）。颜色本身由页面从清氧设计令牌映射，
 * 枚举只表达语义档位，便于首页/页面共享与本地化。
 */
enum class FluidPreset {
    /** 清氧水色：水蓝 + 自然绿 + 水色浅底 */
    OXYGEN_WATER,

    /** 泡沫白：白 + 水色浅底 + 浅绿 */
    FOAM_WHITE,

    /** 落沙暖色：暖橙 + 朱红 + 暖沙浅底 */
    WARM_SAND,
}

/**
 * 指尖流体 ViewModel：配色预设 + 播放意图 + 重置信号（会话内即弃，模块约定）。
 *
 * [playing] 只表达「用户是否主动播放」；正常态下页面恒为播放，
 * reduced motion 下才以本状态为准（静帧为默认）。
 */
@HiltViewModel
class FluidViewModel @Inject constructor() : ViewModel() {

    private val _preset = MutableStateFlow(FluidPreset.OXYGEN_WATER)
    val preset: StateFlow<FluidPreset> = _preset.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _resetToken = MutableStateFlow(0)
    val resetToken: StateFlow<Int> = _resetToken.asStateFlow()

    fun selectPreset(preset: FluidPreset) {
        _preset.value = preset
    }

    fun setPlaying(playing: Boolean) {
        _playing.value = playing
    }

    /** 触发一次重置（页面观察到 token 变化后通知渲染器重铺静态种子） */
    fun reset() {
        _resetToken.value += 1
    }
}
