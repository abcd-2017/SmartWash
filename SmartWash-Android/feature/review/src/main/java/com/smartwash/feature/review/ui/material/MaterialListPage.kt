package com.smartwash.feature.review.ui.material

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.components.dialogEnterMotion
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.review.R
import com.smartwash.feature.review.logic.ReviewFormat
import com.smartwash.feature.review.network.MaterialVo
import com.smartwash.feature.review.network.ReviewCardVo
import com.smartwash.feature.review.ui.DueTypeChip
import com.smartwash.feature.review.ui.notifyReviewDataChanged
import java.time.LocalDateTime

/**
 * 资料页：资料列表 + 粘贴文本建资料（内联表单）+ 每份资料的问答卡列表。
 * 卡片 v1 仅支持新增（契约未提供改/删端点）；任一写入成功后向队列页发数据变更信号。
 */
@Composable
fun MaterialListPage(
    navController: NavHostController,
    viewModel: MaterialListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = currentView()
    val state by viewModel.state.collectAsState()

    // 建资料成功：提示 + 队列页刷新信号（副作用一律 LaunchedEffect，禁止组合期副作用）
    LaunchedEffect(state.createState) {
        if (state.createState is RequestState.Success) {
            Toast.makeText(
                context,
                context.getString(R.string.review_toast_material_created),
                Toast.LENGTH_SHORT,
            ).show()
            view.performHaptic(HapticEffect.SUCCESS)
            navController.notifyReviewDataChanged()
            viewModel.consumeCreateState()
        }
    }
    // 建卡成功：提示 + 队列页刷新信号（错误留在弹窗内联展示）
    LaunchedEffect(state.addCardState) {
        if (state.addCardState is RequestState.Success) {
            Toast.makeText(
                context,
                context.getString(R.string.review_toast_card_created),
                Toast.LENGTH_SHORT,
            ).show()
            view.performHaptic(HapticEffect.SUCCESS)
            navController.notifyReviewDataChanged()
            viewModel.consumeAddCardSuccess()
        }
    }
    // 加载更多失败不再立即消费：错误态由 LoadMoreRow 内联展示，行本身即重试入口，
    // 消费时机后移到用户点击重试（loadMore 覆盖状态）或离开页面（审查矩阵 #5）

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.review_material_title),
                onBack = { navController.navigateUp() },
                actions = {
                    // 文字按钮 — §3.3 规格 13sp/600 主色，热区 ≥48dp（审查矩阵 #8）
                    Box(
                        modifier = Modifier
                            .pressable(onClick = { viewModel.toggleCreateForm() })
                            .heightIn(min = 48.dp)
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm),
                    ) {
                        Text(
                            text = stringResource(R.string.review_material_action_new),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.textGreen,
                        )
                    }
                },
            )
            when (val loadState = state.loadState) {
                RequestState.Loading, RequestState.Idle -> {
                    SkeletonList(modifier = Modifier.fillMaxSize(), rows = 5)
                }
                is RequestState.Error -> {
                    EmptyState(
                        icon = Icons.Rounded.School,
                        message = loadState.getMessage(context),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.review_action_retry),
                                onClick = { viewModel.load() },
                            )
                        },
                    )
                }
                RequestState.Success -> {
                    MaterialListContent(
                        state = state,
                        onLoadMore = viewModel::loadMore,
                        onToggleCreateForm = viewModel::toggleCreateForm,
                        onTitleChange = viewModel::updateFormTitle,
                        onRawTextChange = viewModel::updateFormRawText,
                        onSubmitCreate = viewModel::submitCreateMaterial,
                        onToggleExpand = viewModel::toggleExpand,
                        onLoadMoreCards = viewModel::loadMoreCards,
                        onRetryCards = viewModel::retryCards,
                        onShowAddCard = viewModel::showAddCardDialog,
                    )
                }
            }
        }
    }

    if (state.cardDialogMaterialId != null) {
        ReviewCardEditDialog(
            materialTitle = state.expandedMaterial?.title.orEmpty(),
            question = state.dialogQuestion,
            answer = state.dialogAnswer,
            submitState = state.addCardState,
            onQuestionChange = viewModel::updateDialogQuestion,
            onAnswerChange = viewModel::updateDialogAnswer,
            onConfirm = viewModel::submitAddCard,
            onDismiss = viewModel::dismissAddCardDialog,
        )
    }
}

@Composable
private fun MaterialListContent(
    state: MaterialListViewModel.UiState,
    onLoadMore: () -> Unit,
    onToggleCreateForm: () -> Unit,
    onTitleChange: (String) -> Unit,
    onRawTextChange: (String) -> Unit,
    onSubmitCreate: () -> Unit,
    onToggleExpand: (Long) -> Unit,
    onLoadMoreCards: () -> Unit,
    onRetryCards: () -> Unit,
    onShowAddCard: (Long) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    // 展开动效规格：reduced motion → 仅 CrossfadeAlt 180ms 纯淡入淡出，expand/shrink 位移
    // 整体归零（规范 §8，审查矩阵 #7）；正常路径淡入淡出消费 Float 规格、expand/shrink 消费
    // IntSize 规格，两组分开——展开/收缩只在非 reduce 分支拼接。
    val fadeEnter = if (reduceMotion) tween<Float>(CROSSFADE_ALT_MS) else enterTween(DURATION_CARD_MS)
    val fadeExit = if (reduceMotion) tween<Float>(CROSSFADE_ALT_MS) else exitTween(DURATION_CARD_MS)
    val expandEnter = enterTween<IntSize>(DURATION_CARD_MS)
    val expandExit = exitTween<IntSize>(DURATION_CARD_MS)
    val now = remember { LocalDateTime.now() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
        contentPadding = PaddingValues(
            horizontal = AppDimens.pagePadding,
            vertical = AppDimens.spaceXs,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ---------- 建资料内联表单（「新建」文字按钮切换） ----------
        item(key = "material_create_form") {
            AnimatedVisibility(
                visible = state.createFormVisible,
                enter = if (reduceMotion) {
                    fadeIn(fadeEnter)
                } else {
                    fadeIn(fadeEnter) + expandVertically(expandEnter)
                },
                exit = if (reduceMotion) {
                    fadeOut(fadeExit)
                } else {
                    fadeOut(fadeExit) + shrinkVertically(expandExit)
                },
            ) {
                CreateMaterialForm(
                    title = state.formTitle,
                    rawText = state.formRawText,
                    createState = state.createState,
                    onTitleChange = onTitleChange,
                    onRawTextChange = onRawTextChange,
                    onSubmit = onSubmitCreate,
                )
            }
        }

        if (state.materials.isEmpty()) {
            // 空态：滚筒圆母题 + 引导 + 行动按钮（规范 §3.6）
            item(key = "material_empty") {
                EmptyState(
                    icon = Icons.Rounded.School,
                    message = stringResource(R.string.review_material_empty),
                    modifier = Modifier.fillMaxWidth(),
                    action = {
                        AppButton(
                            small = true,
                            text = stringResource(R.string.review_material_action_new),
                            onClick = onToggleCreateForm,
                        )
                    },
                )
            }
        } else {
            items(state.materials, key = { it.id }) { material ->
                MaterialCard(
                    material = material,
                    expanded = state.expandedMaterialId == material.id,
                    cards = state.cards,
                    cardsState = state.cardsState,
                    cardsTotal = state.cardsTotal,
                    cardsHasMore = state.cardsHasMore,
                    fadeEnter = fadeEnter,
                    expandEnter = expandEnter,
                    fadeExit = fadeExit,
                    expandExit = expandExit,
                    now = now,
                    onToggleExpand = { onToggleExpand(material.id) },
                    onLoadMoreCards = onLoadMoreCards,
                    onRetryCards = onRetryCards,
                    onShowAddCard = { onShowAddCard(material.id) },
                )
            }
            if (state.materialsHasMore) {
                item(key = "material_load_more") {
                    LoadMoreRow(
                        loadMoreState = state.loadMoreState,
                        onClick = onLoadMore,
                    )
                }
            }
        }
    }
}

/** 建资料内联表单：标题（必填 ≤100）+ 原文粘贴（可选 multiline）；错误仅提交后内联展示 */
@Composable
private fun CreateMaterialForm(
    title: String,
    rawText: String,
    createState: RequestState,
    onTitleChange: (String) -> Unit,
    onRawTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    GroupCard {
        FormLabel(stringResource(R.string.review_material_form_title_label))
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.review_material_form_title_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                // 固定高度改弹性下限：130% 字体缩放时文字不被裁切（审查矩阵 #15）
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(AppDimens.inputRadius),
            colors = tokenTextFieldColors(),
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        FormLabel(stringResource(R.string.review_material_form_raw_label))
        OutlinedTextField(
            value = rawText,
            onValueChange = onRawTextChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.review_material_form_raw_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.inputRadius),
            colors = tokenTextFieldColors(),
        )

        // 提交错误（仅提交后展示，规范 §3.7）
        val errorMessage = (createState as? RequestState.Error)?.getMessage(context)
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.error,
                modifier = Modifier.padding(top = AppDimens.spaceSm),
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        AppButton(
            text = stringResource(R.string.review_material_action_create),
            onClick = onSubmit,
            loading = createState is RequestState.Loading,
        )
    }
}

/**
 * 资料卡：标题 + 创建日期 + 原文预览（两行）；头部整行点击展开/收起卡片列表
 * （发丝线只出现在卡内行间，规范 §1.3/§3.4）。
 */
@Composable
private fun MaterialCard(
    material: MaterialVo,
    expanded: Boolean,
    cards: List<ReviewCardVo>,
    cardsState: RequestState,
    cardsTotal: Long,
    cardsHasMore: Boolean,
    fadeEnter: FiniteAnimationSpec<Float>,
    expandEnter: FiniteAnimationSpec<IntSize>,
    fadeExit: FiniteAnimationSpec<Float>,
    expandExit: FiniteAnimationSpec<IntSize>,
    now: LocalDateTime,
    onToggleExpand: () -> Unit,
    onLoadMoreCards: () -> Unit,
    onRetryCards: () -> Unit,
    onShowAddCard: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    AppCard {
        // 卡内容包卡内边距 20dp：AppCard 无默认内边距，遗漏会让文本贴 16dp 圆角切字
        // （规范 §3.1 卡内边距 / 审查矩阵 #1，与队列页 QueueCard 画法一致）
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pressable(onClick = onToggleExpand)
                    .heightIn(min = 48.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = material.title,
                        style = AppTextStyles.CardTitle,
                        color = AppColors.colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    Text(
                        text = ReviewFormat.createdDateOf(material.createdAt, "—"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        // 创建日期是可读信息（非装饰角标）：textTertiary 双主题均破 4.5:1（审查矩阵 #2）
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
                Text(
                    text = stringResource(
                        if (expanded) R.string.review_material_collapse else R.string.review_material_expand
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.colorScheme.textGreen,
                )
            }
            if (!material.rawText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = material.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) {
                    fadeIn(fadeEnter)
                } else {
                    fadeIn(fadeEnter) + expandVertically(expandEnter)
                },
                exit = if (reduceMotion) {
                    fadeOut(fadeExit)
                } else {
                    fadeOut(fadeExit) + shrinkVertically(expandExit)
                },
            ) {
                Column {
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    // 卡片列表头：计数 + 添加卡片（文字按钮 13/600 主色）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.review_material_cards_header_format, cardsTotal),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .pressable(onClick = onShowAddCard)
                                .heightIn(min = 48.dp)
                                .padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXxs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.review_material_add_card),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.colorScheme.textGreen,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                    ExpandedCardsContent(
                        cards = cards,
                        cardsState = cardsState,
                        cardsHasMore = cardsHasMore,
                        now = now,
                        onLoadMoreCards = onLoadMoreCards,
                        onRetryCards = onRetryCards,
                    )
                }
            }
        }
    }
}

/** 展开区卡片列表：加载骨架 / 错误重试 / 空提示 / 问答行（发丝线分隔）+ 加载更多 */
@Composable
private fun ExpandedCardsContent(
    cards: List<ReviewCardVo>,
    cardsState: RequestState,
    cardsHasMore: Boolean,
    now: LocalDateTime,
    onLoadMoreCards: () -> Unit,
    onRetryCards: () -> Unit,
) {
    val context = LocalContext.current
    when (cardsState) {
        RequestState.Loading -> {
            Column(modifier = Modifier.padding(vertical = AppDimens.spaceXs)) {
                repeat(2) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                }
            }
        }
        is RequestState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cardsState.getMessage(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.dangerInk,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .pressable(onClick = onRetryCards)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXxs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.review_action_retry),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.colorScheme.textGreen,
                    )
                }
            }
        }
        else -> {
            if (cards.isEmpty()) {
                Text(
                    text = stringResource(R.string.review_material_cards_empty),
                    style = MaterialTheme.typography.bodySmall,
                    // 引导性正文（非装饰角标）：textTertiary 双主题均破 4.5:1（审查矩阵 #3）
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.padding(vertical = AppDimens.spaceSm),
                )
            } else {
                Column {
                    cards.forEachIndexed { index, card ->
                        CardRow(card = card, now = now)
                        if (index < cards.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                        }
                    }
                }
                if (cardsHasMore) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .pressable(onClick = onLoadMoreCards),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.review_material_load_more),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.textGreen,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 问答行：问题（主文案）+ 答案预览（次文案，单行省略）+ 到期类型角标（v1 契约无改/删端点，行不可点） */
@Composable
private fun CardRow(card: ReviewCardVo, now: LocalDateTime) {
    Column(modifier = Modifier.padding(vertical = AppDimens.spaceSm)) {
        Text(
            text = card.question,
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        Text(
            text = card.answer,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        DueTypeChip(card = card)
    }
}

/**
 * 加载更多行：加载中骨架块 / 错误文案内联展示（行本身即重试入口，错误不再静默——审查矩阵 #5）/
 * 静态文字按钮。错误文案沿用展开区错误行的 dangerInk 口径，点击整行走 [onClick] 重试。
 */
@Composable
private fun LoadMoreRow(loadMoreState: RequestState, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (loadMoreState is RequestState.Loading) Modifier else Modifier.pressable(onClick = onClick))
            .padding(vertical = AppDimens.spaceSm),
        contentAlignment = Alignment.Center,
    ) {
        when (loadMoreState) {
            RequestState.Loading -> {
                SkeletonBlock(
                    modifier = Modifier
                        .widthIn(min = 120.dp)
                        .height(14.dp)
                )
            }
            is RequestState.Error -> {
                Text(
                    text = loadMoreState.getMessage(context),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.colorScheme.dangerInk,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.review_material_load_more),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.colorScheme.textGreen,
                )
            }
        }
    }
}

// ---------- 建卡弹窗 ----------

/**
 * 添加问答卡弹窗：问题 + 答案（multiline，§3.7 输入正文 16sp）；确认提交（loading 态按钮
 * 承担禁用），错误仅提交后内联展示。入场动效走 common:ui 的 dialogEnterMotion
 * （reduced motion 自动降级为 180ms 纯淡入）。
 */
@Composable
private fun ReviewCardEditDialog(
    materialTitle: String,
    question: String,
    answer: String,
    submitState: RequestState,
    onQuestionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.dialogEnterMotion(),
            shape = RoundedCornerShape(AppDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppElevation.floatLayer,
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = AppDimens.spaceXl, vertical = AppDimens.spaceLg),
                ) {
                    Text(
                        text = stringResource(R.string.review_card_dialog_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (materialTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        Text(
                            text = materialTitle,
                            style = MaterialTheme.typography.labelSmall,
                            // 可读上下文信息（非装饰）：textTertiary 双主题均破 4.5:1（审查矩阵 #4）
                            color = AppColors.colorScheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    FormLabel(stringResource(R.string.review_card_dialog_question_label))
                    OutlinedTextField(
                        value = question,
                        onValueChange = onQuestionChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.review_card_dialog_question_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.colorScheme.textSecondary,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.inputRadius),
                        colors = tokenTextFieldColors(),
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    FormLabel(stringResource(R.string.review_card_dialog_answer_label))
                    OutlinedTextField(
                        value = answer,
                        onValueChange = onAnswerChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.review_card_dialog_answer_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.colorScheme.textSecondary,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.inputRadius),
                        colors = tokenTextFieldColors(),
                    )
                    val errorMessage = (submitState as? RequestState.Error)?.getMessage(context)
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.error,
                            modifier = Modifier.padding(top = AppDimens.spaceSm),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.spaceLg, vertical = AppDimens.spaceMd),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
                ) {
                    val loading = submitState is RequestState.Loading
                    AppButton(
                        text = stringResource(R.string.review_dialog_action_cancel),
                        onClick = onDismiss,
                        variant = AppButtonVariant.SECONDARY,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = stringResource(R.string.review_dialog_action_confirm),
                        onClick = onConfirm,
                        loading = loading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 表单字段标签（captionStrong 12/500 次文本，规范 §3.4 副文案层级） */
@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier.padding(bottom = AppDimens.spaceXs),
    )
}

/** 输入框令牌配色（r14 + surfaceVariant 底 + outline 描边，规范 §3.7） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)
