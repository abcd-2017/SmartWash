package com.smartwash.feature.memo.ui.memo

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor
import com.smartwash.feature.memo.MemoConstant
import com.smartwash.feature.memo.R
import com.smartwash.feature.memo.logic.MemoTimeFormat
import com.smartwash.feature.memo.network.MemoTagCountVo
import com.smartwash.feature.memo.network.MemoVo

/**
 * 速记手账页（单页承载全部能力，flomo 式）：
 * 顶部随手记输入区（多行输入 + 标签 chips + 保存，编辑模式复用回填）→ 搜索栏（实时过滤）
 * → 标签筛选行（tags 聚合接口驱动）→ 记录卡片列表（置顶优先由后端排序；卡片操作：
 * 置顶/编辑/删除）。分页走 Paging 3 + 下拉刷新；骨架屏/空态/错误态规范 §3.6。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoPage(
    navController: NavHostController,
    viewModel: MemoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val composer by viewModel.composer.collectAsState()
    val keyword by viewModel.keyword.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val actionToast by viewModel.actionToast.collectAsState()
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()

    // 刷新会话门控（对齐 ToolboxListPage）：刷新/筛选变化会把列表清空为 0 项，
    // 在「清空后、新加载尚未开始」的帧里 refresh=NotLoading && itemCount=0 会命中空态闪一帧。
    // 会话期间一律走骨架；用 rememberSaveable 保证旋转/进程重建后不丢已加载状态。
    var refreshSession by rememberSaveable { mutableStateOf(false) }
    var loadStarted by rememberSaveable { mutableStateOf(false) }
    var everLoaded by rememberSaveable { mutableStateOf(false) }

    // 进入页面拉一次标签聚合（增删改成功后由 VM 内部再次刷新）
    LaunchedEffect(Unit) {
        viewModel.loadTags()
    }

    // 保存结果：成功 → 提示；失败 → 提示后端转译文案（消费复位，防旋转重放）
    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.memo_toast_saved), Toast.LENGTH_SHORT).show()
                viewModel.consumeSubmit()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.consumeSubmit()
            }
            else -> {}
        }
    }

    // 卡片操作失败提示（成功提示由 actionToast 承载）
    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is RequestState.Success -> viewModel.consumeAction()
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.consumeAction()
            }
            else -> {}
        }
    }

    // 卡片操作成功提示
    LaunchedEffect(actionToast) {
        actionToast?.let { resId ->
            Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
            viewModel.consumeActionToast()
        }
    }

    // 首刷/下拉刷新/筛选切换失败提示（append 失败由页脚重试入口承载）
    val refreshState = pagingItems.loadState.refresh
    LaunchedEffect(refreshState) {
        when (refreshState) {
            is LoadState.Loading -> {
                loadStarted = true
                everLoaded = true
            }
            is LoadState.NotLoading -> {
                // 会话结束：有数据，或本轮加载已完成（空结果也放行空态）
                if (pagingItems.itemCount > 0 || loadStarted) {
                    refreshSession = false
                    loadStarted = false
                }
            }
            is LoadState.Error -> {
                refreshSession = false
                loadStarted = false
            }
        }
        if (refreshState is LoadState.Error) {
            Toast.makeText(context, context.getString(R.string.memo_error_load), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            .imePadding()
    ) {
        PageHeader(
            title = stringResource(R.string.memo_title),
            onBack = { navController.navigateUp() },
        )

        MemoComposerCard(
            composer = composer,
            frequentTags = tags.take(MemoConstant.FREQUENT_TAGS_LIMIT),
            submitLoading = submitState is RequestState.Loading,
            onContentChange = viewModel::updateContent,
            onTagDraftChange = viewModel::updateTagDraft,
            onAddDraftTag = viewModel::addTagFromDraft,
            onToggleTag = viewModel::toggleTag,
            onRemoveTag = viewModel::removeTag,
            onCancelEdit = viewModel::cancelEdit,
            onSave = viewModel::save,
        )

        // 搜索栏（实时过滤：keyword 变化经 pagingFlow debounce 后换 Pager）
        OutlinedTextField(
            value = keyword,
            onValueChange = viewModel::setKeyword,
            placeholder = {
                Text(
                    text = stringResource(R.string.memo_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(AppDimens.inputRadius),
            colors = tokenTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.pagePadding)
                .height(48.dp),
        )

        // 标签筛选行（tags 聚合接口驱动；「全部」= 空筛选）
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.spaceXs),
        ) {
            item(key = "filter_all") {
                MemoSelectableChip(
                    text = stringResource(R.string.memo_filter_all),
                    selected = tagFilter == null,
                    onClick = { viewModel.setTagFilter(null) },
                )
            }
            items(tags, key = { it.tag }) { tagVo ->
                MemoSelectableChip(
                    text = stringResource(R.string.memo_tag_with_count_format, tagVo.tag, tagVo.count),
                    selected = tagFilter == tagVo.tag,
                    onClick = {
                        viewModel.setTagFilter(if (tagFilter == tagVo.tag) null else tagVo.tag)
                    },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshSession || (refreshState is LoadState.Loading && pagingItems.itemCount > 0),
            onRefresh = {
                refreshSession = true
                pagingItems.refresh()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                // 首刷失败：说明 + 重试按钮（规范 §3.6 错误态；错误分支前置防会话门控掩盖）
                pagingItems.itemCount == 0 && refreshState is LoadState.Error -> {
                    EmptyState(
                        icon = Icons.Rounded.Notes,
                        message = stringResource(R.string.memo_error_load),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                text = stringResource(R.string.memo_action_retry),
                                onClick = { pagingItems.retry() },
                            )
                        },
                    )
                }
                // 首刷加载 / 刷新会话清空帧 / 从未完成过加载：骨架屏占位（禁止转圈）
                pagingItems.itemCount == 0 &&
                    (refreshState is LoadState.Loading || refreshSession || !everLoaded) -> {
                    SkeletonList(modifier = Modifier.fillMaxSize(), rows = 5)
                }
                // 空态：走到这里必为 session=false && everLoaded=true，语义 = 真实空结果
                // （有关键词/筛选 → 无匹配；否则 → 还没有记录）
                pagingItems.itemCount == 0 && refreshState is LoadState.NotLoading -> {
                    val hasFilter = keyword.isNotBlank() || tagFilter != null
                    EmptyState(
                        icon = Icons.Rounded.Notes,
                        message = stringResource(
                            if (hasFilter) R.string.memo_empty_filtered_message
                            else R.string.memo_empty_message
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
                        contentPadding = PaddingValues(
                            horizontal = AppDimens.pagePadding,
                            vertical = AppDimens.spaceXs,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = pagingItems.itemCount,
                            key = pagingItems.itemKey { it.id },
                        ) { index ->
                            pagingItems[index]?.let { vo ->
                                MemoCard(
                                    vo = vo,
                                    onTogglePin = { viewModel.togglePin(vo) },
                                    onEdit = { viewModel.startEdit(vo) },
                                    onDelete = { viewModel.delete(vo) },
                                )
                            }
                        }
                        // 追加页脚：加载中骨架 / 失败重试（文字入口）
                        when (pagingItems.loadState.append) {
                            is LoadState.Loading -> {
                                item(key = "append_loading") { AppendLoadingRow() }
                            }
                            is LoadState.Error -> {
                                item(key = "append_error") {
                                    Text(
                                        text = stringResource(R.string.memo_error_load),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppColors.colorScheme.dangerInk,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pressable(onClick = { pagingItems.retry() })
                                            .padding(vertical = AppDimens.spaceSm),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶部随手记输入区（新建/编辑双模式复用）：多行内容输入（≤2000 字，输入即截断 +
 * 计数）→ 已选标签（点击移除）→ 标签草稿输入（回车/「添加」提交）→ 常用标签（聚合接口
 * 驱动，点按切换）→ 保存按钮（编辑态切换为「更新」并展示取消编辑入口）。
 */
@Composable
private fun MemoComposerCard(
    composer: MemoViewModel.Composer,
    frequentTags: List<MemoTagCountVo>,
    submitLoading: Boolean,
    onContentChange: (String) -> Unit,
    onTagDraftChange: (String) -> Unit,
    onAddDraftTag: () -> Unit,
    onToggleTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXs),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
        ) {
            // 编辑态横幅：编辑中 chip + 取消编辑入口
            if (composer.isEditing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemoStaticChip(
                        text = stringResource(R.string.memo_composer_editing),
                        containerColor = AppColors.colorScheme.primarySoft,
                        contentColor = AppColors.colorScheme.onPrimarySoft,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextAction(
                        text = stringResource(R.string.memo_action_cancel_edit),
                        color = AppColors.colorScheme.textSecondary,
                        onClick = onCancelEdit,
                    )
                }
            }

            // 多行内容输入（随手记主体）
            OutlinedTextField(
                value = composer.content,
                onValueChange = onContentChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.memo_composer_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                // 长文到上限后转输入框内滚动，防止 2000 字把列表顶出屏幕
                maxLines = 10,
                shape = RoundedCornerShape(AppDimens.inputRadius),
                colors = tokenTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
            )

            // 字数计数（content ≤ 2000 契约，输入即截断）
            Text(
                text = stringResource(
                    R.string.memo_counter_format,
                    composer.content.length,
                    MemoConstant.CONTENT_MAX_LENGTH,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.textTertiary,
                modifier = Modifier.align(Alignment.End),
            )

            // 已选标签：整 chip 可点移除（44dp 热区），下方提示承载可发现性
            if (composer.selectedTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)) {
                        items(composer.selectedTags, key = { it }) { tag ->
                            MemoRemovableTagChip(
                                text = tag,
                                onClick = { onRemoveTag(tag) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.memo_composer_selected_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textTertiary,
                    )
                }
            }

            // 标签草稿输入 + 添加（回车与「添加」等效提交）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
            ) {
                OutlinedTextField(
                    value = composer.tagDraft,
                    onValueChange = onTagDraftChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.memo_composer_tag_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.colorScheme.textSecondary,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    shape = RoundedCornerShape(AppDimens.inputRadius),
                    colors = tokenTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAddDraftTag() }),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )
                Box(
                    modifier = Modifier
                        .pressable(onClick = onAddDraftTag, debounce = false)
                        .heightIn(min = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
                    ) {
                        Text(
                            text = stringResource(R.string.memo_composer_tag_add),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.colorScheme.textGreen,
                            modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
                        )
                    }
                }
            }

            // 常用标签（tags 聚合接口驱动；点按切换加入/移除）
            if (frequentTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.memo_composer_frequent_tags),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textTertiary,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)) {
                    items(frequentTags, key = { it.tag }) { tagVo ->
                        MemoSelectableChip(
                            text = tagVo.tag,
                            selected = tagVo.tag in composer.selectedTags,
                            onClick = { onToggleTag(tagVo.tag) },
                        )
                    }
                }
            }

            // 保存 / 更新（空内容禁用）
            AppButton(
                text = stringResource(
                    if (composer.isEditing) R.string.memo_action_update
                    else R.string.memo_action_save
                ),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = composer.content.isNotBlank(),
                loading = submitLoading,
            )
        }
    }
}

/**
 * 记录卡片（画法对齐短链卡：r16 surface 底 + 1px outline 描边 + softCardShadow）：
 * 置顶标记 chip → 内容 → 标签 chips → 时间行（updateTime 与 createTime 不同时标注「已编辑」）
 * → 操作行（置顶/编辑/删除）。
 */
@Composable
private fun MemoCard(
    vo: MemoVo,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(
                start = AppDimens.cardPadding,
                top = AppDimens.cardPadding,
                end = AppDimens.cardPadding,
                bottom = AppDimens.spaceXs,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
        ) {
            if (vo.pinned) {
                MemoStaticChip(
                    text = stringResource(R.string.memo_card_pinned),
                    containerColor = AppColors.colorScheme.primarySoft,
                    contentColor = AppColors.colorScheme.onPrimarySoft,
                )
            }
            Text(
                text = vo.content,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.colorScheme.textPrimary,
            )
            if (vo.tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs)) {
                    items(vo.tags.distinct(), key = { it }) { tag ->
                        MemoStaticChip(
                            text = tag,
                            containerColor = AppColors.colorScheme.surfaceVariant,
                            contentColor = AppColors.colorScheme.textSecondary,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = MemoTimeFormat.display(vo.createTime),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textSecondary,
                )
                val edited = !vo.updateTime.isNullOrBlank() && vo.updateTime != vo.createTime
                if (edited) {
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    Text(
                        text = stringResource(R.string.memo_card_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textTertiary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextAction(
                    text = stringResource(if (vo.pinned) R.string.memo_action_unpin else R.string.memo_action_pin),
                    color = AppColors.colorScheme.primaryDark,
                    onClick = onTogglePin,
                )
                TextAction(
                    text = stringResource(R.string.memo_action_edit),
                    color = AppColors.colorScheme.primaryDark,
                    onClick = onEdit,
                )
                TextAction(
                    text = stringResource(R.string.memo_action_delete),
                    color = AppColors.colorScheme.dangerInk,
                    onClick = onDelete,
                )
            }
        }
    }
}

// ========== 模块内小组件 ==========

/** 静态标签 chip —— 胶囊（radiusFull），labelSmall，条目上的置顶标记/标签标注 */
@Composable
private fun MemoStaticChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
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

/** 可移除标签 chip（已选标签）：整 chip 可点移除，44dp 最小热区（规范 §2.3） */
@Composable
private fun MemoRemovableTagChip(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .pressable(onClick = onClick, debounce = false)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.radiusFull),
            color = AppColors.colorScheme.primarySoft,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AppDimens.spaceXs),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.onPrimarySoft,
                    modifier = Modifier.padding(vertical = AppDimens.spaceXxs),
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.onPrimarySoft,
                    modifier = Modifier.padding(vertical = AppDimens.spaceXxs),
                )
            }
        }
    }
}

/**
 * 可选筛选 chip —— 选中 = 品牌底，未选中 = 白底描边
 * （模式对齐 SubscriptionSelectableChip：弹簧过渡 + 44dp 热区 + 描边常驻防跳变）。
 */
@Composable
private fun MemoSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = selectedColor(
        if (selected) AppColors.colorScheme.primary else MaterialTheme.colorScheme.surface
    )
    val contentColor = selectedColor(
        if (selected) AppColors.colorScheme.onPrimary else AppColors.colorScheme.textSecondary
    )
    val borderColor = selectedColor(
        if (selected) Color.Transparent else AppColors.colorScheme.outline
    )
    Box(
        modifier = Modifier
            .pressable(onClick = onClick, debounce = false)
            .heightIn(min = 44.dp),
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
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
            )
        }
    }
}

/** 文字操作入口（卡片 置顶/编辑/删除、输入区取消编辑）：44dp 最小热区 + 按压反馈 */
@Composable
private fun TextAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .pressable(onClick = onClick, debounce = false)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = AppDimens.spaceSm),
        )
    }
}

/** 追加页脚加载占位：骨架块而非转圈（规范 §3.6） */
@Composable
private fun AppendLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceSm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(120.dp)
                .height(14.dp)
        )
    }
}

/** 输入框令牌配色（对齐 SubscriptionEditPage/ShortCodeCreatePage 先例） */
@Composable
private fun tokenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    errorBorderColor = AppColors.colorScheme.error,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)
