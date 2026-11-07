package com.smartwash.feature.readlater.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppPillTabRow
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonBlock
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.pressable
import com.smartwash.feature.readlater.ReadLaterConstant
import com.smartwash.feature.readlater.ReadLaterFormat
import com.smartwash.feature.readlater.R
import com.smartwash.feature.readlater.network.ReadLaterVo

/**
 * 稍后读列表页：收藏输入条（URL+标签 chips+收藏）→ 未读/已读 Tab（status 过滤）→
 * 搜索栏（防抖在 VM 分页触发流）→ 标签筛选行 → 卡片列表（Paging 3 分页 +
 * PullToRefreshBox 下拉刷新 + 骨架/空态/错误重试，规范 §3.6；模式对齐 ToolboxListPage）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadLaterPage(
    navController: NavHostController,
    viewModel: ReadLaterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()

    val editor by viewModel.editor.collectAsState()
    val status by viewModel.status.collectAsState()
    val keyword by viewModel.keyword.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val adding by viewModel.adding.collectAsState()
    val busyIds by viewModel.busyIds.collectAsState()

    // 刷新会话门控（对齐 ToolboxListPage）：下拉刷新/筛选重建会先清空列表，
    // 「清空后、新加载尚未开始」的帧不放空态，会话期间一律骨架
    var refreshSession by rememberSaveable { mutableStateOf(false) }
    var loadStarted by rememberSaveable { mutableStateOf(false) }
    var everLoaded by rememberSaveable { mutableStateOf(false) }

    // 一次性事件（Toast/复制）：LaunchedEffect 消费，禁止组合期副作用
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReadLaterEvent.InvalidUrl -> Toast.makeText(
                    context, context.getString(R.string.readlater_toast_invalid_url), Toast.LENGTH_SHORT,
                ).show()

                is ReadLaterEvent.AddSuccess -> Toast.makeText(
                    context, context.getString(R.string.readlater_toast_add_success), Toast.LENGTH_SHORT,
                ).show()

                is ReadLaterEvent.DigestSuccess -> Toast.makeText(
                    context, context.getString(R.string.readlater_toast_digest_done), Toast.LENGTH_SHORT,
                ).show()

                is ReadLaterEvent.DeleteSuccess -> Toast.makeText(
                    context, context.getString(R.string.readlater_toast_delete_done), Toast.LENGTH_SHORT,
                ).show()

                is ReadLaterEvent.ShortenSuccess -> {
                    copyToClipboard(context, event.shortUrl)
                    Toast.makeText(
                        context, context.getString(R.string.readlater_toast_copy_done), Toast.LENGTH_SHORT,
                    ).show()
                }

                is ReadLaterEvent.Failed -> Toast.makeText(
                    context,
                    // 失败 toast 优先后端文案（ResponseInterceptor 透传 message），空缺兜底本地文案
                    event.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.readlater_toast_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // 首刷/下拉刷新失败提示（append 失败由列表尾重试入口承载）
    val refreshState = pagingItems.loadState.refresh
    LaunchedEffect(refreshState) {
        when (refreshState) {
            is LoadState.Loading -> {
                loadStarted = true
                everLoaded = true
            }
            is LoadState.NotLoading -> {
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
            Toast.makeText(context, context.getString(R.string.readlater_error_load), Toast.LENGTH_SHORT).show()
        }
    }

    // 标签筛选行候选：从已加载条目去重聚合（契约无独立标签枚举端点；
    // itemSnapshotList 非组合期读取，itemCount 变化（首页加载/翻页/刷新）时重算）
    val knownTags = remember(pagingItems.itemCount) {
        pagingItems.itemSnapshotList.items
            .asSequence()
            .flatMap { it.tags.asSequence() }
            .distinct()
            .toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.readlater_title),
                onBack = { navController.navigateUp() },
            )

            // ---------- 收藏输入条 ----------
            GroupCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = editor.url,
                        onValueChange = viewModel::updateUrl,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.readlater_url_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.colorScheme.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(AppDimens.radiusMd),
                        colors = inputFieldColors(),
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    AppButton(
                        text = stringResource(R.string.readlater_action_collect),
                        onClick = viewModel::submit,
                        loading = adding,
                        enabled = editor.url.isNotBlank() && !adding,
                        small = true,
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                // ---------- 标签 chips（可选） ----------
                if (editor.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        editor.tags.forEach { tag ->
                            ReadLaterRemovableTagChip(
                                text = tag,
                                onRemove = { viewModel.removeTag(tag) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = editor.tagInput,
                        onValueChange = viewModel::updateTagInput,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.readlater_tag_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textTertiary,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.commitTag() }),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(AppDimens.radiusMd),
                        colors = inputFieldColors(),
                    )
                    AppButton(
                        text = stringResource(R.string.readlater_tag_action_add),
                        onClick = viewModel::commitTag,
                        variant = AppButtonVariant.TEXT,
                        enabled = editor.tagInput.isNotBlank(),
                    )
                }
            }

            // ---------- 未读/已读 Tab（status 过滤） ----------
            AppPillTabRow(
                tabs = listOf(
                    stringResource(R.string.readlater_tab_unread),
                    stringResource(R.string.readlater_tab_read),
                ),
                selectedIndex = if (status == ReadLaterConstant.STATUS_READ) 1 else 0,
                onSelect = { index ->
                    viewModel.setStatus(
                        if (index == 1) ReadLaterConstant.STATUS_READ else ReadLaterConstant.STATUS_UNREAD,
                    )
                },
                modifier = Modifier.padding(top = AppDimens.spaceSm),
            )

            // ---------- 搜索栏（防抖在 VM 分页触发流） ----------
            OutlinedTextField(
                value = keyword,
                onValueChange = viewModel::updateKeyword,
                placeholder = {
                    Text(
                        text = stringResource(R.string.readlater_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textTertiary,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = AppColors.colorScheme.textTertiary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding)
                    .padding(top = AppDimens.spaceSm)
                    .height(44.dp),
                shape = RoundedCornerShape(AppDimens.radiusMd),
                colors = inputFieldColors(),
            )

            // ---------- 标签筛选行 ----------
            if (knownTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppDimens.spaceXxs)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                    ReadLaterFilterChip(
                        text = stringResource(R.string.readlater_tag_filter_all),
                        selected = tagFilter == null,
                        onClick = { viewModel.selectTag(null) },
                    )
                    knownTags.forEach { tag ->
                        ReadLaterFilterChip(
                            text = tag,
                            selected = tagFilter == tag,
                            onClick = { viewModel.selectTag(tag) },
                        )
                    }
                    Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                }
            }

            // ---------- 卡片列表 ----------
            PullToRefreshBox(
                isRefreshing = refreshSession || (refreshState is LoadState.Loading && pagingItems.itemCount > 0),
                onRefresh = {
                    refreshSession = true
                    pagingItems.refresh()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = AppDimens.spaceXxs),
            ) {
                when {
                    // 首刷失败：说明 + 重试（错误分支前置，会话门控不得掩盖真实错误）
                    pagingItems.itemCount == 0 && refreshState is LoadState.Error -> {
                        EmptyState(
                            icon = Icons.Rounded.Link,
                            message = stringResource(R.string.readlater_error_load),
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                AppButton(
                                    text = stringResource(R.string.readlater_action_retry),
                                    onClick = { pagingItems.retry() },
                                )
                            },
                        )
                    }
                    // 首刷加载 / 刷新会话清空帧 / 从未完成过加载：骨架屏（禁止转圈）
                    pagingItems.itemCount == 0 &&
                        (refreshState is LoadState.Loading || refreshSession || !everLoaded) -> {
                        SkeletonList(modifier = Modifier.fillMaxSize(), rows = 5)
                    }
                    // 空态：走到这里必为 session=false && everLoaded=true，语义=真实空结果
                    pagingItems.itemCount == 0 && refreshState is LoadState.NotLoading -> {
                        EmptyState(
                            icon = Icons.Rounded.Link,
                            message = stringResource(R.string.readlater_empty_message),
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
                                    ReadLaterCard(
                                        vo = vo,
                                        busy = vo.id in busyIds,
                                        onDigest = { viewModel.generateDigest(vo) },
                                        onToggleStatus = { viewModel.toggleStatus(vo) },
                                        onShorten = { viewModel.shorten(vo) },
                                        onDelete = { viewModel.delete(vo) },
                                    )
                                }
                            }
                            // 追加页脚：加载中骨架 / 失败重试（文字按钮）
                            when (pagingItems.loadState.append) {
                                is LoadState.Loading -> {
                                    item(key = "append_loading") { AppendLoadingRow() }
                                }
                                is LoadState.Error -> {
                                    item(key = "append_error") {
                                        Text(
                                            text = stringResource(R.string.readlater_error_load),
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
}

/** 输入框令牌配色（对齐 toolbox 先例：r12 + surfaceVariant 底 + outline 描边） */
@Composable
private fun inputFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.colorScheme.primary,
    unfocusedBorderColor = AppColors.colorScheme.outline,
    focusedContainerColor = AppColors.colorScheme.surfaceVariant,
    unfocusedContainerColor = AppColors.colorScheme.surfaceVariant,
)

@Composable
private fun AppendLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.spaceSm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 骨架块而非转圈（规范 §3.6）
        SkeletonBlock(
            modifier = Modifier
                .width(120.dp)
                .height(14.dp)
        )
    }
}

/**
 * 收藏条目卡（surface 底 + 1px outline 描边 + 轻阴影，规范 §2.4 卡片档）：
 * 顶行 = 图标盒 36（蓝·链接语义）+ 标题（已读弱化为次要色）+ 域名 + 时间；
 * 摘要预览区（无摘要给「生成 AI 摘要」入口，生成中给骨架占位）；标签 chips；操作行
 * （生成摘要/标记已读↔未读/转短链/删除）。
 */
@Composable
private fun ReadLaterCard(
    vo: ReadLaterVo,
    busy: Boolean,
    onDigest: () -> Unit,
    onToggleStatus: () -> Unit,
    onShorten: () -> Unit,
    onDelete: () -> Unit,
) {
    val isRead = vo.status == ReadLaterConstant.STATUS_READ
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                IconBox(
                    icon = Icons.Rounded.Link,
                    size = 36.dp,
                    iconSize = 20.dp,
                    containerColor = AppColors.colorScheme.iconContainerBlue,
                    iconTint = AppColors.colorScheme.iconForegroundBlue,
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vo.title.takeIf { it.isNotBlank() } ?: vo.url,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isRead) {
                            AppColors.colorScheme.textSecondary
                        } else {
                            AppColors.colorScheme.textPrimary
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    Text(
                        text = ReadLaterFormat.hostOf(vo.url),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                Text(
                    text = ReadLaterFormat.formatCreatedAt(vo.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textTertiary,
                )
            }
            // ---------- 摘要预览区 ----------
            when {
                busy -> {
                    // 生成中：骨架占位（禁止转圈），完成后随列表刷新回填
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(14.dp),
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp),
                    )
                }
                vo.digest.isNullOrBlank() -> {
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    // 无摘要：「生成 AI 摘要」入口（描边行，整行 48 热区）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(onClick = onDigest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(AppDimens.radiusMd),
                            color = AppColors.colorScheme.primarySoft,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.readlater_digest_entry),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.colorScheme.primaryDark,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = AppDimens.spaceXs),
                            )
                        }
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                    Text(
                        text = vo.digest,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // ---------- 标签 ----------
            if (vo.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    vo.tags.forEach { tag ->
                        ReadLaterTagChip(text = tag)
                    }
                }
            }
            // ---------- 操作行 ----------
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs)) {
                ReadLaterPillAction(
                    text = stringResource(
                        if (busy) R.string.readlater_digest_generating else R.string.readlater_action_digest,
                    ),
                    onClick = onDigest,
                    enabled = !busy,
                )
                ReadLaterPillAction(
                    text = stringResource(
                        if (isRead) R.string.readlater_action_mark_unread else R.string.readlater_action_mark_read,
                    ),
                    onClick = onToggleStatus,
                    enabled = !busy,
                )
                ReadLaterPillAction(
                    text = stringResource(R.string.readlater_action_shorten),
                    onClick = onShorten,
                    enabled = !busy,
                )
                ReadLaterPillAction(
                    text = stringResource(R.string.readlater_action_delete),
                    onClick = onDelete,
                    enabled = !busy,
                    contentColor = AppColors.colorScheme.dangerInk,
                )
            }
        }
    }
}
