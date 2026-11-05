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
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.AppPillTabRow
import com.smartwash.common.ui.components.PageHeader
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
import com.smartwash.feature.payment.api.PaymentRoute
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

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

    // 从订单详情/支付/寄件页返回时刷新列表（ON_RESUME）；首次进入跳过（VM init 已加载，避免重复请求）
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFirstResume by rememberSaveable { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isFirstResume) isFirstResume = false else orderViewModel.loadAllOrders()
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
            // 下拉刷新（规范 §3.6）：全项目列表页统一 PullToRefreshBox，刷新走 loadAllOrders 整表重载
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { orderViewModel.loadAllOrders() },
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
                        EmptyState(
                            icon = Icons.Default.LocalLaundryService,
                            message = stringResource(R.string.no_orders)
                        )
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
        // 价格用 primaryDark（green.700 浅色 / #5BC894 深色成对）；已取消置灰 textSecondary
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                // 金额两位小数：Float.toString() 会把 29.9f 直出成「¥29.9」，与他页「¥29.90」不一致
                text = stringResource(R.string.currency_format, String.format("%.2f", order.payPrice)),
                color = if (order.status == OrderStatus.CANCELED.status) {
                    AppColors.colorScheme.textSecondary
                } else {
                    AppColors.colorScheme.primaryDark
                },
                style = AppTextStyles.AmountMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(20.dp))
            // 操作按钮（功能保留，逻辑与路由不动）
            when (order.status) {
                ShowOrderStatus.PENDING_PAYMENT.status -> {
                    // U-109：危险操作按 §4 用 error 色并独立分组——「取消订单」与「去支付」
                    // 换行分离，中间拉开 12dp 间距（参考设置页退出登录的独立胶囊先例）
                    Column(horizontalAlignment = Alignment.End) {
                        OrderActionPill(label = stringResource(R.string.go_pay), onClick = paymentClick)
                        Spacer(Modifier.height(AppDimens.spaceSm))
                        // 取消订单 — 自绘文字动作（规范 §7 禁 M3 交互组件）；
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
                                color = AppColors.colorScheme.error,
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
 * 行内主按钮（2026-11-01 按钮统一：原 34dp 高配 18sp 字是"矮高+大字"错位组合，全应用唯一）。
 * 统一到规范 §3.3 小按钮档：36dp 高 / 12dp 圆角 / 横 16 / 13sp·600，与优惠券 ClaimAction 一致；
 * 点击统一走防抖 + 中触感。
 */
@Composable
private fun OrderActionPill(label: String, onClick: () -> Unit) {
    val view = currentView()
    Box(
        modifier = Modifier
            .height(36.dp)
            .pressable(onClick = rememberDebouncedClick {
                view.performHaptic(HapticEffect.MEDIUM)
                onClick()
            })
            .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusMd))
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
 * 订单时间相对化展示（D-O7）：同天「今天 HH:mm」、前一天「昨天 HH:mm」、
 * 其余仅「M月d日」（设计稿屏 5 旧日期无时分）；createdAt 解析失败兜底显示原文。时区用系统默认。
 */
@Composable
private fun formatOrderTime(createdAt: String): String {
    val date = try {
        LocalDate.parse(createdAt.take(10))
    } catch (_: DateTimeParseException) {
        return createdAt
    }
    // 时间部分取 createdAt 第 12-16 位（"HH:mm"）
    val time = createdAt.drop(11).take(5)
    return when (ChronoUnit.DAYS.between(date, LocalDate.now())) {
        0L -> stringResource(R.string.order_time_today_prefix, time)
        1L -> stringResource(R.string.order_time_yesterday_prefix, time)
        else -> stringResource(R.string.order_time_date, date.monthValue, date.dayOfMonth)
    }
}
