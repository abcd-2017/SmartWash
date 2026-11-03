package com.smartwash.feature.lab.ui.bubble

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.random.Random

/**
 * 单个泡泡：网格坐标 + 大小档。
 * [tier] 三档（0 小 / 1 中 / 2 大）驱动两个表现——绘制半径系数与戳破触觉力度
 * （大泡=重、中=中、小=轻），模拟真实泡泡纸的大小混排手感。
 */
data class Bubble(
    val key: String,
    val col: Int,
    val row: Int,
    val tier: Int,
) {
    companion object {

        /** 半径系数（随档递增）：小 0.62 / 中 0.81 / 大 1.0（乘以格内基准半径） */
        fun radiusFactor(tier: Int): Float = 0.62f + tier.coerceIn(0, 2) * 0.19f
    }
}

/**
 * 泡泡纸 ViewModel：泡泡阵列 + 戳破计数（会话内即弃，不持久化——模块约定）。
 *
 * 阵列由页面测得画布尺寸后经 [newSheet] 铺设（cols/rows 由页面按屏宽自适应计算，
 * 目标约 8 列）；[pop] 负责戳破状态与计数的唯一变更入口，返回是否实际戳中
 * （页面据此决定是否播放戳破动画/触觉/pop 音）。
 */
@HiltViewModel
class BubbleWrapViewModel @Inject constructor() : ViewModel() {

    private val _bubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val bubbles: StateFlow<List<Bubble>> = _bubbles.asStateFlow()

    private val _poppedKeys = MutableStateFlow<Set<String>>(emptySet())
    val poppedKeys: StateFlow<Set<String>> = _poppedKeys.asStateFlow()

    private val _poppedCount = MutableStateFlow(0)
    val poppedCount: StateFlow<Int> = _poppedCount.asStateFlow()

    /** 当前阵列泡泡总数（未铺时 0，页面据此判断是否需要首铺） */
    val totalCount: Int get() = _bubbles.value.size

    /** 当前阵列网格规格（cols to rows；未铺时 null，页面据此感知旋转/分屏后的规格变化） */
    var gridSpec: Pair<Int, Int>? = null
        private set

    /**
     * 铺一张新泡泡纸：大小档随机三档混排（[Bubble.tier]）。
     * cols/rows 非法（画布未测量完成）时静默忽略。
     */
    fun newSheet(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        val rng = Random(System.nanoTime())
        _bubbles.value = buildList {
            repeat(rows) { row ->
                repeat(cols) { col ->
                    add(
                        Bubble(
                            key = "${row}_${col}",
                            col = col,
                            row = row,
                            tier = rng.nextInt(BUBBLE_TIERS),
                        )
                    )
                }
            }
        }
        gridSpec = cols to rows
        _poppedKeys.value = emptySet()
        _poppedCount.value = 0
    }

    /**
     * 戳破一个泡泡：仅首次有效（重复戳/越界 key/未铺静默忽略）。
     * @return 是否实际戳中（页面据此触发动画/触觉/声音链路）
     */
    fun pop(key: String): Boolean {
        if (_bubbles.value.none { it.key == key }) return false
        val popped = _poppedKeys.value
        if (key in popped) return false
        _poppedKeys.value = popped + key
        _poppedCount.value = popped.size + 1
        return true
    }

    companion object {

        /** 泡泡大小档位数（0 小 / 1 中 / 2 大） */
        const val BUBBLE_TIERS = 3
    }
}
