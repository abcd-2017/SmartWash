package com.smartwash.feature.toolbox.ui.list

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SkeletonList
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.ToolboxRoute
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import com.smartwash.feature.toolbox.network.ShortCodeVo
import com.smartwash.feature.toolbox.ui.ToolboxTagChip
import com.smartwash.feature.toolbox.ui.copyToClipboard
import com.smartwash.feature.toolbox.ui.navigateToShortCodeDetail
import java.time.LocalDateTime

/**
 * 我的短链列表页：Paging 3 分页（pagingFlow/PageData 模式）+ PullToRefreshBox 下拉刷新 +
 * 骨架屏/空态/错误提示（规范 §3.6）。条目点击进详情（字段随路由携带）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ToolboxListPage(
    navController: NavHostController,
    // 一镜到底：NavHost 条目 scope（2026-11-01），供列表卡 sharedElement
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    viewModel: ToolboxListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()

    // 刷新会话门控（2026-11-06 闪空态回归）：下拉刷新 / 变更重建都会先把列表清空为 0 项，
    // 在「清空后、新加载尚未开始」的帧里 refresh=NotLoading && itemCount=0 会命中空态分支闪一帧。
    // 会话期间一律走骨架；只有会话结束（有数据，或本轮加载已完成）才允许真实空态。
    // 用 rememberSaveable：旋转/进程重建后 everLoaded 不丢，避免已加载完的 0 项页卡在骨架。
    var refreshSession by rememberSaveable { mutableStateOf(false) }
    var loadStarted by rememberSaveable { mutableStateOf(false) }
    var everLoaded by rememberSaveable { mutableStateOf(false) }

    // 创建/续期/删除成功回列表的刷新信号（发起页经 savedStateHandle 置位）
    val changedState = navController.currentBackStackEntry?.savedStateHandle
        ?.getStateFlow(ToolboxRoute.CHANGED_KEY, false)
        ?.collectAsState()
    LaunchedEffect(changedState?.value) {
        if (changedState?.value == true) {
            refreshSession = true
            viewModel.refresh()
            navController.currentBackStackEntry?.savedStateHandle?.set(ToolboxRoute.CHANGED_KEY, false)
        }
    }

    // 首刷/下拉刷新失败提示（append 失败由 footer 重试入口承载，见列表尾）
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
            Toast.makeText(context, context.getString(R.string.toolbox_error_load), Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.toolbox_title),
                onBack = { navController.navigateUp() },
                actions = {
                    // 文字按钮 — font.title 18/24/600、色 primaryDark（≥18sp 绿字豁免，2026-11-06 评审 R17 保留），
                    // 热区 48 高（画板「热区-新建」56×48）
                    Box(
                        modifier = Modifier
                            .pressable(onClick = { navController.navigate(ToolboxRoute.Create) })
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm)
                    ) {
                        Text(
                            text = stringResource(R.string.toolbox_action_new),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.colorScheme.primaryDark,
                        )
                    }
                },
            )

            PullToRefreshBox(
                // 会话期间保持指示器；错误/数据到达即停（见会话门控注释）
                isRefreshing = refreshSession || (refreshState is LoadState.Loading && pagingItems.itemCount > 0),
                onRefresh = {
                    refreshSession = true
                    pagingItems.refresh()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    // 首刷失败：说明 + 重试按钮（规范 §3.6 错误态）。
                    // 错误分支前置：会话门控不得掩盖真实错误（否则刷新失败时先闪一帧骨架）
                    pagingItems.itemCount == 0 && refreshState is LoadState.Error -> {
                        EmptyState(
                            icon = Icons.Rounded.Link,
                            message = stringResource(R.string.toolbox_error_load),
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                AppButton(
                                    text = stringResource(R.string.toolbox_action_retry),
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
                    // 空态：滚筒圆母题 + 引导 + 行动按钮。
                    // 走到这里必为 session=false && everLoaded=true（会话门控保证），语义=真实空结果
                    pagingItems.itemCount == 0 && refreshState is LoadState.NotLoading -> {
                        EmptyState(
                            icon = Icons.Rounded.Link,
                            message = stringResource(R.string.toolbox_empty_message),
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                AppButton(
                                    text = stringResource(R.string.toolbox_empty_action),
                                    onClick = { navController.navigate(ToolboxRoute.Create) },
                                )
                            },
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
                                    ShortCodeCard(
                                        vo = vo,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        sharedTransitionScope = sharedTransitionScope,
                                        onClick = { navController.navigateToShortCodeDetail(vo) },
                                        onCopy = {
                                            // 复制短链（空缺回落短码）+ 提示，与详情页复制行为一致
                                            val text = vo.shortUrl?.takeIf { it.isNotBlank() } ?: vo.code
                                            copyToClipboard(context, text)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toolbox_copy_success),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
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
                                            text = stringResource(R.string.toolbox_error_load),
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
        com.smartwash.common.ui.components.SkeletonBlock(
            modifier = Modifier
                .width(120.dp)
                .height(14.dp)
        )
    }
}

/**
 * 短链条目卡（画板「短链管理」312×122 · r16 · surface 底 + 1px outline 描边 +
 * 阴影 AppElevation.level1（规范 §2.4 卡片档，替代原「无阴影」写法））：
 * 顶行 = 图标盒 36（蓝·链接语义，icon 20）+ 短码（title 18/600 + tnum）+ 类型/可见性 chip +
 * 右列（点击数 + 复制描边按钮）；有效期独占一行置于顶行下方、与短码列左对齐
 * （2026-11-05 修复⑦：原右列塞「点击数/有效期/复制」三件，有效期文本无宽度约束，
 * 窄屏（≈360dp）把中间 weight 列挤没、「公开」chip 被顶成竖排）；次行为目标链接预览
 * （caption，单行省略）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ShortCodeCard(
    vo: ShortCodeVo,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    // 一镜到底：与详情页信息卡共享元素（key 用短码 id，两侧一致）
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
) {
    val now = remember { LocalDateTime.now() }
    val expiryState = ShortCodeRules.expiryStateOf(vo.expireAt, now)
    val isPublic = ShortCodeRules.isPublicVisibility(vo.isPublic)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 一镜到底：列表卡与详情页信息卡共享边界，导航时平滑展开（2026-11-01）
            .then(
                if (animatedVisibilityScope != null && sharedTransitionScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "shortcode-card-${vo.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                } else {
                    Modifier
                }
            )
            .pressable(onClick = onClick, scaleFactor = 0.97f).softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        // 画板实测：内边距左/上/右 20，底部 12 留给链接行
        Column(
            modifier = Modifier.padding(
                start = AppDimens.cardPadding,
                top = AppDimens.cardPadding,
                end = AppDimens.cardPadding,
                bottom = AppDimens.spaceSm,
            )
        ) {
            Row {
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
                        text = vo.code,
                        style = AppTextStyles.CardTitle.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXxs)) {
                        ToolboxTagChip(
                            text = stringResource(R.string.toolbox_type_short_link),
                            containerColor = AppColors.colorScheme.surfaceVariant,
                            contentColor = AppColors.colorScheme.textSecondary,
                        )
                        // 画板实测：公开 = primaryContainer/onPrimaryContainer；
                        // 私密 = warningContainer 底 + 深棕字（画板 #8F5B0C 为 v0.4 已合并的旧
                        // ongoingInk，端上按条目备选档取其配对字色 onWarningContainer，浅 #92400E/深 #ECB36A）
                        ToolboxTagChip(
                            text = stringResource(
                                if (isPublic) R.string.toolbox_visibility_public_value
                                else R.string.toolbox_visibility_private_value
                            ),
                            containerColor = if (isPublic) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                AppColors.colorScheme.warningContainer
                            },
                            contentColor = if (isPublic) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                AppColors.colorScheme.onWarningContainer
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.toolbox_clicks_format, vo.clickCount),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    CopyPillButton(onClick = onCopy)
                }
            }
            // 有效期独占整行（修复⑦）：与短码列左对齐——短码列 x = 卡内边距 20 + 图标盒 36 +
            // 间距 12 = 68dp（自卡片边缘），本行已在外层 20dp 内边距 Column 内，故补 48dp。
            // 独占整行不再与 chips 竞争宽度，任何屏宽都完整可读。
            // 画板实测：常态 caption 12/400（textSecondary），过期态 captionStrong 12/500（error）
            val (expiryText, expiryColor, expiryStyle) = when (expiryState) {
                is ShortCodeRules.ExpiryState.Permanent ->
                    Triple(
                        stringResource(R.string.toolbox_expiry_permanent),
                        AppColors.colorScheme.textSecondary,
                        MaterialTheme.typography.bodySmall,
                    )
                is ShortCodeRules.ExpiryState.Expired ->
                    Triple(
                        stringResource(R.string.toolbox_expiry_expired),
                        AppColors.colorScheme.dangerInk,
                        MaterialTheme.typography.labelSmall,
                    )
                is ShortCodeRules.ExpiryState.Active ->
                    Triple(
                        stringResource(
                            R.string.toolbox_expiry_until_format,
                            ShortCodeRules.formatDisplayDateTime(vo.expireAt),
                        ),
                        AppColors.colorScheme.textSecondary,
                        MaterialTheme.typography.bodySmall,
                    )
            }
            Text(
                text = expiryText,
                style = expiryStyle,
                color = expiryColor,
                modifier = Modifier.padding(start = 48.dp),
            )
            if (!vo.target.isNullOrBlank()) {
                Text(
                    text = vo.target,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 复制描边按钮 — 画板实测：视觉 52×32 胶囊（透明底 + 1px outline + 文字 captionStrong
 * 12/500 primary），外扩 4/8 热区至 60×48（画板「热区-复制」）满足 48dp 触控红线。
 */
@Composable
private fun CopyPillButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .border(1.dp, AppColors.colorScheme.outline, RoundedCornerShape(AppDimens.radiusFull)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.toolbox_action_copy),
                style = MaterialTheme.typography.labelSmall,
                // 文字绿 textGreen（12sp，2026-11-06 评审 R18 由 primaryDark 改）
                color = AppColors.colorScheme.textGreen,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
