package com.smartwash.feature.dining.ui.order

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.api.model.DiningOrderStatus
import com.smartwash.feature.dining.logic.DiningOrderListItem

/** 订单列表内容区三态：首载骨架 / 首载失败 / 内容（含空态） */
private enum class OrderListContentState { Skeleton, Error, Content }

/** FAB 尺寸（CreateOrderFab 与列表底部避让共用；规范自绘 FAB 无既有令牌） */
private val FAB_SIZE = 56.dp

/**
 * 订单列表页（吃啥域首页，07 号票）：「我发起的 / 我参与的」分区 + 订单卡（标题/状态/
 * 时间/「再来一单」）+ 右下角 FAB 发起 + 右上角菜谱库入口（04 号票三页的可达性入口——深链
 * host "dining" 已重指向本页，菜谱库不再承载域首页落点）。
 *
 * 布局（规范 §1 每屏一个锚点）：视觉锚点为订单卡列表；FAB 为唯一品牌渐变元素（主 CTA）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiningOrderListPage(
    navController: NavHostController,
    viewModel: DiningOrderListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val listState by viewModel.listState.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadMoreState by viewModel.loadMoreState.collectAsState()

    // 首载（返回本页经 LaunchedEffect(Unit) 不重放——栈上重组，列表保留；下拉刷新显式触发）
    LaunchedEffect(Unit) { viewModel.load() }

    // 返回本页（创建/点单/扫码等离栈再回）或回前台时刷新：新单/状态变更在返回栈重组路径上
    // 可能不重放首载（13 号票修「列表返回不刷新」遗留）；注册前已 RESUMED 的首载由上面 LaunchedEffect 兜住
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contentState = when {
        orders.isEmpty() && listState is RequestState.Loading -> OrderListContentState.Skeleton
        orders.isEmpty() && listState is RequestState.Idle -> OrderListContentState.Skeleton
        orders.isEmpty() && listState is RequestState.Error -> OrderListContentState.Error
        else -> OrderListContentState.Content
    }

    // 分区派生（组合期纯计算；创建即首个成员，我发起的单不出现在「我参与的」）
    val (createdOrders, joinedOrders) = remember(orders) {
        orders.partition { it.isCreator }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.dining_order_list_title),
                onBack = { navController.navigateUp() },
                actions = {
                    // 扫码加入入口已上移壳层全局扫一扫（工作台常驻），此处不再占位
                    AppButton(
                        text = stringResource(R.string.dining_order_action_recipe_library),
                        onClick = { navController.navigate(DiningRoute.RecipeLibrary) },
                        variant = AppButtonVariant.TEXT,
                    )
                },
            )

            Crossfade(
                targetState = contentState,
                animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
                label = "diningOrderListContent",
            ) { state ->
                when (state) {
                    OrderListContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                    OrderListContentState.Error -> EmptyState(
                        // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位
                        icon = Icons.Default.Restaurant,
                        message = (listState as? RequestState.Error)?.getMessage(context)
                            ?: stringResource(R.string.dining_order_list_load_failed),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppDimens.pagePadding),
                        action = {
                            AppButton(
                                small = true,
                                text = stringResource(R.string.dining_recipe_action_retry),
                                onClick = { viewModel.load() },
                                variant = AppButtonVariant.SECONDARY,
                            )
                        },
                    )

                    OrderListContentState.Content -> OrderListContent(
                        createdOrders = createdOrders,
                        joinedOrders = joinedOrders,
                        hasMore = hasMore,
                        loadMoreState = loadMoreState,
                        isRefreshing = listState is RequestState.Loading,
                        onRefresh = { viewModel.load() },
                        onLoadMore = { viewModel.loadMore() },
                        onOrderClick = { order ->
                            if (order.id > 0) {
                                // 列表卡进点单页（09 号票）；分享视图由点单页顶部分享入口进入
                                navController.navigate(DiningRoute.OrderDetail(orderId = order.id))
                            }
                        },
                        onReuse = { order ->
                            // 再来一单（13 号票）：带 fromOrderId 进创建页，菜单由后端整份复用
                            if (order.id > 0) {
                                navController.navigate(
                                    DiningRoute.OrderCreate(
                                        fromOrderId = order.id,
                                        sourceTitle = order.title,
                                    )
                                )
                            }
                        },
                        onFavoritesClick = {
                            navController.navigate(DiningRoute.RecipeFavorites)
                        },
                    )
                }
            }
        }

        // 右下角 FAB（自绘圆形 + Canvas 十字加号：规范 §7 禁 M3 交互组件/ripple，
        // 项目亦无 FAB 先例；品牌渐变底承担主 CTA 语义）
        CreateOrderFab(
            // 带默认值参数的 data class 必须显式构造：裸写 OrderCreate 在 Kotlin 里是
            // Companion 实例（泛型推断为 Companion 类型），运行时 Navigation 找不到其
            // serializer 直接 SerializationException 崩溃（07 号票存量 bug）
            onClick = { navController.navigate(DiningRoute.OrderCreate()) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = AppDimens.pagePadding,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        AppDimens.spaceLg,
                ),
        )
    }
}

/**
 * 列表区：分区（我发起的/我参与的）+ 下拉刷新 + 触底加载更多 footer + 空态；
 * 两区可独立为空（隐藏空区标题）。
 *
 * 触底加载：footer 进入视口（末两项可见）即自动调 [onLoadMore]，无需手动点按；
 * footer 三态（加载点/失败重试/加载更多）与加载更多独立态同源，失败不封底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderListContent(
    createdOrders: List<DiningOrderListItem>,
    joinedOrders: List<DiningOrderListItem>,
    hasMore: Boolean,
    loadMoreState: RequestState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOrderClick: (DiningOrderListItem) -> Unit,
    onReuse: (DiningOrderListItem) -> Unit,
    onFavoritesClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 我的收藏入口（契约 §三.4）：常驻入口，空列表时亦可进入收藏
        FavoritesEntry(onClick = onFavoritesClick)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
        // 空态也放进刷新容器：空列表同样支持下拉刷新（与有数据态交互一致）
        if (createdOrders.isEmpty() && joinedOrders.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Restaurant,
                message = stringResource(R.string.dining_order_list_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppDimens.pagePadding),
            )
        } else {
            // 列表底部避让手势条/三键导航——末卡呼吸空间 = FAB 高(56dp) + 底距(spaceLg) + 空隙(spaceLg)，
            // 保证滚动到底不遮挡末卡
            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val listState = rememberLazyListState()

            // 触底探测：末两项可见即认为到达底部（footer 出现前提前半屏触发，滚动更顺）；
            // 仅在还有下一页且非加载中时触发，加载更多失败保留 footer 重试入口
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 2
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore && hasMore && loadMoreState !is RequestState.Loading) {
                    onLoadMore()
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppDimens.pagePadding,
                    top = AppDimens.spaceXs,
                    end = AppDimens.pagePadding,
                    bottom = navBarsBottom + FAB_SIZE + AppDimens.spaceLg * 2,
                ),
            ) {
                if (createdOrders.isNotEmpty()) {
                    orderSection(
                        titleRes = R.string.dining_order_section_created,
                        orders = createdOrders,
                        onOrderClick = onOrderClick,
                        onReuse = onReuse,
                    )
                }
                if (joinedOrders.isNotEmpty()) {
                    orderSection(
                        titleRes = R.string.dining_order_section_joined,
                        orders = joinedOrders,
                        onOrderClick = onOrderClick,
                        onReuse = onReuse,
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
                                // 触底已自动触发，正常态按钮仅作失败后的手动重试入口
                                is RequestState.Loading -> LoadingDots()
                                is RequestState.Error -> AppButton(
                                    text = stringResource(R.string.dining_order_load_more_failed),
                                    onClick = onLoadMore,
                                    variant = AppButtonVariant.SECONDARY,
                                    small = true,
                                )
                                else -> AppButton(
                                    text = stringResource(R.string.dining_load_more),
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
    }
}

/**
 * 我的收藏入口卡（契约 §三.4）：图标容器（橙=待办/收藏语义）+ 标题 + 箭头，整卡 pressable。
 * 位于订单分区之上，作为域内常驻入口。
 */
@Composable
private fun FavoritesEntry(onClick: () -> Unit) {
    GroupCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            .pressable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = AppDimens.cardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                    .background(AppColors.colorScheme.iconContainerOrange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AppColors.colorScheme.iconForegroundOrange,
                )
            }
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Text(
                text = stringResource(R.string.dining_order_action_favorites),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.colorScheme.textTertiary,
            )
        }
    }
}

/** 分区（区块标题 + 订单卡组；section key 固定、订单卡 key 用订单 ID 防错位复用） */
private fun LazyListScope.orderSection(
    titleRes: Int,
    orders: List<DiningOrderListItem>,
    onOrderClick: (DiningOrderListItem) -> Unit,
    onReuse: (DiningOrderListItem) -> Unit,
) {
    item(key = "section_$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textSecondary,
            modifier = Modifier.padding(top = AppDimens.spaceSm, bottom = AppDimens.spaceXs, start = AppDimens.spaceXxs),
        )
    }
    items(orders, key = { it.id }) { order ->
        OrderCard(
            order = order,
            onClick = { onOrderClick(order) },
            onReuse = { onReuse(order) },
            modifier = Modifier.padding(bottom = AppDimens.cardSpacing),
        )
    }
}

/**
 * 订单卡（GroupCard 唯一画法 + pressable 整卡按压）：
 * 状态色点（§3.5 8dp：进行中橙 / 已结束绿）+ 标题 + 发起角标 + 时间行 + 「再来一单」文字操作
 * （13 号票：带 fromOrderId 进创建页，整份复用源单菜单；内层 pressable 消费点击不进点单页）。
 */
@Composable
private fun OrderCard(
    order: DiningOrderListItem,
    onClick: () -> Unit,
    onReuse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(order.status)
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Text(
                text = order.title,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (order.isCreator) {
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                Text(
                    text = stringResource(R.string.dining_order_creator_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.primaryDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(AppColors.colorScheme.primarySoft)
                        .padding(horizontal = AppDimens.spaceXs, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (order.displayTime.isNotBlank()) {
                Text(
                    text = order.displayTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            AppButton(
                text = stringResource(R.string.dining_order_action_reuse),
                onClick = onReuse,
                variant = AppButtonVariant.TEXT,
                small = true,
            )
        }
    }
}

/** 状态色点 + 状态小字（§3.5：进行中 #D98A26 橙 / 已结束 success 绿） */
@Composable
private fun StatusDot(status: DiningOrderStatus, modifier: Modifier = Modifier) {
    val (dotColor, label) = when (status) {
        DiningOrderStatus.ONGOING -> AppColors.colorScheme.ongoing to stringResource(R.string.dining_order_status_ongoing)
        DiningOrderStatus.FINISHED -> AppColors.colorScheme.success to stringResource(R.string.dining_order_status_finished)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceXxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary,
        )
    }
}

/**
 * 发起点单 FAB：56dp 圆形 + 品牌渐变（135° primary→primaryDeep，规范 §3.3 主 CTA 语义）+
 * Canvas 白色十字加号（无图标资源依赖）；pressable 承担按压（禁 M3 FAB/ripple）。
 */
@Composable
private fun CreateOrderFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(FAB_SIZE)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        AppColors.colorScheme.ctaGradientStart,
                        AppColors.colorScheme.ctaGradientEnd,
                    ),
                ),
            )
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 十字加号：两条圆帽线段（18dp 臂长，2.5dp 线宽居中）
        Canvas(modifier = Modifier.size(24.dp)) {
            val stroke = 2.5.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val arm = 9.dp.toPx()
            val cap = StrokeCap.Round
            drawLine(
                color = Color.White,
                start = Offset(center.x - arm, center.y),
                end = Offset(center.x + arm, center.y),
                strokeWidth = stroke,
                cap = cap,
            )
            drawLine(
                color = Color.White,
                start = Offset(center.x, center.y - arm),
                end = Offset(center.x, center.y + arm),
                strokeWidth = stroke,
                cap = cap,
            )
        }
    }
}
