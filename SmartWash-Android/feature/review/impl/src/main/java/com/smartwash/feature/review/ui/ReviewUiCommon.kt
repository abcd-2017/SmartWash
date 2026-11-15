package com.smartwash.feature.review.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.review.R
import com.smartwash.feature.review.model.ReviewCardState
import com.smartwash.feature.review.model.ReviewRating
import com.smartwash.feature.review.api.network.ReviewCardVo

/**
 * 复习模块共享小件：静态标签 chip（资料/状态/到期类型角标）、卡片状态角标、
 * 复习页细进度条与四档评分按钮（Anki 红/橙/绿/蓝惯例，语义浅底 + ink 深字成对令牌）。
 * 均组合 pressable/令牌实现，不引入 M3 交互组件（规范 §7）。
 */

/** 静态标签 chip —— 胶囊（radiusFull）+ captionStrong 12/500，用于条目上的资料/状态标注 */
@Composable
internal fun ReviewTagChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXxs),
        )
    }
}

/** 新卡角标（primaryContainer/onPrimaryContainer，对齐 toolbox 公开 chip 用色） */
@Composable
internal fun NewCardChip(modifier: Modifier = Modifier) {
    ReviewTagChip(
        text = stringResource(R.string.review_card_new),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    )
}

/**
 * 到期类型角标：Learning/Relearning = 分钟级（橙），Review = 天级（蓝）；
 * 新卡（lastReviewAt 为空）直接显示「新卡」。
 */
@Composable
internal fun DueTypeChip(card: ReviewCardVo, modifier: Modifier = Modifier) {
    if (card.isNewCard) {
        NewCardChip(modifier)
        return
    }
    val isDaily = card.state == ReviewCardState.REVIEW.code
    ReviewTagChip(
        text = stringResource(
            if (isDaily) R.string.review_due_badge_daily else R.string.review_due_badge_minute
        ),
        containerColor = if (isDaily) {
            AppColors.colorScheme.iconContainerBlue
        } else {
            AppColors.colorScheme.warningContainer
        },
        contentColor = if (isDaily) {
            AppColors.colorScheme.iconForegroundBlue
        } else {
            // 分钟级角标小字：warning 橙 on warningContainer 浅色仅 2.51:1 破线（审查矩阵 #3），
            // 改成对 ink 档 onWarningContainer（浅 #92400E ≈5.9:1 / 深 #ECB36A ≈7.6:1，双主题达标）
            AppColors.colorScheme.onWarningContainer
        },
        modifier = modifier,
    )
}

/** 卡片状态角标（学习中/复习中/重学中，复习页卡面顶部展示） */
@Composable
internal fun CardStateChip(card: ReviewCardVo, modifier: Modifier = Modifier) {
    val labelRes = ReviewCardState.fromCode(card.state)?.labelRes ?: R.string.review_state_learning
    ReviewTagChip(
        text = stringResource(labelRes),
        containerColor = AppColors.colorScheme.surfaceVariant,
        contentColor = AppColors.colorScheme.textSecondary,
        modifier = modifier,
    )
}

/**
 * 评分按钮配色 — 容器取各语义色「浅底容器」档、文字取对应 ink 深字档
 * （规范 §2.1「语义色浅底 + ink 深字」成对令牌）。原语义实底 + onPrimary 白字在浅色模式
 * 低于 4.5:1 红线（HARD 2.76:1 / EASY 2.92:1，审查矩阵 #4；GOOD 3.5:1 与主按钮同口径、
 * AGAIN 达标）；改浅底 + ink 后浅色最低 5.1:1、深色最低 6.5:1，两主题同步核算均达标。
 * 主题切换自动跟随（深色取各令牌的 Dark 档）。
 */

/** 评分按钮禁用态透明度（规范 §3.1：禁用 = 内容 38% 透明度，M3 口径） */
private const val RATING_DISABLED_ALPHA = 0.38f

/** 评分按钮容器色 — 各语义色浅底容器档（粉=Again / 橙=Hard / 绿=Good / 蓝=Easy） */
@Composable
internal fun ratingContainerColor(rating: ReviewRating): Color = when (rating) {
    ReviewRating.AGAIN -> AppColors.colorScheme.iconContainerPink
    ReviewRating.HARD -> AppColors.colorScheme.warningContainer
    ReviewRating.GOOD -> AppColors.colorScheme.iconContainerGreen
    ReviewRating.EASY -> AppColors.colorScheme.waterSoft
}

/** 评分按钮文字色 — 与容器成对的 ink 深字档（浅色最低 5.1:1 / 深色最低 6.5:1，均达 AA） */
@Composable
internal fun ratingContentColor(rating: ReviewRating): Color = when (rating) {
    ReviewRating.AGAIN -> AppColors.colorScheme.dangerInk
    ReviewRating.HARD -> AppColors.colorScheme.onWarningContainer
    ReviewRating.GOOD -> AppColors.colorScheme.textGreenOnTint
    ReviewRating.EASY -> AppColors.colorScheme.textPrimary
}

/**
 * 四档评分按钮（Anki 红/橙/绿/蓝惯例，配色见 [ratingContainerColor]/[ratingContentColor]）：
 * 48dp 胶囊（radiusFull），两行文案 = Anki 英文惯例名（15sp/600）+ 中文语义（caption 12sp）。
 * 高度 heightIn(min) 随内容弹性增高：系统字体放大 130%+ 时不裁切两行文案（规范 §8，矩阵 #5）。
 * 按压反馈 scale 0.97（pressable 内置 indication=null）；提交中整排禁用 = 内容 38% 透明度
 * （规范 §3.1，矩阵 #6），且保留 clickable(enabled=false) 向语义树暴露 Disabled 状态。
 */
@Composable
internal fun RatingButton(
    rating: ReviewRating,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = currentView()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor =
        ratingContainerColor(rating).copy(alpha = if (enabled) 1f else RATING_DISABLED_ALPHA)
    val contentColor =
        ratingContentColor(rating).copy(alpha = if (enabled) 1f else RATING_DISABLED_ALPHA)
    Box(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.pressable(onClick = {
                        // 评分是明确因果的确认操作：中触（规范 7.7 时刻表）
                        view.performHaptic(HapticEffect.MEDIUM)
                        onClick()
                    })
                } else {
                    // 禁用态保留 clickable（enabled=false）：语义树暴露 Disabled（规范 §3.1/§8，矩阵 #6）
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = false,
                        onClick = {},
                    )
                }
            )
            .heightIn(min = 48.dp)
            .background(containerColor, RoundedCornerShape(AppDimens.radiusFull)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(rating.labelRes),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(rating.semanticRes),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 细进度条高度（AppDimens 无对应档位，沿用既有细进度条规格沉淀为组件局部常量 — 审查矩阵 #11） */
private val ProgressBarHeight = 4.dp

/**
 * 细进度条（复习页进度意象）：surfaceVariant 槽 + primary 填充。
 * 弹簧过渡；reduced motion 时直接落位到目标值（规范 §8：取消动画但保留状态可读）。
 */
@Composable
internal fun StudyProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = motionSpec(defaultSpring()),
        label = "studyProgress",
    )
    val fillFraction = (if (reduceMotion) fraction else animatedFraction).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ProgressBarHeight)
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(AppColors.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .height(ProgressBarHeight)
                .clip(RoundedCornerShape(AppDimens.radiusFull))
                .background(AppColors.colorScheme.primary),
        )
    }
}
