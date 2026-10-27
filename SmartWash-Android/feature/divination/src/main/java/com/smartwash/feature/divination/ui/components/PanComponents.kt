package com.smartwash.feature.divination.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.feature.divination.core.liuyao.LiuYaoChart
import com.smartwash.feature.divination.core.liuyao.LiuYaoFacts
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 六爻逐爻展开的单爻时长 — 350ms（规范 7：进入减速 LinearOutSlowInEasing） */
private const val YAO_DRAW_MS = 350

/** 六爻逐爻展开的交叠间隔 — 60ms（规范 7：逐项间隔 ≤60ms） */
private const val YAO_STAGGER_MS = 60L

/**
 * 六爻装卦行（原型 ChartLiuyao）：六神色点 / 宣纸(墨)爻线 / 动爻○× / 六亲干支 / 世应。
 * 支持自初爻向上 draw-on 入场（阳爻自中心向两端展开、阴爻两条同展）。
 */

/** 爻线：阳爻六段、阴爻两组四段（画板 ▅▅ 口径）；progress 0..1 控制展开；
 *  动爻在玄墨侧整段鎏金（画板 #D4B878），宣纸侧与静爻同为墨 */
@Composable
private fun YaoBar(
    yang: Boolean,
    moving: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val brush = if (moving && c.isDark) {
        Brush.horizontalGradient(listOf(c.goldHi, c.goldHi))
    } else {
        Brush.horizontalGradient(listOf(c.yaoTop, c.yaoBottom))
    }
    @Composable
    fun Segment(segmentModifier: Modifier) {
        Box(segmentModifier.height(9.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(9.dp)
                    .background(brush, RoundedCornerShape(2.dp))
            )
        }
    }
    if (yang) {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(6) { Segment(Modifier.weight(1f)) }
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Segment(Modifier.width(15.dp))
            Segment(Modifier.width(15.dp))
            Spacer(Modifier.width(6.dp))
            Segment(Modifier.width(15.dp))
            Segment(Modifier.width(15.dp))
        }
    }
}

/** 六爻装卦一行（画板：神煞 12 弱化灰无色点 / 爻线 100 槽 / 六亲纳甲 12 / 世应 12·600 右对齐 24 槽）；
 *  动爻标记 ○/⨯ 并入纳甲串尾，[revealed] 控制本爻 draw-on 进度（1 = 完整） */
@Composable
fun LiuYaoRow(
    row: com.smartwash.feature.divination.core.liuyao.LiuYaoLine,
    revealed: Float,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val moving = row.yao.isMoving

    // 动爻标记：爻线展开满后延迟 80ms 再盖上，避免与爻线同帧硬切
    var markVisible by remember { mutableStateOf(false) }
    LaunchedEffect(revealed >= 1f) {
        if (revealed >= 1f) {
            delay(80L)
            markVisible = true
        } else {
            markVisible = false
        }
    }
    // reduced-motion：取消缩放，标记直接出现（规范 §8）
    val markScale by animateFloatAsState(
        targetValue = if (markVisible) 1f else 0f,
        animationSpec = if (LocalReduceMotion.current) snap() else motionSpec(defaultSpring()),
        label = "yaoMark",
    )

    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 六神（画板：无色点，弱化灰）
        Text(
            text = row.liuShen,
            fontSize = 12.sp,
            color = c.textSecondary,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.width(12.dp))
        // 爻象（阳爻六段 / 阴爻两组）
        Box(Modifier.width(100.dp)) {
            YaoBar(yang = row.yao.isYang, moving = moving, progress = revealed)
        }
        Spacer(Modifier.width(6.dp))
        // 六亲 · 纳甲（动爻整串鎏金·玄墨侧；标记延迟 80ms 弹簧缩放盖上）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(row.liuQin).append(row.ganZhi).append(row.zhiWuXing.label)
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (moving && c.isDark) c.goldHi else c.textPrimary,
                maxLines = 1,
            )
            if (moving && markVisible) {
                Text(
                    text = if (row.yao.code == 3) " ○" else " ⨯",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (moving && c.isDark) c.goldHi else c.textPrimary,
                    modifier = Modifier.graphicsLayer {
                        scaleX = markScale
                        scaleY = markScale
                    },
                )
            }
        }
        // 世应徽标（画板：玄墨鎏金 / 宣纸墨，右对齐）
        Text(
            text = row.shiYing ?: "",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.goldText,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
    }
}

/**
 * 六爻装卦表（画板口径）：无卡片容器直接置于背景，轨道左 28 / 右 20，
 * 行间 1px 12% 金发丝分隔（分隔线右缩 8 与卡片轨对齐）；自上爻到初爻渲染。
 * [animateEntry] 时按"自初爻向上 draw-on"逐条展开（交叠并行，间隔 60ms，总时长 ≤400ms）；
 * 任意点按盘面可跳过（进度直接置满）。
 * reduced-motion：整盘直接呈现，不播放逐爻入场。
 */
@Composable
fun LiuYaoPan(
    chart: LiuYaoChart,
    facts: LiuYaoFacts?,
    animateEntry: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val reduceMotion = LocalReduceMotion.current
    // 自初爻(index 0)向上依次 reveal
    val progress = remember { List(6) { Animatable(if (animateEntry) 0f else 1f) } }
    var skipped by remember { mutableStateOf(!animateEntry) }

    // reduced-motion：整盘直接呈现（取消逐爻延迟与位移）
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) progress.forEach { if (it.value < 1f) it.snapTo(1f) }
    }

    // 点按跳过：skipped 变化即把未满进度钉到满值，原 tween 序列自然结束
    LaunchedEffect(skipped) {
        if (skipped) progress.forEach { if (it.value < 1f) it.snapTo(1f) }
    }

    LaunchedEffect(chart, reduceMotion) {
        if (reduceMotion || skipped) return@LaunchedEffect
        coroutineScope {
            // 交叠并行：总时长 ≈ 间隔×(n-1) + 单爻时长（规范 7：总 ≤400ms）
            progress.forEachIndexed { index, anim ->
                launch {
                    delay(index * YAO_STAGGER_MS)
                    anim.animateTo(1f, tween(YAO_DRAW_MS, easing = LinearOutSlowInEasing))
                }
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 20.dp)
            .pressable(onClick = { skipped = true }, debounce = false),
    ) {
        (5 downTo 0).forEach { index ->
            if (index < 5) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .height(1.dp)
                        .background(c.hair)
                )
            }
            val row = chart.rows[index]
            val revealed by progress[index].asState()
            LiuYaoRow(row = row, revealed = revealed)
        }
    }
}

/** 事实小卡（用神 / 规则事实；画板：surface 底 + 12% 金发丝描边、内边距 14/12、标签 12 弱化灰） */
@Composable
fun DivMiniCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = DivColors.current
    DivCard(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, fontSize = 12.sp, letterSpacing = 2.sp, color = c.textSecondary)
            content()
        }
    }
}
