package com.smartwash.feature.order.impl.ui.order

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.DrumMark
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.AppPillTabRow
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.StatusDot
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.feature.order.api.model.ShowOrderStatus
import com.smartwash.feature.order.impl.R
import com.smartwash.feature.laundry.api.LaundryRoute
import com.smartwash.feature.payment.api.PaymentRoute
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 订单列表混合项：月份标题或整月订单组（D-O9 每月合并单卡），用于 LazyColumn 单列表渲染 */
private sealed interface OrderListItem {
    data class Header(val monthKey: String) : OrderListItem
    data class Group(val monthKey: String, val orders: List<OrderInfo>) : OrderListItem
}

/**
 * 内容区 AnimatedContent 的目标键 —— 胶囊下标 + 是否处于骨架态。
 * 骨架门控放在页面侧（不改 OrderViewModel）：刷新期间若列表仍有旧数据，
 * 走 stale-while-revalidate，不再整屏闪骨架。
 */
private data class OrderContentKey(val pillIndex: Int, val skeleton: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderPage(
    navController: NavHostController,
    itemId: Int,
    orderViewModel: OrderViewModel = hiltViewModel(),
) {
    // 用 rememberSaveable：从订单详情返回时组合会重建，tab 选择需保留（原 remember 会重置回路由初始值）
    var selectedPillIndex by rememberSaveable {
        mutableIntStateOf(itemId.coerceIn(0, ShowOrderStatus.entries.size - 1))
    }

    val uiState by orderViewModel.uiState.collectAsState()
    val loadState by orderViewModel.loadState.collectAsState()
    val cancelOrderState by orderViewModel.cancelOrderState.collectAsState()

    val context = LocalContext.current
    val view = currentView()
    var confirmPayShow by remember { mutableStateOf(false) }
    var currOrderId by remember { mutableLongStateOf(-1L) }

    // 状态驱动的副作用统一放 LaunchedEffect，禁止在组合期直接弹 Toast/回写状态
    LaunchedEffect(cancelOrderState) {
        when (cancelOrderState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.cancel_success), Toast.LENGTH_SHORT).show()
                orderViewModel.resetCancelOrderState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (cancelOrderState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                orderViewModel.resetCancelOrderState()
            }
            else -> {}
        }
    }

    // 从订单详情/支付/寄件页返回时刷新列表（ON_RESUME）；首次进入跳过（VM init 已加载，避免重复请求）。
    // N-05-02：走 refreshLoadedOrders 按已加载页数重拉，不再静默丢弃第 2+ 页（原 loadAllOrders
    // 只回 summary 首屏，返回后列表被钳制替换、滚动跳动）
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFirstResume by rememberSaveable { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isFirstResume) isFirstResume = false else orderViewModel.refreshLoadedOrders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val status = ShowOrderStatus.entries[selectedPillIndex].status
    val orderList = uiState.orders[status] ?: emptyList()
    val isLoadingMore = uiState.loadingMore[status] ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.my_orders), onBack = { navController.navigateUp() })

            Spacer(modifier = Modifier.height(AppDimens.spaceSm))

            // 状态筛选胶囊条 — 统一走公共 AppPillTabRow（2026-11-01 缺陷 9：此前页内自绘 8dp 内边距 +
            // labelSmall 12sp 挤压文字，且与券页/游戏中心三套胶囊参数漂移；设计稿 .seg3 即胶囊形态）
            AppPillTabRow(
                tabs = ShowOrderStatus.entries.map { stringResource(it.descriptionRes) },
                selectedIndex = selectedPillIndex,
                onSelect = { selectedPillIndex = it }
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceXl))

            // 骨架门控（页面侧实现，不改 OrderViewModel）：仅「当前筛选下无数据且正在加载」才出骨架。
            // 取消订单成功后 VM 会整表 loadAllOrders()（loadState=Loading），此时 paging 仍保留旧列表，
            // 走 stale-while-revalidate，不再把有数据的列表整屏换成骨架。
            val showSkeleton = loadState is RequestState.Loading && orderList.isEmpty()
            // 下拉刷新指示器：仅「已有列表数据且正在刷新」时转圈，首刷走骨架（对齐 SubscriptionListPage）
            val isRefreshing = loadState is RequestState.Loading && orderList.isNotEmpty()

            // 内容区进出场（动效语言 v0.4）：进 300ms 减速淡入 / 出 200ms 加速淡出；
            // reduced motion 统一降级为 180ms 纯淡入（规范 §8：取消位移/缩放/旋转，保留可读性）
            // 注意：transitionSpec 是 suspend 非 composable 上下文，reduced motion 判定必须在组合期取
            val reduceMotion = LocalReduceMotion.current
            val contentEnterSpec = if (reduceMotion) {
                tween<Float>(CROSSFADE_ALT_MS)
            } else {
                enterTween<Float>(DURATION_CARD_MS)
            }
            val contentExitSpec = if (reduceMotion) {
                tween<Float>(CROSSFADE_ALT_MS)
            } else {
                exitTween<Float>(DURATION_MICRO_MS)
            }
            // 下拉刷新（规范 §3.6）：全项目列表页统一 PullToRefreshBox；
            // N-05-02：走 refreshLoadedOrders 按已加载页数重拉（整表 loadAllOrders 会丢已翻页）
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { orderViewModel.refreshLoadedOrders() },
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = OrderContentKey(selectedPillIndex, showSkeleton),
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(contentEnterSpec) togetherWith fadeOut(contentExitSpec)
                    },
                    label = "orderContent"
                ) { contentKey ->
                    if (contentKey.skeleton) {
                        LoadingState(modifier = Modifier.fillMaxSize())
                    } else if (loadState is RequestState.Error && orderList.isEmpty()) {
                        // 错误态（N-05-03）：断网降级不得伪装成「暂无订单」空态——
                        // 具体文案 + 重试；verticalScroll 让 PullToRefreshBox 的嵌套滚动可达，
                        // 失败状态下拉同样能重新发起请求
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            EmptyState(
                                // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 仅占位
                                icon = Icons.Default.LocalLaundryService,
                                message = (loadState as RequestState.Error).getMessage(context),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 96.dp),
                                action = {
                                    AppButton(
                                        small = true,
                                        text = stringResource(R.string.order_action_retry),
                                        onClick = { orderViewModel.loadAllOrders() },
                                        variant = AppButtonVariant.SECONDARY,
                                    )
                                }
                            )
                        }
                    } else if (orderList.isNotEmpty()) {
                        // 按月分组构建混合列表项（D-O9：每月订单合并为一张卡，不再逐单成卡；分页逻辑保留）
                        val listItems = remember(orderList) {
                            val sorted = orderList.sortedByDescending { it.createdAt }
                            val grouped = sorted.groupBy { it.createdAt.take(7) }
                            val sortedKeys = grouped.keys.sortedDescending()
                            buildList {
                                for (key in sortedKeys) {
                                    add(OrderListItem.Header(key))
                                    grouped[key]?.let { add(OrderListItem.Group(key, it)) }
                                }
                            }
                        }

                        val listState = rememberLazyListState()

                        // 加载更多触发：derivedStateOf 派生"滚动接近末尾"，snapshotFlow 监听触发 loadMore
                        val nearListEnd by remember {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                layoutInfo.totalItemsCount > 0 &&
                                    lastVisibleIndex >= layoutInfo.totalItemsCount - 2
                            }
                        }
                        LaunchedEffect(status) {
                            snapshotFlow {
                                nearListEnd to (uiState.orders[status]?.size ?: 0)
                            }.collect { (nearEnd, listSize) ->
                                if (!nearEnd || listSize == 0) return@collect
                                val current = uiState
                                if (current.hasMore[status] == true && current.loadingMore[status] != true) {
                                    orderViewModel.loadMore(status)
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
                            // 卡间距 16dp（规范 §2.3；月分组卡之间不再用 12dp）
                            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = listItems,
                                key = { item ->
                                    when (item) {
                                        is OrderListItem.Header -> "header_${item.monthKey}"
                                        is OrderListItem.Group -> "group_${item.monthKey}"
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is OrderListItem.Header -> {
                                        val parts = item.monthKey.split("-")
                                        val label = if (parts.size == 2) {
                                            stringResource(
                                                R.string.order_month_group,
                                                parts[0].toIntOrNull() ?: 0,
                                                parts[1].toIntOrNull() ?: 0
                                            )
                                        } else {
                                            item.monthKey
                                        }
                                        // 月份标题（设计稿实测）：12sp labelSmall / Medium / textSecondary，
                                        // 与上下卡片的间距由 LazyColumn spacedBy(12) 统一提供
                                        // U-116：v0.4 字阶已废字距，删除 letterSpacing = 0.3.sp
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppColors.colorScheme.textSecondary
                                        )
                                    }
                                    is OrderListItem.Group -> {
                                        MonthGroupCard(
                                            group = item,
                                            itemClick = { order -> navController.navigate(OrderRoute.OrderDetail(order.orderId)) },
                                            paymentClick = { order -> navController.navigate(PaymentRoute.Payment(order.orderId)) },
                                            shipmentClick = { order -> navController.navigate(OrderRoute.PickupDelivery(order.orderId, OrderRoute.PICKUP_TYPE_DELIVERY)) },
                                            pickupClick = { order -> navController.navigate(OrderRoute.PickupDelivery(order.orderId, OrderRoute.PICKUP_TYPE_PICKUP)) },
                                            cancelClick = { orderId ->
                                                currOrderId = orderId
                                                confirmPayShow = true
                                            }
                                        )
                                    }
                                }
                            }
                            // 分页尾部 loading：AnimatedVisibility 淡入淡出替代 if 硬插拔；
                            // reduced motion 时降级为静态三点灰占位（取消无限旋转，保留"还在加载"的可读性）。
                            // 外层套 Column 提供 ColumnScope：PullToRefreshBox 的 BoxScope 在外层，
                            // 遮蔽了 AnimatedContent 的 ColumnScope 隐式接收器，否则 AnimatedVisibility 解析失败。
                            item(key = "load_more_footer") {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AnimatedVisibility(
                                        visible = isLoadingMore,
                                        enter = fadeIn(tween<Float>(CROSSFADE_ALT_MS)),
                                        exit = fadeOut(tween<Float>(CROSSFADE_ALT_MS))
                                    ) {
                                        if (LocalReduceMotion.current) {
                                            LoadingDots(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                            )
                                        } else {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                                    .wrapContentWidth(Alignment.CenterHorizontally),
                                                color = AppColors.colorScheme.primary,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 空态（P09 设计稿板尾样张 + N-05-03）：白卡设计 + verticalScroll 包装，
                        // 保证 PullToRefreshBox 的下拉手势在空态可达（原空态为非滚动组件拉不动）
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            OrderEmptyState(
                                onGoOrder = { navController.navigate(LaundryRoute.Laundry()) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmPayShow) {
        AppConfirmDialog(
            message = stringResource(R.string.confirm_cancel_order),
            onConfirm = {
                view.performHaptic(HapticEffect.HEAVY)
                if (currOrderId != -1L) orderViewModel.cancelOrder(currOrderId)
                currOrderId = -1L
                confirmPayShow = false
            },
            onDismiss = { confirmPayShow = false }
        )
    }
}

/**
 * 整月订单组卡片（D-O9）：每月一张卡，行间 0.5dp 发丝线（与 Service/Index 同款）。
 * 卡壳本体不可点，点击挂在每行（进该单详情）；动作按钮自身 onClick 消费，与行点击不冲突。
 */
@Composable
private fun MonthGroupCard(
    group: OrderListItem.Group,
    paymentClick: (OrderInfo) -> Unit,
    shipmentClick: (OrderInfo) -> Unit,
    pickupClick: (OrderInfo) -> Unit,
    cancelClick: (Long) -> Unit,
    itemClick: (OrderInfo) -> Unit,
) {
    // DESIGN-SPEC v0.4 §3.1 唯一卡片画法：纯白 surface 底 + 1px outline 描边 + level1(1dp) 阴影
    //（≈ CSS 0 2px 12px rgba(20,40,30,.05)）。旧实现「无阴影裸白卡」属规范明令禁止项。
    Surface(
        modifier = Modifier.fillMaxWidth().softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Column {
            group.orders.forEachIndexed { index, order ->
                OrderRow(
                    order = order,
                    paymentClick = { paymentClick(order) },
                    shipmentClick = { shipmentClick(order) },
                    pickupClick = { pickupClick(order) },
                    cancelClick = cancelClick,
                    itemClick = { itemClick(order) }
                )
                if (index < group.orders.lastIndex) {
                    // §1.3：0.5dp 发丝线只能用在同一张卡片内部的行之间；
                    // 本卡无卡片级内边距（行自己带 horizontal 20dp），故分隔线同步内缩 20dp 与文字对齐
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = AppDimens.cardPadding),
                        thickness = 0.5.dp,
                        color = AppColors.colorScheme.hairline
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderRow(
    order: OrderInfo,
    paymentClick: () -> Unit,
    shipmentClick: () -> Unit,
    pickupClick: () -> Unit,
    cancelClick: (Long) -> Unit,
    itemClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = itemClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧图标容器（设计稿实测：36dp 盒 / 图标 20，按订单状态映射语义色；顶部 +4 与角标文字对齐）
        val (container, foreground) = when (order.status) {
            OrderStatus.PENDING_SHIPMENT.status, OrderStatus.WASHING.status ->
                AppColors.colorScheme.iconContainerBlue to AppColors.colorScheme.iconForegroundBlue
            OrderStatus.COMPLETED.status ->
                AppColors.colorScheme.primaryLight to AppColors.colorScheme.primary
            OrderStatus.CANCELED.status ->
                MaterialTheme.colorScheme.errorContainer to AppColors.colorScheme.error
            else -> AppColors.colorScheme.warningContainer to AppColors.colorScheme.warning
        }
        IconBox(
            icon = Icons.Default.LocalLaundryService,
            modifier = Modifier.padding(top = 4.dp),
            size = 36.dp,
            iconSize = 20.dp,
            containerColor = container,
            iconTint = foreground
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 主体信息
        Column(modifier = Modifier.weight(1f)) {
            // 状态角标胶囊（设计稿 badge：24dp 高、radiusFull、底色/文字成对取语义色）
            val (badgeContainer, badgeContent) = when (order.status) {
                OrderStatus.PENDING_SHIPMENT.status, OrderStatus.WASHING.status ->
                    AppColors.colorScheme.iconContainerBlue to AppColors.colorScheme.iconForegroundBlue
                OrderStatus.COMPLETED.status ->
                    // U-108：原 #1E8C5C on #F0FBF4 3.99:1（<4.5，且 12sp/500 不适用 §10 绿字红线）
                    // → onPrimaryContainer #17322B on primaryContainer #F0FBF4 = 12.97:1
                    AppColors.colorScheme.primaryLight to MaterialTheme.colorScheme.onPrimaryContainer
                OrderStatus.CANCELED.status ->
                    MaterialTheme.colorScheme.errorContainer to AppColors.colorScheme.error
                // U-108：原 #D98A26 on #FDF3E7 2.52:1 → warning 语义深色 #92400E（6.47:1），底 #FDF3E7 不变
                else -> AppColors.colorScheme.warningContainer to AppColors.colorScheme.onWarningContainer
            }
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .background(badgeContainer, RoundedCornerShape(AppDimens.radiusFull))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(OrderStatus.getDescriptionResByStatus(order.status)),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeContent
                )
            }
            // 设计稿实测：角标底→订单号行间距 8（与订单号→时间行一致）
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                // 单号 · 套餐 组合文案资源化（规范 §9「用户可见文案在 strings.xml」）
                text = stringResource(
                    R.string.order_no_item_format,
                    order.orderNo,
                    order.laundryPackageVo.itemName
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
                // 2026-11-01 折行修复：右侧操作列占宽 ~113dp 挤压本列，长文本限单行省略（原会折行撑高卡片）
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatOrderTime(order.createdAt),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 右侧：金额顶部与角标对齐，下距 20 后接操作区 / 状态回显（设计稿实测间距）
        // 强调绿大字取 primary（P10 条目裁定：浅色板 #2D9B6A / 深色提亮 #5BC894 = primary 令牌双主题成对）；
        // 已取消置灰 textSecondary
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                // 金额两位小数：Float.toString() 会把 29.9f 直出成「¥29.9」，与他页「¥29.90」不一致
                text = stringResource(R.string.currency_format, String.format(Locale.CHINA, "%.2f", order.payPrice)),
                color = if (order.status == OrderStatus.CANCELED.status) {
                    AppColors.colorScheme.textSecondary
                } else {
                    AppColors.colorScheme.primary
                },
                style = AppTextStyles.AmountMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(20.dp))
            // 操作按钮（功能保留，逻辑与路由不动）
            when (order.status) {
                ShowOrderStatus.PENDING_PAYMENT.status -> {
                    // 「取消订单」与「去支付」换行分离，中间拉开 12dp 间距（参考设置页退出登录的独立胶囊先例）
                    Column(horizontalAlignment = Alignment.End) {
                        OrderActionPill(label = stringResource(R.string.go_pay), onClick = paymentClick)
                        Spacer(Modifier.height(AppDimens.spaceSm))
                        // 取消订单 — 自绘文字动作（规范 §7 禁 M3 交互组件）；
                        // P09 裁定：字色随画板取中性 textSecondary（不标 error 红）；
                        // 热区 44dp（触控最低档，原 40dp 不达标）
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .pressable(onClick = { cancelClick(order.orderId) })
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.cancel_order),
                                color = AppColors.colorScheme.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                ShowOrderStatus.PENDING_SHIPMENT.status -> {
                    OrderActionPill(label = stringResource(R.string.go_ship), onClick = shipmentClick)
                }
                ShowOrderStatus.WASHING.status -> {
                    Text(
                        text = stringResource(R.string.washing),
                        color = AppColors.colorScheme.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ShowOrderStatus.READY_FOR_PICKUP.status -> {
                    OrderActionPill(label = stringResource(R.string.go_pickup), onClick = pickupClick)
                }
                OrderStatus.COMPLETED.status -> {
                    Text(
                        text = stringResource(R.string.completed),
                        color = AppColors.colorScheme.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    Text(
                        text = stringResource(OrderStatus.getDescriptionResByStatus(order.status)),
                        color = AppColors.colorScheme.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * 行内主按钮（P09 裁定：设计稿画板为胶囊形——radiusFull + 高 40，底色/字色两板一致
 * （浅 primary+白 / 深 #5BC894+墨绿 = primary/onPrimary 令牌成对），
 * 原 §3.3 小按钮档 36/12 矩形与画板形态冲突，按画板执行；
 * 点击统一走防抖 + 中触感。
 */
@Composable
private fun OrderActionPill(label: String, onClick: () -> Unit) {
    val view = currentView()
    Box(
        modifier = Modifier
            .height(40.dp)
            .pressable(onClick = rememberDebouncedClick {
                view.performHaptic(HapticEffect.MEDIUM)
                onClick()
            })
            .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusFull))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 订单列表空态（P09 设计稿板尾样张）：白卡容器内居中滚筒圆母题 + 主标题「还没有订单」
 * + 副标题「去下一单，衣物焕然一新」+「去下单」primary 胶囊 CTA（规范 §3.6 空态
 * 「一句引导文案 + 一个行动按钮」）。本页自组（不扩公共 EmptyState 单行结构），
 * CTA 跳洗衣预约下单页（与首页「预约洗衣」同一目的地）。
 */
@Composable
private fun OrderEmptyState(onGoOrder: () -> Unit) {
    val view = currentView()
    Surface(
        modifier = Modifier.fillMaxWidth().softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.cardPadding, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 母题 — 规范 §3.6：滚筒圆静止态 48dp（非灰色 Material 图标）
            DrumMark(size = 48.dp, tint = AppColors.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.order_empty_title),
                // 设计稿实测 ≈16/600：bodyLarge 16sp + SemiBold（字阶 §2 允许字重集）
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.colorScheme.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.order_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            // 主 CTA — 胶囊、高 40、primary 底（设计稿实测），与行内 OrderActionPill 同语言
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .pressable(onClick = rememberDebouncedClick {
                        view.performHaptic(HapticEffect.MEDIUM)
                        onGoOrder()
                    })
                    .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusFull))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.go_order),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 订单时间相对化展示（D-O7）：同天「今天 HH:mm」、前一天「昨天 HH:mm」、
 * 其余仅「M月d日」（设计稿屏 5 旧日期无时分）；createdAt 解析失败兜底显示原文。时区用系统默认。
 * 纯日期串（无时分部分）直接落「M月d日」档，避免「今天 」带尾随空格（AND-B-009）。
 */
@Composable
private fun formatOrderTime(createdAt: String): String {
    val date = try {
        LocalDate.parse(createdAt.take(10))
    } catch (_: DateTimeParseException) {
        return createdAt
    }
    // 时间部分取 createdAt 第 12-16 位（"HH:mm"）；日期串时为空
    val time = createdAt.drop(11).take(5)
    return when {
        time.isBlank() -> stringResource(R.string.order_time_date, date.monthValue, date.dayOfMonth)
        ChronoUnit.DAYS.between(date, LocalDate.now()) == 0L ->
            stringResource(R.string.order_time_today_prefix, time)
        ChronoUnit.DAYS.between(date, LocalDate.now()) == 1L ->
            stringResource(R.string.order_time_yesterday_prefix, time)
        else -> stringResource(R.string.order_time_date, date.monthValue, date.dayOfMonth)
    }
}
