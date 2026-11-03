package com.smartwash.feature.lab.ui.painting

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject

/**
 * 今日一画 ViewModel：持有种子与生成内核 [PaintingGenerator]（纯 Kotlin）。
 *
 * 种子来源三选一：默认「日期种子」（今日一幅，跨天自动换画）、
 * [randomize] 随机六位数字种子、[confirmSeedInput] 手动输入数字种子。
 * 同一数字种子必然生成同一幅画（内核确定性，见 [PaintingRandom]）。
 *
 * 模块约定：纯本地玩具，状态会话内即弃、退出页面即释放，不持久化。
 * 保存图片由页面渲染位图后写 MediaStore，不经过本 VM。
 */
@HiltViewModel
class PaintingViewModel @Inject constructor() : ViewModel() {

    /** 今日日期（只取一次，种子与日期文案同源） */
    private val today: LocalDate = LocalDate.now()

    /** 今日日期种子（判断当前画是否「今日」用；VM 不持 UI 文案） */
    val todaySeed: PaintingSeed =
        PaintingSeed.fromDate(today.year, today.monthValue, today.dayOfMonth)

    /** 今日日期（页面据此格式化日期文案） */
    val todayDate: LocalDate = today

    private val _seed = MutableStateFlow(todaySeed)
    val seed: StateFlow<PaintingSeed> = _seed.asStateFlow()

    private val _scene = MutableStateFlow(PaintingGenerator(todaySeed).generate())
    val scene: StateFlow<PaintingScene> = _scene.asStateFlow()

    private val _showSeedDialog = MutableStateFlow(false)
    val showSeedDialog: StateFlow<Boolean> = _showSeedDialog.asStateFlow()

    private val _seedInput = MutableStateFlow("")
    val seedInput: StateFlow<String> = _seedInput.asStateFlow()

    private val _seedInputError = MutableStateFlow(false)
    val seedInputError: StateFlow<Boolean> = _seedInputError.asStateFlow()

    /** 换一张：随机六位数字种子（页面同时发 LIGHT 触觉） */
    fun randomize() {
        applySeed(PaintingSeed.random())
    }

    /** 回到今日：恢复日期种子 */
    fun backToToday() {
        applySeed(todaySeed)
    }

    fun openSeedDialog() {
        _seedInput.value = ""
        _seedInputError.value = false
        _showSeedDialog.value = true
    }

    fun dismissSeedDialog() {
        _showSeedDialog.value = false
    }

    fun updateSeedInput(value: String) {
        _seedInput.value = value
        _seedInputError.value = false
    }

    /** 确认手动输入：非数字种子标错保留弹窗，合法则换画并关弹窗 */
    fun confirmSeedInput() {
        val parsed = _seedInput.value.trim().toLongOrNull()
        if (parsed == null) {
            _seedInputError.value = true
            return
        }
        applySeed(PaintingSeed(parsed))
        _showSeedDialog.value = false
    }

    private fun applySeed(seed: PaintingSeed) {
        _seed.value = seed
        _scene.value = PaintingGenerator(seed).generate()
    }
}
