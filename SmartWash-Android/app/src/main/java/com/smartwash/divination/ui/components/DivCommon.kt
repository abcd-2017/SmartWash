package com.smartwash.divination.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.ui.theme.DivColors
import com.smartwash.utils.HapticEffect
import com.smartwash.utils.currentView
import com.smartwash.utils.performHaptic
import com.smartwash.utils.pressScale

/**
 * 观象台公共组件：衬线样式 / 印章 / 天象头 / 页头 / 鎏金按钮 / 发丝线卡片 / 领域 chips / 脚注。
 * 视觉规则：容器只用 1px 渐变金线描边；鎏金只作图形强调不作正文色；朱砂只出现在印章与动爻标记。
 */

/** 衬线仪式文本样式（卦名/章节标题/干支——衬线出现本身即"传统"语义） */
fun divSerif(size: TextUnit, weight: FontWeight = FontWeight.SemiBold, spacing: TextUnit = 2.sp): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = weight,
        fontSize = size,
        letterSpacing = spacing,
    )

/** 朱砂印章：圆角方 + 白描边 + 同色溢出投影，承担卦名印/类别印/落款印三职 */
@Composable
fun DivSeal(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    fontSize: TextUnit = 15.sp,
) {
    val c = DivColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(c.sealHi, c.seal)))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = divSerif(fontSize, FontWeight.Bold, 1.sp),
            color = Color(0xFFF6E8DC),
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
                .background(Brush.horizontalGradient(listOf(Color.Transparent, c.goldLine)))
        )
        Text(
            text = text,
            style = divSerif(12.sp, FontWeight.Medium, 3.sp),
            color = c.gold.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(
            Modifier
                .height(1.dp)
                .width(52.dp)
                .background(Brush.horizontalGradient(listOf(c.goldLine, Color.Transparent)))
        )
    }
}

/** 观象台页头：返回 + 可选印章 + 衬线标题 + 尾注 */
@Composable
fun DivPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    seal: String? = null,
    tail: String? = null,
) {
    val c = DivColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = c.textSecondary,
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp)
                    .clickable(onClick = onBack),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (seal != null) {
            DivSeal(seal, size = 30.dp, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = divSerif(17.sp, FontWeight.SemiBold, 3.sp),
            color = c.textPrimary,
        )
        if (tail != null) {
            Spacer(Modifier.weight(1f))
            Text(text = tail, fontSize = 11.sp, color = c.textTertiary)
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
            Text(
                text = tail,
                fontSize = 11.sp,
                color = c.textTertiary,
                modifier = if (onTailClick != null) {
                    Modifier.clickable(onClick = onTailClick)
                } else {
                    Modifier
                },
            )
        }
    }
}

/** 鎏金描边主按钮：金底 8% + 金线描边 + 衬线字 + 按压缩放 0.97 */
@Composable
fun DivGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    fontSize: TextUnit = 15.sp,
) {
    val c = DivColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val view = currentView()
    Box(
        modifier = modifier
            .height(height)
            .pressScale(interactionSource, 0.97f)
            .clip(RoundedCornerShape(height / 2))
            .background(if (enabled) c.goldSoft else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (enabled) c.goldLine else c.hair),
                RoundedCornerShape(height / 2),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
            ) {
                view.performHaptic(HapticEffect.LIGHT)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = divSerif(fontSize, FontWeight.SemiBold, 4.sp),
            color = if (enabled) c.gold else c.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 发丝线卡片：surface 底 + 1px 渐变金线描边（135°：亮→隐→亮） */
@Composable
fun DivCard(
    modifier: Modifier = Modifier,
    background: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = DivColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background ?: c.surface)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(borderColor ?: c.goldLine, c.goldHair, borderColor ?: c.goldLine)),
                ),
                shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        content = content,
    )
}

/** 问事领域 chip：选中金底 + 勾，未选素底 */
@Composable
fun DivCategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DivColors.current
    val view = currentView()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.gold14 else c.surface)
            .border(
                BorderStroke(1.dp, if (selected) c.goldLine else c.hair),
                RoundedCornerShape(16.dp),
            )
            .clickable {
                view.performHaptic(HapticEffect.SELECTION)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = if (selected) "$text ✓" else text,
            fontSize = 12.sp,
            color = if (selected) c.gold else c.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 单选行（起卦方式）：标题 + 副题 + 金色圆形单选点 */
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                view.performHaptic(HapticEffect.SELECTION)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, color = c.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 10.5.sp, color = c.textTertiary)
            }
        }
        Box(
            modifier = Modifier
                .size(19.dp)
                .clip(CircleShape)
                .border(
                    1.6.dp,
                    if (selected) c.gold else c.line,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(c.gold)
                )
            }
        }
    }
}

/** 常驻脚注（免责声明） */
@Composable
fun DivFooterNote(text: String, modifier: Modifier = Modifier) {
    val c = DivColors.current
    Text(
        text = text,
        fontSize = 10.sp,
        color = c.textTertiary,
        textAlign = TextAlign.Center,
        letterSpacing = 1.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}
