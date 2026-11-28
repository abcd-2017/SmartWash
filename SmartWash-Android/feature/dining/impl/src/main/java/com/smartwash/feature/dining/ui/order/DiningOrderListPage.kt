package com.smartwash.feature.dining.ui.order

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.animatedListItem
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
    val activeOrder by viewModel.activeOrder.collectAsState()
    val cancelState by viewModel.cancelState.collectAsState()
    val leaveState by viewModel.leaveState.collectAsState()

    // 「已取消」点击提示（组合期禁副作用，统一收进 effect）
    var cancelledHint by remember { mutableStateOf(false) }
    LaunchedEffect(cancelledHint) {
        if (cancelledHint) {
            Toast.makeText(context, context.getString(R.string.dining_order_cancelled_hint), Toast.LENGTH_SHORT).show()
            cancelledHint = false
        }
    }

    // 放弃草稿结果提示（成功/失败各一次，随后消费）
    LaunchedEffect(cancelState) {
        when (val state = cancelState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.dining_order_cancel_success), Toast.LENGTH_SHORT).show()
                viewModel.consumeCancelState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.consumeCancelState()
            }
            else -> Unit
        }
    }

    // 退出点单结果提示（成功/失败各一次，随后消费）
    LaunchedEffect(leaveState) {
        when (val state = leaveState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.dining_order_leave_success), Toast.LENGTH_SHORT).show()
                viewModel.consumeLeaveState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
                viewModel.consumeLeaveState()
            }
            else -> Unit
        }
    }

    // 待放弃的草稿（二次确认弹窗）：走 common:ui 唯一确认弹窗（自带 dialogEnterMotion +
    // reduced motion 180ms 降级 + dangerInk 确认键），不再用 material3 AlertDialog/TextButton
    // （§7 禁 M3 自带 state layer 的交互组件；文案亦不得硬编码）
    var pendingCancel by remember { mutableStateOf<DiningOrderListItem?>(null) }
    pendingCancel?.let { order ->
        AppConfirmDialog(
            title = stringResource(R.string.dining_order_cancel_confirm_title),
            message = stringResource(R.string.dining_order_cancel_confirm_text),
            confirmText = stringResource(R.string.dining_order_cancel_confirm_yes),
            isDanger = true,
            onConfirm = {
                pendingCancel = null
                viewModel.cancelOrder(order.id)
            },
            onDismiss = { pendingCancel = null },
        )
    }

    // 待退出的点单（参与者二次确认弹窗）：同上，退出为不可逆动作走危险语义
    var pendingLeave by remember { mutableStateOf<DiningOrderListItem?>(null) }
    pendingLeave?.let { order ->
        AppConfirmDialog(
            title = stringResource(R.string.dining_order_leave_confirm_title),
            message = stringResource(R.string.dining_order_leave_confirm_text),
            confirmText = stringResource(R.string.dining_order_action_leave),
            isDanger = true,
            onConfirm = {
                pendingLeave = null
                viewModel.leaveOrder(order.id)
            },
            onDismiss = { pendingLeave = null },
        )
    }

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
            // 页面底走 background 令牌（§2.1 / §9 验收：底 #F5F6F3 与白卡 ≈1.11:1），
            // 与全应用列表页（OrderPage/LaundryPage 等）同口径；白卡层次由 1dp 描边 + level1 阴影承担
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
                                // 状态分流：DRAFT → 创建页（编辑态，共同编辑）；FINISHED → 购买清单结果页
                                when (order.status) {
                                    // 已取消为终态：不进入编辑/清单页，仅提示（避免进入无法操作的页面）
                                    DiningOrderStatus.CANCELLED -> cancelledHint = true
                                    DiningOrderStatus.DRAFT -> navController.navigate(
                                        DiningRoute.OrderCreate(orderId = order.id)
                                    )
                                    else -> navController.navigate(
                                        DiningRoute.ShoppingList(orderId = order.id)
                                    )
                                }
                            }
                        },
                        onReuse = { order ->
                            // 再来一单（13 号票）：带 fromOrderId 进创建页，菜单由后端整份复用
                            if (order.id > 0L) {
                                navController.navigate(
                                    DiningRoute.OrderCreate(
                                        fromOrderId = order.id,
                                        sourceTitle = order.title,
                                    )
                                )
                            }
                        },
                        onCancel = { order ->
                            // 放弃草稿：二次确认后调 cancel（仅发起者 DRAFT 卡展示入口）
                            pendingCancel = order
                        },
                        onLeave = { order ->
                            // 退出点单：二次确认后调 leave（仅参与者 DRAFT 卡展示入口）
                            pendingLeave = order
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
            onClick = {
                // 唯一性收敛：已有本人进行中的 DRAFT → 直接进该单编辑态，不再新建（无则空白创建页）
                val activeId = activeOrder?.safeOrderId ?: 0L
                if (activeId > 0L) {
                    navController.navigate(DiningRoute.OrderCreate(orderId = activeId))
                } else {
                    navController.navigate(DiningRoute.OrderCreate())
                }
            },
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
    onCancel: (DiningOrderListItem) -> Unit,
    onLeave: (DiningOrderListItem) -> Unit,
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
                    // 卡片式：左右页边距 24dp 由容器统一提供（对齐洗衣域订单列表）；
                    // 顶部 16dp = 卡间距（§2.3），兼作「我的收藏」入口卡与列表首项的间隔；
                    // 底部 = FAB 56dp + 20dp 呼吸，与左右页边距同量级（过大时末卡下方空洞）
                    start = AppDimens.pagePadding,
                    end = AppDimens.pagePadding,
                    top = AppDimens.cardSpacing,
                    bottom = navBarsBottom + FAB_SIZE + AppDimens.spaceLg,
                ),
                // 卡间距 16dp（§2.3，与 OrderPage 同口径；原 12dp 为旧值）
                verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
            ) {
                if (createdOrders.isNotEmpty()) {
                    orderSection(
                        titleRes = R.string.dining_order_section_created,
                        orders = createdOrders,
                        onOrderClick = onOrderClick,
                        onReuse = onReuse,
                        onCancel = onCancel,
                        onLeave = onLeave,
                        // 分区标题已表明发起关系，卡内「发起」角标冗余，仅我参与的分区展示
                        showCreatorBadge = false,
                    )
                }
                if (joinedOrders.isNotEmpty()) {
                    orderSection(
                        titleRes = R.string.dining_order_section_joined,
                        orders = joinedOrders,
                        onOrderClick = onOrderClick,
                        onReuse = onReuse,
                        onCancel = onCancel,
                        onLeave = onLeave,
                        showCreatorBadge = true,
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
 * 卡片式（与订单卡同形状：GroupCard 白底 + 1dp 描边 + 轻阴影 + radiusLg）+ 左右 24dp 页边距，
 * 横排 min 56dp；行高铺满卡片故内边距仅水平 20dp（对齐 GroupCard「列表行卡」口径）。
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
            // 图标容器小档（§3.2 v0.4 覆写）：32dp 容器 / 12dp 圆角 / 20dp 图标（原 18dp 为旧值）
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
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
    onCancel: (DiningOrderListItem) -> Unit,
    onLeave: (DiningOrderListItem) -> Unit,
    showCreatorBadge: Boolean,
) {
    item(key = "section_$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = AppTextStyles.SectionTitle,
            color = AppColors.colorScheme.textSecondary,
            // 页边距由列表 contentPadding、卡间 16dp 由 spacedBy 提供；标题再补 16dp 使
            // 「标题 ↔ 上一分区末卡」合计 32dp（§2.3 区块间距），标题与其下首卡保持 16dp
            modifier = Modifier.padding(top = AppDimens.spaceMd),
        )
    }
    items(orders, key = { it.id }) { order ->
        OrderCard(
            order = order,
            onClick = { onOrderClick(order) },
            onReuse = { onReuse(order) },
            onCancel = { onCancel(order) },
            onLeave = { onLeave(order) },
            showCreatorBadge = showCreatorBadge,
            modifier = Modifier.then(animatedListItem()),
        )
    }
}

/**
 * 订单卡（卡片式，对齐洗衣域订单列表画法）：GroupCard 唯一卡片画法（白底 + 1dp outline +
 * 软阴影 + radiusLg），内边距按 TD35 L1 取横 20 / 纵 12；左右 24dp 页边距由列表 contentPadding
 * 统一提供。
 *
 * 内容三层（外卖订单卡惯例，标题即主锚点）：标题行（16sp/600，右侧状态徽标 + 发起角标）→
 * 摘要行（12sp 次要灰 tnum，菜数 + 食材）→ 时间行（12sp 更浅灰 tnum +「再来一单」文字操作，
 * 带 fromOrderId 进创建页整份复用源单菜单）。整卡 pressable。
 */
@Composable
private fun OrderCard(
    order: DiningOrderListItem,
    onClick: () -> Unit,
    onReuse: () -> Unit,
    onCancel: () -> Unit,
    onLeave: () -> Unit,
    showCreatorBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    GroupCard(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
        // 列表卡内边距（TD35 L1）：横 cardPadding 20 / 纵 spaceSm 12（外松内紧），
        // 与卡内 Spacer(spaceSm) 同档；GroupCard 默认四周 20 为信息卡口径，不适用于本列表卡
        contentPadding = PaddingValues(horizontal = AppDimens.cardPadding, vertical = AppDimens.spaceSm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val untitled = order.title.isBlank()
            Text(
                // 空标题（草稿未命名）显示占位锚点（次要色示占位语义），避免只剩状态字无标题
                text = if (untitled) stringResource(R.string.dining_order_title_placeholder) else order.title,
                // 卡片标题 = bodyLarge 档 16/24/600（§2.2 v0.5，CardTitle 别名），原 500 为旧值
                style = AppTextStyles.CardTitle,
                color = if (untitled) AppColors.colorScheme.textTertiary else AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            StatusDot(order.status)
            if (order.isCreator && showCreatorBadge) {
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                Text(
                    text = stringResource(R.string.dining_order_creator_badge),
                    style = MaterialTheme.typography.labelSmall,
                    // primarySoft 底上的文字取 onPrimarySoft（§2.1 配对）：原 primaryDark
                    // #1E8C5C on #E8F6EF 仅 3.44:1，12sp 不足 AA 4.5:1；配对档浅色 10.7:1
                    color = AppColors.colorScheme.onPrimarySoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(AppColors.colorScheme.primarySoft)
                        .padding(horizontal = AppDimens.spaceXs, vertical = 2.dp),
                )
            }
        }
        // 已选菜品 + 食材摘要（用户定案 2026-11-18：所有人从订单列表看到菜单与食材）
        if (order.menuCount > 0 || order.ingredientSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(AppDimens.space2xs))
            Text(
                text = if (order.ingredientSummary.isNotBlank()) {
                    stringResource(
                        R.string.dining_order_list_summary_format,
                        order.menuCount,
                        order.ingredientSummary,
                    )
                } else {
                    stringResource(R.string.dining_order_list_menu_count_format, order.menuCount)
                },
                // 句内含数量（§2.2 硬规则：数量必须开 tabular numbers，与时间行同口径）
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (order.displayTime.isNotBlank()) {
                Text(
                    text = order.displayTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textTertiary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            // 草稿（发起者）提供「放弃」出口；草稿不提供「再来一单」（点卡片进编辑态继续）
            if (order.status == DiningOrderStatus.DRAFT) {
                // 发起者「放弃」；参与者「退出」（各自二次确认后调用，出口互斥）
                if (order.isCreator) {
                    AppButton(
                        text = stringResource(R.string.dining_order_action_cancel),
                        onClick = onCancel,
                        variant = AppButtonVariant.TEXT,
                        small = true,
                    )
                } else {
                    AppButton(
                        text = stringResource(R.string.dining_order_action_leave),
                        onClick = onLeave,
                        variant = AppButtonVariant.TEXT,
                        small = true,
                    )
                }
            } else if (order.status == DiningOrderStatus.FINISHED) {
                // 复用源单语义仅对已定稿单有意义（草稿点卡片继续编辑，已取消不引导复用）
                AppButton(
                    text = stringResource(R.string.dining_order_action_reuse),
                    onClick = onReuse,
                    variant = AppButtonVariant.TEXT,
                    small = true,
                )
            }
        }
    }
}

/** 状态色点 + 状态小字（§3.5：草稿 secondary 灰 / 已结束 success 绿） */
@Composable
private fun StatusDot(status: DiningOrderStatus, modifier: Modifier = Modifier) {
    val (dotColor, label) = when (status) {
        DiningOrderStatus.DRAFT -> MaterialTheme.colorScheme.secondary to stringResource(R.string.dining_order_status_draft)
        DiningOrderStatus.FINISHED -> AppColors.colorScheme.success to stringResource(R.string.dining_order_status_finished)
        DiningOrderStatus.CANCELLED -> AppColors.colorScheme.error to stringResource(R.string.dining_order_status_cancelled)
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
 * Canvas 十字加号（无图标资源依赖，颜色取 onPrimary 令牌与主按钮文字同口径）；
 * pressable 承担按压（禁 M3 FAB/ripple）。
 */
@Composable
private fun CreateOrderFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 无障碍：纯图形 FAB 无文字，必须给 contentDescription，否则 TalkBack 只播报「按钮」
    val fabLabel = stringResource(R.string.dining_order_create_title)
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
            .semantics { contentDescription = fabLabel }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 十字加号：两条圆帽线段（18dp 臂长，2.5dp 线宽居中）。
        // 取 onPrimary 而非硬编码白：深色下 CTA 渐变提亮（清氧 #5BC894 / 吃啥 #FB923C），
        // 白图形对比仅 ≈2.1:1，onPrimary（#17322B / #431407）≈6.7:1（与 AppButton 主按钮同策略）
        val plusColor = AppColors.colorScheme.onPrimary
        Canvas(modifier = Modifier.size(24.dp)) {
            val stroke = 2.5.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val arm = 9.dp.toPx()
            val cap = StrokeCap.Round
            drawLine(
                color = plusColor,
                start = Offset(center.x - arm, center.y),
                end = Offset(center.x + arm, center.y),
                strokeWidth = stroke,
                cap = cap,
            )
            drawLine(
                color = plusColor,
                start = Offset(center.x, center.y - arm),
                end = Offset(center.x, center.y + arm),
                strokeWidth = stroke,
                cap = cap,
            )
        }
    }
}
