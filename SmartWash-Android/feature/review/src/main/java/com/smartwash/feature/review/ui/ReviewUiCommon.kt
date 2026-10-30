package com.smartwash.feature.review.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.smartwash.feature.review.network.ReviewCardVo

/**
 * 复习模块共享小件：静态标签 chip（资料/状态/到期类型角标）、卡片状态角标、
 * 复习页细进度条与四档评分按钮（Anki 红/橙/绿/蓝惯例，色值全部取设计令牌）。
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
            modifier = Modifier.padding(horizontal = AppDimens.spaceXs, vertical = 2.dp),
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
            AppColors.colorScheme.warning
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

/** 评分按钮容器色（Anki 惯例红/橙/绿/蓝，全部取语义令牌；主题切换自动跟随） */
@Composable
internal fun ratingColor(rating: ReviewRating): Color = when (rating) {
    ReviewRating.AGAIN -> AppColors.colorScheme.danger
    ReviewRating.HARD -> AppColors.colorScheme.ongoing
    ReviewRating.GOOD -> AppColors.colorScheme.primary
    ReviewRating.EASY -> AppColors.colorScheme.water
}

/**
 * 四档评分按钮 — 规格（§3.3 主按钮口径）：高 52dp / 14dp 圆角 / 实色语义底 / 白字。
 * 两行文案：Anki 英文惯例名（14/600）+ 中文语义（captionStrong 12/500）。
 * 按压反馈 scale 0.97（pressable 内置 indication=null）；提交中整排禁用降透明度。
 */
@Composable
internal fun RatingButton(
    rating: ReviewRating,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = currentView()
    val containerColor = ratingColor(rating).copy(alpha = if (enabled) 1f else 0.5f)
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
                    Modifier
                }
            )
            .height(52.dp)
            .background(containerColor, RoundedCornerShape(AppDimens.buttonRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(rating.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(rating.semanticRes),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

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
            .height(4.dp)
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(AppColors.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(AppDimens.radiusFull))
                .background(AppColors.colorScheme.primary),
        )
    }
}
