package com.smartwash.feature.toolbox.ui.assettag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.api.ToolboxRoute
import com.smartwash.feature.toolbox.logic.AssetTagRules
import com.smartwash.feature.toolbox.api.network.AssetTagVo
import com.smartwash.feature.toolbox.ui.ToolboxTagChip
import com.smartwash.feature.toolbox.ui.notifyAssetTagChanged
import com.smartwash.feature.toolbox.ui.navigateToAssetTagDetail

/**
 * 物品标签列表页（一物一码 M2）：四态（首刷骨架/错误/空态/列表+尾部），
 * 手动分页 + 下拉刷新；条目点击进详情（字段随路由携带）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetTagListPage(
    navController: NavHostController,
    viewModel: AssetTagListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // 创建/更新/删除成功回列表的刷新信号（发起页经 savedStateHandle 置位）
    val changedState = navController.currentBackStackEntry?.savedStateHandle
        ?.getStateFlow(ToolboxRoute.ASSET_TAG_CHANGED_KEY, false)
        ?.collectAsState()
    LaunchedEffect(changedState?.value) {
        if (changedState?.value == true) {
            viewModel.refresh()
            navController.currentBackStackEntry?.savedStateHandle?.set(ToolboxRoute.ASSET_TAG_CHANGED_KEY, false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.asset_tag_title),
                onBack = { navController.navigateUp() },
                actions = {
                    Box(
                        modifier = Modifier
                            .pressable(onClick = { navController.navigate(ToolboxRoute.AssetTagCreate) })
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                    ) {
                        Text(
                            text = stringResource(R.string.asset_tag_action_new),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.colorScheme.primaryDark,
                        )
                    }
                },
            )

            when {
                state.loadingFirst -> {
                    SkeletonList(modifier = Modifier.fillMaxSize(), rows = 4)
                }
                state.firstError && state.items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Rounded.Link,
                        message = stringResource(R.string.toolbox_error_load),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.toolbox_action_retry),
                                onClick = { viewModel.refresh() },
                            )
                        },
                    )
                }
                state.items.isEmpty() && !state.refreshing -> {
                    EmptyState(
                        icon = Icons.Rounded.Link,
                        message = stringResource(R.string.asset_tag_empty_message),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.asset_tag_action_new),
                                onClick = { navController.navigate(ToolboxRoute.AssetTagCreate) },
                            )
                        },
                    )
                }
                else -> {
                    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
                            contentPadding = PaddingValues(
                                start = AppDimens.pagePadding,
                                top = AppDimens.spaceXs,
                                end = AppDimens.pagePadding,
                                bottom = AppDimens.spaceMd + navBarsBottom,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = state.items.size,
                                key = { index -> state.items[index].id },
                            ) { index ->
                                AssetTagCard(
                                    vo = state.items[index],
                                    onClick = { navController.navigateToAssetTagDetail(state.items[index]) },
                                )
                            }
                            if (state.loadingMore) {
                                item(key = "append_loading") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = AppDimens.spaceSm),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        com.smartwash.common.ui.components.SkeletonBlock(
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(14.dp)
                                        )
                                    }
                                }
                            }
                            if (state.appendError) {
                                item(key = "append_error") {
                                    Text(
                                        text = stringResource(R.string.toolbox_error_load),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppColors.colorScheme.dangerInk,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pressable(onClick = { viewModel.loadMore() })
                                            .padding(vertical = AppDimens.spaceMd),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 状态 chip 配色：寻物中 = errorContainer / 已寻回 = primaryContainer / 正常 = surfaceVariant */
@Composable
private fun statusChipColors(status: Int): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (status) {
        AssetTagRules.STATUS_LOST ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AssetTagRules.STATUS_FOUND ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else ->
            AppColors.colorScheme.surfaceVariant to AppColors.colorScheme.textSecondary
    }
}

@Composable
private fun statusText(status: Int): String = when (status) {
    AssetTagRules.STATUS_LOST -> stringResource(R.string.asset_tag_status_lost)
    AssetTagRules.STATUS_FOUND -> stringResource(R.string.asset_tag_status_found)
    else -> stringResource(R.string.asset_tag_status_normal)
}

@Composable
private fun AssetTagCard(
    vo: AssetTagVo,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, scaleFactor = 0.97f)
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding)
        ) {
            Row {
                IconBox(
                    icon = Icons.Rounded.Link,
                    size = 36.dp,
                    iconSize = 20.dp,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconTint = AppColors.colorScheme.iconForegroundTeal,
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vo.itemName.orEmpty(),
                        style = com.smartwash.common.ui.theme.AppTextStyles.CardTitle,
                        color = AppColors.colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    val (container, content) = statusChipColors(vo.status)
                    ToolboxTagChip(
                        text = statusText(vo.status),
                        containerColor = container,
                        contentColor = content,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.asset_tag_scans_format, vo.scanCount),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary,
                    )
                }
            }
            if (!vo.itemDesc.isNullOrBlank()) {
                Text(
                    text = vo.itemDesc.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = AppDimens.spaceXs),
                )
            }
        }
    }
}
