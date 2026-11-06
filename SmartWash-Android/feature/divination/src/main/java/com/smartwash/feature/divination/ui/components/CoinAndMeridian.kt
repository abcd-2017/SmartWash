package com.smartwash.feature.divination.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.feature.divination.R
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.momentumSpring
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** 三币翻转时长 — 420ms（原型口径：起跳 → 落定） */
private const val COIN_SPIN_MS = 420

/**
 * 方孔铜钱（Canvas：鎏金渐变盘 + 中央小方孔 + 孔下单字标示）与摇卦子午线节点。
 * 币面：正面（字）记 3、背面（背）记 2 —— 三币和 6/7/8/9 对应老阴/少阳/少阴/老阳。
 * 2026-11-06 按裁决 2 对齐画板形态：弃拟真乾隆通宝面/回纹，改孔下单字「字/背」（12px 墨字）。
 */

/** 单枚铜钱；face 为当前应显示面，rotationY 由翻转动画驱动（>90° 视觉即背面） */
@Composable
fun DivCoin(
    modifier: Modifier = Modifier,
    face: Boolean,
    rotationY: Float = 0f,
) {
    val c = DivColors.current
    val textMeasurer = rememberTextMeasurer()
    val ziLabel = stringResource(R.string.div_coin_zi)
    val beiLabel = stringResource(R.string.div_coin_bei)
    androidx.compose.foundation.Canvas(
        modifier
            .size(72.dp)
            .graphicsLayer {
                this.rotationY = rotationY
                cameraDistance = 48f * density
            }
    ) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val normalized = ((rotationY % 360f) + 360f) % 360f
        val visibleFace = normalized < 90f || normalized > 270f

        // 币身：对角鎏金渐变（画板双板口径：玄墨 500→700 / 宣纸 300→500）
        drawCircle(
            brush = Brush.linearGradient(
                colors = c.goldGradient,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            radius = r,
            center = center,
        )
        drawCircle(color = Color(0xFF6B5626), radius = r, center = center, style = Stroke(1.5.dp.toPx()))
        // 方孔（透出页面底色，画板口径）
        val holeHalf = r * 0.27f
        drawRect(
            color = c.bgColors[2],
            topLeft = androidx.compose.ui.geometry.Offset(center.x - holeHalf, center.y - holeHalf),
            size = androidx.compose.ui.geometry.Size(holeHalf * 2, holeHalf * 2),
        )
        drawRect(
            color = Color(0xFF6B5626),
            topLeft = androidx.compose.ui.geometry.Offset(center.x - holeHalf, center.y - holeHalf),
            size = androidx.compose.ui.geometry.Size(holeHalf * 2, holeHalf * 2),
            style = Stroke(1.5.dp.toPx()),
        )
        // 孔下单字「字/背」（画板 12px 墨字，位于方孔下）直接标示本枚结果
        val measured = textMeasurer.measure(
            if (visibleFace) ziLabel else beiLabel,
            TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, color = c.onGold),
        )
        drawText(
            measured,
            topLeft = (center + androidx.compose.ui.geometry.Offset(0f, r * 0.66f)) -
                measured.size.center.toOffset(),
        )
    }
}

/**
 * 掷币编排：tossId 变化即三币同步起跳翻转（translationY 弧线 + rotationX/随机 tiltZ），
 * ~420ms 落定（SpringMomentum），落定对齐结果面。动画期间由页面禁用掷爻按钮。
 *
 * reduced-motion：不播放翻转/回弹，直接把三币定格到结果面与落定角，随后照常回调
 * [onSettled]（触觉保留，规范 §8：降级不是没有反馈）。
 */
@Composable
fun CoinTossRow(
    faces: List<Boolean>,           // 三币结果面：true=字(3) false=背(2)
    tossId: Long,                   // 递增触发器；变化即播放一次编排
    onSettled: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val rotY = remember { List(3) { Animatable(0f) } }
    val lift = remember { List(3) { Animatable(0f) } }
    val tiltZ = remember { List(3) { Animatable(0f) } }

    LaunchedEffect(tossId) {
        if (tossId == 0L) return@LaunchedEffect
        if (reduceMotion) {
            // 直接定格结果面（字=0°、背=180°）与落定角，取消翻转/回弹/位移
            rotY.forEachIndexed { i, anim -> anim.snapTo(if (faces[i]) 0f else 180f) }
            lift.forEach { it.snapTo(0f) }
            tiltZ.forEach { it.snapTo(0f) }
            onSettled()
            return@LaunchedEffect
        }
        // 随机相位：避免每次轨迹一模一样（rotationZ ±20°、起跳高度扰动）
        val tilts = List(3) { (kotlin.random.Random.nextInt(41) - 20).toFloat() }
        val heights = List(3) { 52f + kotlin.random.Random.nextInt(17) }
        coroutineScope {
            val spinJobs = rotY.mapIndexed { i, anim ->
                launch {
                    // 目标净角：字=0°，背=180°（至少两整圈，从当前值续转）
                    val current = anim.value
                    val targetAngle = if (faces[i]) 0f else 180f
                    val delta = 720f + ((targetAngle - current) % 360f + 360f) % 360f
                    anim.animateTo(current + delta, tween(COIN_SPIN_MS))
                }
            }
            rotY.indices.forEach { i ->
                launch {
                    tiltZ[i].snapTo(0f)
                    tiltZ[i].animateTo(tilts[i], tween(200))
                    tiltZ[i].animateTo(0f, tween(220))
                }
                launch {
                    lift[i].snapTo(0f)
                    lift[i].animateTo(-heights[i], tween(200))
                    // 落定回弹：惯性弹簧（vocabulary: momentumSpring）
                    lift[i].animateTo(0f, momentumSpring())
                }
            }
            // 落定触觉对齐 rotY 翻转结束点（≈420ms），抬升回弹继续播放不等待
            spinJobs.joinAll()
            onSettled()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rotY.forEachIndexed { i, anim ->
            val rot by anim.asState()
            val liftV by lift[i].asState()
            val tilt by tiltZ[i].asState()
            Box(
                Modifier
                    .padding(horizontal = 6.dp)  // 画板币间距 12dp
                    .graphicsLayer {
                        translationY = liftV
                        rotationZ = tilt
                    }
            ) {
                DivCoin(face = faces[i], rotationY = rot)
            }
        }
    }
}

/** 子午线单节点：爻位名 + 中列（待掷金点 / 爻条）+ 爻名（画板口径：行高 50、列 40/120/右弹性、动爻金强调） */
@Composable
fun MeridianNode(
    positionLabel: String,
    yangLine: Boolean?,
    nameLabel: String?,
    isMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    // 动爻金强调：玄墨亮金（goldHi）、宣纸反转为墨（goldText）——画板双板口径
    val movingColor = if (c.isDark) c.goldHi else c.goldText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = positionLabel,
            style = divSerif(12.sp, FontWeight.Normal, 0.sp, 16.sp),
            color = c.textSecondary,
            textAlign = TextAlign.Right,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(32.dp))
        Box(
            modifier = Modifier.width(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (yangLine != null) {
                YaoSegments(yang = yangLine, isMoving = isMoving)
            } else {
                // 待掷点：8dp 25% 金
                Box(
                    Modifier
                        .size(8.dp)
                        .background(c.gold.copy(alpha = 0.25f), CircleShape)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (yangLine != null && nameLabel != null) {
            Text(
                text = nameLabel,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (isMoving) movingColor else c.textSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 爻条 — 画板分段色块（2026-11-05 摇卦画板）：总宽 92、高 16、圆角 2，居中于子午线；
 * 阳爻六段连排，阴爻两组三段、中缝 8（画板 ▅▅ 口径与卦盘 [YaoBar] 同语言）。
 * 填色：yao 渐变（玄墨宣纸白爻 / 宣纸墨爻）；动爻玄墨侧整段鎏金（goldHi）。
 */
@Composable
private fun YaoSegments(yang: Boolean, isMoving: Boolean, modifier: Modifier = Modifier) {
    val c = DivColors.current
    val brush = if (isMoving && c.isDark) {
        Brush.horizontalGradient(listOf(c.goldHi, c.goldHi))
    } else {
        Brush.horizontalGradient(listOf(c.yaoTop, c.yaoBottom))
    }
    Row(
        modifier
            .width(92.dp)
            .height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (yang) {
            repeat(6) {
                Box(
                    Modifier
                        .weight(3f)
                        .height(16.dp)
                        .background(brush, RoundedCornerShape(2.dp))
                )
            }
        } else {
            // 两组三段：中缝权重 2（≈8dp），段距 2 与卦盘爻线一致
            repeat(3) {
                Box(
                    Modifier
                        .weight(3f)
                        .height(16.dp)
                        .background(brush, RoundedCornerShape(2.dp))
                )
            }
            Spacer(Modifier.weight(2f))
            repeat(3) {
                Box(
                    Modifier
                        .weight(3f)
                        .height(16.dp)
                        .background(brush, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
