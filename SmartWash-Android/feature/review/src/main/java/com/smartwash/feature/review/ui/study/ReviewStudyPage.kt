package com.smartwash.feature.review.ui.study

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.performHaptic
import com.smartwash.feature.review.R
import com.smartwash.feature.review.model.ReviewRating
import com.smartwash.feature.review.network.ReviewCardVo
import com.smartwash.feature.review.ui.CardStateChip
import com.smartwash.feature.review.ui.RatingButton
import com.smartwash.feature.review.ui.ReviewTagChip
import com.smartwash.feature.review.ui.StudyProgressBar
import com.smartwash.feature.review.ui.notifyReviewDataChanged

/**
 * 复习页：卡片正面（问题）→ 点击翻面（AnimatedContent 淡入 + 微缩放，reduced motion 降级纯淡入）→
 * 底部四档评分按钮 Again/Hard/Good/Easy（红/橙/绿/蓝，Anki 惯例 + 中文语义文案）→
 * 评分成功本地移除该卡自动下一张；队列清空显示完成态（真实状态触发，无伪造动画）。
 */
@Composable
fun ReviewStudyPage(
    navController: NavHostController,
    viewModel: ReviewStudyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by viewModel.state.collectAsState()
    val reduceMotion = LocalReduceMotion.current

    // 评分失败：说明 + 保留当前卡重试（副作用放 LaunchedEffect，规范硬规则）
    LaunchedEffect(state.answerState) {
        val answerState = state.answerState
        if (answerState is RequestState.Error) {
            Toast.makeText(context, answerState.getMessage(context), Toast.LENGTH_SHORT).show()
            view.performHaptic(HapticEffect.ERROR)
            viewModel.consumeAnswerError()
        }
    }

    // 每次评分成功 → 向队列页置数据变更信号（评分改变到期状态，返回时计数条/列表需重拉）
    LaunchedEffect(state.successCount) {
        if (state.successCount > 0) {
            navController.notifyReviewDataChanged()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.review_study_title),
                onBack = { navController.navigateUp() },
            )
            when (val loadState = state.loadState) {
                // 首刷加载：骨架屏（形状对齐卡面，规范 §3.6）
                RequestState.Loading, RequestState.Idle -> {
                    SkeletonList(modifier = Modifier.fillMaxSize(), rows = 4)
                }
                is RequestState.Error -> {
                    EmptyState(
                        icon = Icons.Rounded.School,
                        message = loadState.getMessage(context),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                text = stringResource(R.string.review_action_retry),
                                onClick = { viewModel.load() },
                            )
                        },
                    )
                }
                RequestState.Success -> {
                    val current = state.currentCard
                    if (current == null) {
                        // 完成态：队列清空（真实状态），返回队列页（其经 CHANGED_KEY 重拉计数）
                        CompletedContent(onBack = { navController.popBackStack() })
                    } else {
                        StudyContent(
                            state = state,
                            currentCard = current,
                            materialTitle = state.materialTitles[current.materialId],
                            reduceMotion = reduceMotion,
                            onFlip = {
                                view.performHaptic(HapticEffect.LIGHT)
                                viewModel.flip()
                            },
                            onRate = { viewModel.rate(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyContent(
    state: ReviewStudyViewModel.StudyState,
    currentCard: ReviewCardVo,
    materialTitle: String?,
    reduceMotion: Boolean,
    onFlip: () -> Unit,
    onRate: (ReviewRating) -> Unit,
) {
    // 动效规格在组合期算好（transitionSpec 非 composable 上下文）：
    // reduced motion → CrossfadeAlt 180ms 纯淡入淡出且不加缩放（规范 §8）
    val fadeSpec: FiniteAnimationSpec<Float> =
        if (reduceMotion) tween(CROSSFADE_ALT_MS) else defaultSpring()
    val scaleSpec: FiniteAnimationSpec<Float> =
        if (reduceMotion) tween(CROSSFADE_ALT_MS) else defaultSpring()
    val expandSpec: FiniteAnimationSpec<IntSize> =
        if (reduceMotion) tween(CROSSFADE_ALT_MS) else enterTween(DURATION_CARD_MS)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(bottom = AppDimens.spaceLg),
    ) {
        // 进度行：第 X / Y 张（tnum）+ 细进度条（quiet，锚点让位给卡面）
        Text(
            text = stringResource(
                R.string.review_study_progress_format,
                state.doneCount + 1,
                state.total,
            ),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = AppColors.colorScheme.textSecondary,
            modifier = Modifier.padding(top = AppDimens.spaceXs),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        StudyProgressBar(
            fraction = if (state.total > 0) state.doneCount.toFloat() / state.total else 0f,
        )

        // 卡面 —— 每屏唯一锚点；内容切换（翻面/下一张）统一走 AnimatedContent 淡入淡出
        AnimatedContent(
            targetState = currentCard.id to state.flipped,
            transitionSpec = {
                val enter = if (reduceMotion) {
                    fadeIn(fadeSpec)
                } else {
                    fadeIn(fadeSpec) + scaleIn(initialScale = 0.97f, animationSpec = scaleSpec)
                }
                val exit = if (reduceMotion) {
                    fadeOut(fadeSpec)
                } else {
                    fadeOut(fadeSpec) + scaleOut(targetScale = 0.97f, animationSpec = scaleSpec)
                }
                enter togetherWith exit
            },
            label = "studyCard",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { (cardId, flipped) ->
            CardFace(
                card = currentCard,
                materialTitle = materialTitle,
                flipped = flipped,
                onFlip = onFlip,
            )
        }

        // 评分按钮排：翻面后展开（reduced motion 降级纯淡入，无位移/缩放）
        AnimatedVisibility(
            visible = state.flipped,
            enter = if (reduceMotion) {
                fadeIn(tween(CROSSFADE_ALT_MS))
            } else {
                fadeIn(enterTween(DURATION_CARD_MS)) + expandVertically(expandSpec)
            },
        ) {
            Column {
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ReviewRating.entries.forEach { rating ->
                        RatingButton(
                            rating = rating,
                            enabled = state.answerState !is RequestState.Loading,
                            onClick = { onRate(rating) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 卡面：正面问题 / 背面答案；整卡 pressable 翻面（scale 0.97，规范按压反馈） */
@Composable
private fun CardFace(
    card: ReviewCardVo,
    materialTitle: String?,
    flipped: Boolean,
    onFlip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = AppDimens.spaceMd),
    ) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onFlip,
        ) {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs)) {
                    ReviewTagChip(
                        text = materialTitle
                            ?: stringResource(R.string.review_material_chip_none),
                        containerColor = AppColors.colorScheme.surfaceVariant,
                        contentColor = AppColors.colorScheme.textSecondary,
                    )
                    CardStateChip(card = card)
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                if (!flipped) {
                    // 正面：问题是本页锚点（title 18/600，不与数据大字竞争）
                    Text(
                        text = card.question,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.colorScheme.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceLg))
                    Text(
                        text = stringResource(R.string.review_study_question_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textTertiary,
                    )
                } else {
                    Text(
                        text = card.question,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    Text(
                        text = stringResource(R.string.review_study_answer_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colorScheme.textGreen,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                    Text(
                        text = card.answer,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textPrimary,
                    )
                }
            }
        }
    }
}

/** 完成态：本轮队列清空，文案引导返回（EmptyState 滚筒圆母题静止态） */
@Composable
private fun CompletedContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = Icons.Rounded.School,
            message = stringResource(R.string.review_study_completed_message),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        AppButton(
            text = stringResource(R.string.review_action_back),
            onClick = onBack,
            modifier = Modifier.padding(horizontal = AppDimens.spaceXl),
        )
    }
}
