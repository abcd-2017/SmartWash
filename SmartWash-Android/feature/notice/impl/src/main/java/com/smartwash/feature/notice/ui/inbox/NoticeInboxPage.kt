package com.smartwash.feature.notice.ui.inbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.notice.R
import com.smartwash.feature.notice.api.model.NoticeVo
import com.smartwash.feature.notice.logic.NoticeTimeBucket
import com.smartwash.feature.notice.logic.NoticeTimeParts
import com.smartwash.feature.notice.logic.NoticeType
import com.smartwash.feature.notice.logic.noticeTimeParts

/** 收件箱内容区三态：首载骨架 / 首载失败 / 内容（含空态） */
private enum class NoticeContentState { Skeleton, Error, Content }

/**
 * 收件箱页（扩展提案 #4）：消息中心——通知列表（未读高亮 / 已读灰化）+ 顶部未读计数与
 * 「全部已读」+ 点击标记已读并按需深链跳转。
 *
 * 布局（规范 §1 每屏一个锚点）：本页无 hero 大字，视觉锚点为列表卡片的类型 tag 与未读
 * 圆点；卡片统一 GroupCard 画法（§3.1），行间以卡片间距分隔，不使用发丝线切分页面区块。
 *
 * 深链跳转复用壳层既有入口：smartwash:// 走 ACTION_VIEW（MainActivity onNewIntent →
 * DeepLinkHandler 统一 resolve + 登录守卫 + navigate）；http(s) 直链落内置浏览器
 * （ShellRoute.WebView，与 DeepLinkHandler 的 WebViewUrl 分支同语义）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeInboxPage(
    navController: NavHostController,
    noticeInboxViewModel: NoticeInboxViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val listState by noticeInboxViewModel.listState.collectAsState()
    val notices by noticeInboxViewModel.notices.collectAsState()
    val unreadCount by noticeInboxViewModel.unreadCount.collectAsState()
    val hasMore by noticeInboxViewModel.hasMore.collectAsState()
    val loadMoreState by noticeInboxViewModel.loadMoreState.collectAsState()
    val readAllState by noticeInboxViewModel.readAllState.collectAsState()
    var showReadAllDialog by remember { mutableStateOf(false) }

    // 首载：列表与未读数并行独立（未读数失败不拖垮列表）
    LaunchedEffect(Unit) { noticeInboxViewModel.load() }

    // 全部已读结果反馈（副作用统一在 LaunchedEffect，规范禁组合期副作用）：
    // 成功 → Toast 告知已全读；失败 → 具体错误文案（ResponseInterceptor 转译优先）
    LaunchedEffect(readAllState) {
        when (val state = readAllState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.notice_read_all_success), Toast.LENGTH_SHORT).show()
                noticeInboxViewModel.resetReadAllState()
            }

            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                noticeInboxViewModel.resetReadAllState()
            }

            else -> {}
        }
    }

    // 内容三态：无数据且首载未完成走骨架；无数据且失败走整页错误态；
    // 成功（含空列表）走内容区，空态由列表内承载
    val contentState = when {
        notices.isEmpty() && listState is RequestState.Idle -> NoticeContentState.Skeleton
        notices.isEmpty() && listState is RequestState.Loading -> NoticeContentState.Skeleton
        notices.isEmpty() && listState is RequestState.Error -> NoticeContentState.Error
        else -> NoticeContentState.Content
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.notice_title),
                onBack = { navController.navigateUp() },
                actions = {
                    Text(
                        text = stringResource(R.string.notice_unread_count_format, unreadCount),
                        // 未读数是数量，开 tabular numbers（规范 §2.2 硬规则，避免数字变化左右跳）
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.spaceSm))
                    AppButton(
                        text = stringResource(R.string.notice_read_all),
                        onClick = { showReadAllDialog = true },
                        variant = AppButtonVariant.TEXT,
                        loading = readAllState is RequestState.Loading,
                    )
                },
            )

            // 骨架 ↔ 内容交叉淡变；reduced motion 经 motionSpec 降级为 180ms 纯淡变
            Crossfade(
                targetState = contentState,
                animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
                label = "noticeContent",
            ) { state ->
                when (state) {
                    NoticeContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                    // 首载失败：说明发生了什么（ResponseInterceptor 转译文案优先）+ 重试
                    NoticeContentState.Error -> EmptyState(
                        // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                        icon = Icons.Default.Notifications,
                        message = (listState as? RequestState.Error)?.getMessage(context)
                            ?: stringResource(R.string.notice_load_failed),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppDimens.pagePadding),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.notice_action_retry),
                                onClick = { noticeInboxViewModel.load() },
                                variant = AppButtonVariant.SECONDARY,
                            )
                        },
                    )

                    NoticeContentState.Content -> NoticeList(
                        notices = notices,
                        hasMore = hasMore,
                        loadMoreState = loadMoreState,
                        isRefreshing = listState is RequestState.Loading,
                        onRefresh = { noticeInboxViewModel.load() },
                        onLoadMore = { noticeInboxViewModel.loadMore() },
                        onNoticeClick = { notice ->
                            noticeInboxViewModel.markRead(notice.safeId)
                            handleDeepLink(context, navController, notice.safeDeepLink)
                        },
                    )
                }
            }
        }
    }

    if (showReadAllDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.notice_read_all_confirm_title),
            message = stringResource(R.string.notice_read_all_confirm_message),
            onConfirm = {
                showReadAllDialog = false
                noticeInboxViewModel.readAll()
            },
            onDismiss = { showReadAllDialog = false },
        )
    }
}

/** 列表区：LazyColumn keyed items + 下拉刷新 + 加载更多footer；空态由滚筒母题承载 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeList(
    notices: List<NoticeVo>,
    hasMore: Boolean,
    loadMoreState: RequestState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onNoticeClick: (NoticeVo) -> Unit,
) {
    if (notices.isEmpty()) {
        EmptyState(
            // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
            icon = Icons.Default.Notifications,
            // 引导式文案（§3.6）：本页无自然行动去处（等新消息送达即可），故省略行动按钮
            message = stringResource(R.string.notice_inbox_empty),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.pagePadding),
        )
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 列表底部避让手势条/三键导航（规范 §4：底部预留导航高度 + 余量）——末卡与
        // 「加载更多」footer 可完整滚出系统栏之下；无导航条设备回落纯 24dp 底距
        val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.pagePadding,
                top = AppDimens.spaceXs,
                end = AppDimens.pagePadding,
                bottom = navBarsBottom + AppDimens.spaceXl,
            ),
        ) {
            // key 叠加 createdAt 兜底：后端异常返回多条 id=null 时 safeId 同为 0L，
            // 纯 safeId 会 duplicate key 崩溃（审查19 P2-17）
            items(notices, key = { "notice_${it.safeId}_${it.createdAt}" }) { notice ->
                NoticeCard(
                    notice = notice,
                    onClick = { onNoticeClick(notice) },
                    modifier = Modifier.padding(bottom = AppDimens.cardSpacing),
                )
            }
            if (hasMore) {
                item(key = "load_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppDimens.spaceMd),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (loadMoreState) {
                            is RequestState.Loading -> LoadingDots()
                            is RequestState.Error -> AppButton(
                                text = stringResource(R.string.notice_load_more_failed),
                                onClick = onLoadMore,
                                variant = AppButtonVariant.SECONDARY,
                                small = true,
                            )
                            else -> AppButton(
                                text = stringResource(R.string.notice_load_more),
                                onClick = onLoadMore,
                                variant = AppButtonVariant.TEXT,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通知卡片（GroupCard 唯一画法 + pressable 整卡按压）：
 * 顶行 = 类型 tag（语义色胶囊）+ 未读圆点 + 相对时间；主体 = 标题（未读主色加粗 /
 * 已读次文本灰化）+ 内容摘要（两行省略）。色点不占标题行缩进，未读/已读标题左缘恒对齐。
 */
@Composable
private fun NoticeCard(
    notice: NoticeVo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = NoticeType.from(notice.safeType)
    val timeParts = remember(notice.createdAt) { noticeTimeParts(notice.createdAt) }
    // 未读/已读读屏状态（规范 §10：颜色不是唯一信息载体）：色点纯装饰无语义，
    // 状态经标题 stateDescription 传达（审查19 P1-6）
    val unreadStateLabel = stringResource(R.string.notice_state_unread)
    val readStateLabel = stringResource(R.string.notice_state_read)
    GroupCard(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
        contentPadding = PaddingValues(AppDimens.cardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NoticeTypeTag(type)
            Spacer(modifier = Modifier.weight(1f))
            if (notice.isUnread) {
                // 未读状态色点（规范 §3.5：8dp 圆点，提醒语义取粉/异常色；语义归属待产品裁决，见 P2-12）
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AppColors.colorScheme.iconForegroundPink),
                )
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            }
            if (timeParts != null) {
                Text(
                    text = noticeTimeLabel(timeParts),
                    style = MaterialTheme.typography.bodySmall,
                    // 时间戳是信息性元数据，弱灰 textTertiary on 白仅 2.25:1 触红线（审查19 P1-4）
                    color = AppColors.colorScheme.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Text(
            text = notice.safeTitle,
            style = if (notice.isUnread) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (notice.isUnread) {
                AppColors.colorScheme.textPrimary
            } else {
                AppColors.colorScheme.textSecondary
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = if (notice.isUnread) unreadStateLabel else readStateLabel
                },
        )
        if (notice.safeContent.isNotBlank()) {
            Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
            Text(
                text = notice.safeContent,
                style = MaterialTheme.typography.bodyMedium,
                // 摘要正文是核心可读内容：已读原 textTertiary on 白 2.25:1 触红线（审查19 P0-1），
                // 两态统一 textSecondary（4.90:1），层级靠标题字重/颜色区分
                color = AppColors.colorScheme.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 类型 tag 胶囊（六色浅底按语义：系统通知=橙 / 运营公告=蓝 / 未收录=中性灰） */
@Composable
private fun NoticeTypeTag(type: NoticeType) {
    val (container, foreground) = when (type) {
        NoticeType.SYSTEM ->
            // 前景用文字级深档 onWarningContainer：iconForegroundOrange 是图标档（非文字 3:1
            // 豁免），作 12sp 文字色 on 橙浅底仅 2.50:1 触红线（审查19 P0-2；浅色 #92400E on
            // #FDF3E7 ≈6.6:1，深色 #ECB36A on #332917 达标）
            AppColors.colorScheme.iconContainerOrange to AppColors.colorScheme.onWarningContainer
        NoticeType.ANNOUNCEMENT ->
            AppColors.colorScheme.iconContainerBlue to AppColors.colorScheme.iconForegroundBlue
        NoticeType.UNKNOWN ->
            AppColors.colorScheme.surfaceVariant to AppColors.colorScheme.textSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(container)
            // 间距走令牌：spaceXs=8 / space2xs=2（DESIGN-SPEC §3 允许值集内的组件内微间距档）
            .padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.space2xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(type.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

/** 时间零件 → 中文文案（今天/昨天带时分，本年 M月d日，更早 yyyy-MM-dd） */
@Composable
private fun noticeTimeLabel(parts: NoticeTimeParts): String = when (parts.bucket) {
    NoticeTimeBucket.TODAY -> stringResource(R.string.notice_time_today_format, parts.time)
    NoticeTimeBucket.YESTERDAY -> stringResource(R.string.notice_time_yesterday_format, parts.time)
    NoticeTimeBucket.THIS_YEAR -> stringResource(R.string.notice_time_this_year_format, parts.month, parts.day)
    NoticeTimeBucket.OLDER -> stringResource(R.string.notice_time_older_format, parts.year, parts.month, parts.day)
}

/**
 * 深链分发（复用壳层既有入口）：
 * - `smartwash://` → ACTION_VIEW，由 MainActivity onNewIntent → DeepLinkHandler 统一
 *   resolve + 登录守卫 + navigate（与外部拉起深链完全同一条链路）；
 * - `http(s)://` → 内置浏览器页（ShellRoute.WebView，与 DeepLinkHandler 的 WebViewUrl
 *   分支同语义，避免 external browser 拉起）；
 * - 其余 scheme（后端契约外）忽略，仅标记已读不跳转。
 */
private fun handleDeepLink(context: Context, navController: NavHostController, link: String) {
    if (link.isBlank()) return
    val uri = Uri.parse(link)
    when (uri.scheme?.lowercase()) {
        "smartwash" -> runCatching {
            // 显式限定本应用包名：避免系统 chooser 拦截，确保落回 MainActivity 既有深链入口
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).setPackage(context.packageName)
            )
        }
        "http", "https" -> runCatching {
            navController.navigate(ShellRoute.WebView(url = link))
        }
        else -> Unit
    }
}
