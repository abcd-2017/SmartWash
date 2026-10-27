package com.smartwash.feature.divination.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.theme.DivColors
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.selectedColor

/**
 * 观象台公共组件：衬线样式 / 印章 / 天象头 / 页头 / 鎏金按钮 / 发丝线卡片 / 领域 chips / 脚注。
 * 视觉规则：容器只用 1px 渐变金线描边；鎏金只作图形强调不作正文色；朱砂只出现在印章与动爻标记。
 */

/** 衬线仪式文本样式（卦名/章节标题/干支——衬线出现本身即"传统"语义） */
fun divSerif(
    size: TextUnit,
    weight: FontWeight = FontWeight.SemiBold,
    spacing: TextUnit = 2.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = weight,
        fontSize = size,
        letterSpacing = spacing,
        lineHeight = lineHeight,
    )

/** 朱砂印章：圆角方 + 22% 金描边，承担卦名印/类别印/落款印三职；
 *  flat=平涂深朱无描边（卦盘/卦历画板口径，双板一致 sealDeep） */
@Composable
fun DivSeal(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    fontSize: TextUnit = 15.sp,
    cornerRadius: Dp = 6.dp,
    flat: Boolean = false,
) {
    val c = DivColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (flat) SolidColor(c.sealDeep) else Brush.linearGradient(listOf(c.sealHi, c.seal)),
            )
            .then(
                if (flat) {
                    Modifier
                } else {
                    Modifier.border(1.dp, c.line, RoundedCornerShape(cornerRadius))
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = divSerif(fontSize, FontWeight.Bold, 1.sp),
            color = Color(0xFFEFE9DA),  // 印面文字两主题恒为宣纸白（画板双板一致）
        )
    }
}

/** 干支天象头：两侧鎏金渐隐线 —— 干支是本模块的时钟 */
@Composable
fun TianXiangHeader(text: String, modifier: Modifier = Modifier) {
    val c = DivColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .height(1.dp)
                .width(52.dp)
                .background(c.goldLine)
        )
        Text(
            text = text,
            style = divSerif(12.sp, FontWeight.Medium, 0.sp),
            color = c.goldText.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(
            Modifier
                .height(1.dp)
                .width(52.dp)
                .background(c.goldLine)
        )
    }
}

/** 观象台页头：返回（48 触控）+ 居中衬线标题（可选印章）+ 右侧尾注（画板：18/600 居中、尾注金 75%）；
 *  alignStart 为无返回页的左对齐口径（卦历画板：标题 18/600 左对齐 x20，状态栏下 6、下距 10）；
 *  flatSeal=印章平涂口径、titleSpacing=标题字距（卦盘画板 2，问卦/卦历 4）；
 *  sealTrailing=页头右侧固定卦印（解读画板：右缘 20、30 方印 r8 平涂深朱、印文 14/700；与 tail 互斥）；
 *  subtitle=标题下居中副题（追问画板：下距 4、12/16 字距 2 次要色） */
@Composable
fun DivPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    seal: String? = null,
    flatSeal: Boolean = false,
    tail: String? = null,
    alignStart: Boolean = false,
    titleSpacing: TextUnit = 4.sp,
    sealTrailing: String? = null,
    subtitle: String? = null,
) {
    val c = DivColors.current
    // 返回箭头内置连击防抖：本模块 navigate 均无 launchSingleTop，连点会连弹/双推导航栈
    val debouncedBack = rememberDebouncedClick(enabled = onBack != null) { onBack?.invoke() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = if (alignStart) 6.dp else 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(48.dp)
                            .pressable(onClick = debouncedBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = c.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Row(
                    modifier = if (alignStart) {
                        Modifier.align(Alignment.CenterStart).padding(start = 20.dp)
                    } else {
                        Modifier.align(Alignment.Center)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (seal != null) {
                        // 画板（卦盘）：30 方印 radius 8、印文 14/700
                        DivSeal(seal, size = 30.dp, fontSize = 14.sp, cornerRadius = 8.dp, flat = flatSeal)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = title,
                        style = divSerif(18.sp, FontWeight.SemiBold, titleSpacing, lineHeight = 24.sp),
                        color = c.textPrimary,
                    )
                }
                if (tail != null) {
                    Text(
                        text = tail,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        color = c.goldText.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
                    )
                }
                if (sealTrailing != null) {
                    // 解读画板：页头右侧固定卦印，平涂深朱（双板一致 fill #A63A30 无描边）
                    DivSeal(
                        sealTrailing,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp),
                        size = 30.dp,
                        fontSize = 14.sp,
                        cornerRadius = 8.dp,
                        flat = true,
                    )
                }
            }
            if (subtitle != null) {
                // 追问画板：标题下 4 居中副题（12/16、字距 2、次要色）
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    lineHeight = 16.sp,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 章节标题（衬线 + 字距），如「案 卷」「起卦方式」 */
@Composable
fun DivSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    tail: String? = null,
    onTailClick: (() -> Unit)? = null,
) {
    val c = DivColors.current
    val debouncedTailClick = rememberDebouncedClick(enabled = onTailClick != null) { onTailClick?.invoke() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = divSerif(14.sp, FontWeight.SemiBold, 4.sp),
            color = c.textPrimary,
        )
        if (tail != null) {
            Spacer(Modifier.weight(1f))
            // 画板口径：可点击尾注为链接墨色（宣纸侧全墨），纯信息尾注降为次要灰
            Text(
                text = tail,
                fontSize = 12.sp,
                color = if (onTailClick != null) c.textTertiary else c.textSecondary,
                modifier = if (onTailClick != null) {
                    Modifier.pressable(onClick = debouncedTailClick)
                } else {
                    Modifier
                },
            )
        }
    }
}

/** 鎏金渐变主按钮：金渐变胶囊（52 高、无描边）+ 墨字 + 衬线 + 按压缩放 0.97（§1.5 金按钮）；
 *  soft=解读画板口径：14% 金底 + 金/墨字（玄墨金字/宣纸墨字，双板一致），用于次级仪式主行动 */
@Composable
fun DivGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 52.dp,
    fontSize: TextUnit = 14.sp,
    soft: Boolean = false,
) {
    val c = DivColors.current
    val view = currentView()
    // 鎏金按钮多承载入库/导航等有副作用动作：连击防抖 + 按压缩放/触觉由 pressable 一套承载
    // （不再叠加 Material ripple：涟漪形状不受令牌控制，且与容器不贴合）
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(
                when {
                    // 禁用态退回 14% 金底（画板「已保存」口径，无描边）；解读画板主按钮同为 14% 金底
                    !enabled || soft -> SolidColor(c.gold14)
                    else -> Brush.horizontalGradient(c.goldGradient)
                },
            )
            .pressable(
                onClick = {
                    if (enabled) {
                        view.performHaptic(HapticEffect.LIGHT)
                        onClick()
                    }
                },
                // 禁用态不给按压反馈（保持"禁用即可点但无反馈"的既有行为）
                scaleFactor = if (enabled) 0.97f else 1f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = divSerif(fontSize, FontWeight.SemiBold, 3.sp),
            color = when {
                !enabled -> c.goldText.copy(alpha = 0.7f)
                // soft 口径（解读画板）：玄墨金字 / 宣纸墨字，即主题感知 goldText
                soft -> c.goldText
                else -> c.onGold
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** 发丝线卡片：surface 底 + 1px 12% 金发丝描边（画板双板统一口径） */
@Composable
fun DivCard(
    modifier: Modifier = Modifier,
    background: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = DivColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background ?: c.surface)
            .border(
                BorderStroke(1.dp, borderColor ?: c.hair),
                shape,
            )
            .then(
                if (onClick != null) {
                    // 按压反馈统一走 pressable（indication=null + scale 0.97 + reduced-motion 降级）
                    Modifier.pressable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        content = content,
    )
}

/** 问事领域 chip：48 触控胶囊；选中金底 + 金环（玄墨实金线/宣纸 30% 线，画板双板口径）+ 金强调字；
 *  水平内边距默认 12（问卦领域 chip 宽 48），卦历筛选 chip 画板宽 52 传 14；底色/描边/字色与勾均过渡，不硬切 */
@Composable
fun DivCategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    val c = DivColors.current
    val view = currentView()
    val selectedBorder = if (c.isDark) c.gold else c.goldLine
    val shape = RoundedCornerShape(999.dp)
    // 颜色过渡（200ms 档，reduced motion 经 motionSpec 自动降 180ms）
    val background = selectedColor(if (selected) c.gold14 else c.surface)
    val border = selectedColor(if (selected) selectedBorder else c.goldLine)
    val textColor = selectedColor(if (selected) c.goldText else c.textSecondary)
    // 勾：弹簧缩放盖上/退下（无位移，reduced motion 降级为 180ms）
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motionSpec(defaultSpring()),
        label = "chipCheck",
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, border), shape)
            .pressable(
                onClick = {
                    view.performHaptic(HapticEffect.SELECTION)
                    onClick()
                },
                debounce = false,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = textColor,
                // 字重无插值能力，保留瞬时切换作为"选中"的状态可读性
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (checkScale > 0f) {
                Text(
                    text = "✓",
                    fontSize = 11.sp,
                    color = textColor,
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        },
                )
            }
        }
    }
}

/** 单选行（起卦方式）：标题 + 副题 + 金色圆形单选点（画板：内边距 20/12、环 20 点 8、未选行标题次级色；选中金点弹簧缩放进出） */
@Composable
fun DivRadioRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val view = currentView()
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motionSpec(defaultSpring()),
        label = "radioDot",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(
                onClick = {
                    view.performHaptic(HapticEffect.SELECTION)
                    onClick()
                },
                debounce = false,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                color = if (selected) c.textPrimary else c.textSecondary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = c.textSecondary,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    if (selected) c.gold.copy(alpha = 0.9f) else c.goldLine,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(c.gold)
            )
        }
    }
}

/** 常驻脚注（免责声明） */
@Composable
fun DivFooterNote(text: String, modifier: Modifier = Modifier) {
    val c = DivColors.current
    Text(
        text = text,
        fontSize = 12.sp,
        color = c.textTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    )
}
