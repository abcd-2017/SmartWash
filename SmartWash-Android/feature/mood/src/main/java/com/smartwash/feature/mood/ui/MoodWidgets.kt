package com.smartwash.feature.mood.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor
import com.smartwash.feature.mood.R

/**
 * 情绪气象站模块内共享小件：心情色块、词 chip、月切换条、分布条形
 * （今日/月历/趋势三页共用）。均组合共享组件（pressable + 设计 Token），
 * 不引入新交互组件（规范 §7）。
 */

/** 五个心情色块行（等宽横向铺满）——今日记录页的视觉锚点，一次点按即存 */
@Composable
fun MoodLevelRow(
    selectedLevel: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
    ) {
        MoodLevel.entries.forEach { level ->
            MoodLevelBlock(
                label = stringResource(level.labelRes),
                color = moodLevelColor(level.value),
                selected = selectedLevel == level.value,
                onClick = { onSelect(level.value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 单个心情色块：全彩填充 + 选中墨色描边 + 底部心情名（选中加粗变墨） */
@Composable
private fun MoodLevelBlock(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 描边色 0ms 语义硬切 → 弹簧过渡；reduced motion 自动降级 180ms（规范 7.8）
    val borderColor by animateColorAsState(
        targetValue = if (selected) AppColors.colorScheme.textPrimary else Color.Transparent,
        animationSpec = motionSpec(defaultSpring()),
        label = "moodLevelBorder",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressable(onClick = onClick, debounce = false)
                .background(color, RoundedCornerShape(AppDimens.radiusLg))
                .border(2.dp, borderColor, RoundedCornerShape(AppDimens.radiusLg)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AppColors.colorScheme.textPrimary else AppColors.colorScheme.textSecondary,
            modifier = Modifier.padding(top = AppDimens.spaceXs),
        )
    }
}

/**
 * 词 chip（"累/顺/静/燥/燃"等，可选一个，再点取消）：选中 = 品牌底白字，未选中 = 白底描边。
 * 模式对齐 toolbox 的 ToolboxSelectableChip（不跨域引用 impl，模块内最小复制）。
 */
@Composable
fun MoodWordChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = selectedColor(
        if (selected) AppColors.colorScheme.primary else MaterialTheme.colorScheme.surface
    )
    val contentColor = selectedColor(
        // onPrimary 令牌（浅白/深绿）：深色模式下品牌绿提亮，白字对比度不足
        if (selected) AppColors.colorScheme.onPrimary else AppColors.colorScheme.textSecondary
    )
    val borderColor = selectedColor(
        if (selected) Color.Transparent else AppColors.colorScheme.outline
    )
    // 视觉高保持 labelMedium + 6dp 纵向内边距（约 28dp），外层 Box 撑到 44dp 满足触控热区，视觉不突变
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .pressable(onClick = onClick, debounce = false),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.radiusFull),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                // 字重统一 SemiBold：字重随选中切换会让文字宽度跳变，兄弟 chip 连续位移观感更差
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = 6.dp),
            )
        }
    }
}

/**
 * 月切换条：‹ · 月份文案 · ›。
 * 用文本箭头字符（不新增自绘/Material 图标资源，规范 §9），48dp 触控热区 + semantics 描述。
 */
@Composable
fun MoodMonthSwitcher(
    monthText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoodMonthArrow(
            text = "‹",
            contentDescription = stringResource(R.string.mood_prev_month),
            onClick = onPrev,
        )
        Text(
            text = monthText,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.colorScheme.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        MoodMonthArrow(
            text = "›",
            contentDescription = stringResource(R.string.mood_next_month),
            onClick = onNext,
        )
    }
}

/** 切月箭头：文本字符 + 48dp 热区（导航类豁免入图标容器，规范 §3.2） */
@Composable
private fun MoodMonthArrow(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .pressable(onClick = onClick, debounce = false),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

/**
 * 小按钮胶囊（小按钮规格：12dp 圆角 / 横向 16dp / 36dp 高区间，规范 §3.3）。
 * 今日页页头入口（灰底墨字）与趋势页空态行动（浅绿底绿字）共用。
 */
@Composable
fun MoodSmallAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppColors.colorScheme.surfaceVariant,
    contentColor: Color = AppColors.colorScheme.textPrimary,
) {
    Surface(
        shape = RoundedCornerShape(AppDimens.radiusMd),
        color = containerColor,
        modifier = modifier
            .height(36.dp)
            .pressable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            // 高度固定 36dp 后 wrapContentHeight 把文字重新居中（Surface 内容默认顶对齐）
            modifier = Modifier
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = AppDimens.spaceMd, vertical = 8.dp),
        )
    }
}

/** 加载失败态：说明发生了什么 + 重试按钮（规范 §3.6，月历/趋势页共用） */
@Composable
fun MoodErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.colorScheme.textSecondary,
        )
        Surface(
            shape = RoundedCornerShape(AppDimens.radiusMd),
            color = AppColors.colorScheme.surfaceVariant,
            modifier = Modifier
                .padding(top = AppDimens.spaceMd)
                .height(36.dp)
                .pressable(onClick = onRetry),
        ) {
            Text(
                text = stringResource(R.string.mood_action_retry),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colorScheme.primary,
                // 同 MoodSmallAction：固定 36dp 后 wrapContentHeight 保持文字垂直居中
                modifier = Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = AppDimens.spaceMd, vertical = 8.dp),
            )
        }
    }
}

/** 分布条形行（趋势页）：色点 + 等级名 + 轨道条形（宽度按占比动画）+ 计数 */
@Composable
fun MoodDistributionBar(
    label: String,
    color: Color,
    count: Long,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    // 条形宽度动画走全局弹簧（规范 7.1），reduced motion 自动降级 180ms
    val barFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = motionSpec(defaultSpring()),
        label = "moodBarFraction",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colorScheme.textSecondary,
            modifier = Modifier
                .padding(start = AppDimens.spaceXs)
                // 等级名列宽无对应间距令牌（44dp 非 8pt 网格档），保留字面量
                .width(44.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(AppDimens.spaceMd)
                .background(AppColors.colorScheme.surfaceVariant, RoundedCornerShape(AppDimens.radiusFull)),
        ) {
            if (count > 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barFraction.coerceIn(0.04f, 1f))
                        .background(color, RoundedCornerShape(AppDimens.radiusFull)),
                )
            }
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                // 数字槽位开 tnum（规范 §2.2：数量必须 tabular numbers）
                fontFeatureSettings = "tnum",
            ),
            color = AppColors.colorScheme.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = AppDimens.spaceSm)
                .width(36.dp),
        )
    }
}
