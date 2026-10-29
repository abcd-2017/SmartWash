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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
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
import com.smartwash.common.utils.selectedColor
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

@Composable
fun OrderPage(
    navController: NavHostController,
    itemId: Int,
    orderViewModel: OrderViewModel = hiltViewModel(),
) {
    var selectedPillIndex by remember {
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

            // 状态筛选胶囊条（设计稿实测：34dp 高、胶囊形；未选中 surface 底 + 1px outline 描边，
            // 选中 primary 底无边框；文字 labelSmall 12/500，选中 onPrimary / 未选中 textSecondary 双主题成对）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)
            ) {
                ShowOrderStatus.entries.forEachIndexed { index, entry ->
                    val isSelected = index == selectedPillIndex
                    // 选中底色 / 文字色走公共 selectedColor（200ms 档弹簧；reduced motion 自动降级 180ms 纯颜色过渡）
                    val pillColor = selectedColor(
                        if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.surface
                    )
                    val labelColor = selectedColor(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else AppColors.colorScheme.textSecondary
                    )
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .pressable(onClick = { selectedPillIndex = index }, debounce = false),
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                        color = pillColor,
                        border = if (isSelected) null else BorderStroke(1.dp, AppColors.colorScheme.outline)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(entry.descriptionRes),
                                color = labelColor,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceXl))

            // 骨架门控（页面侧实现，不改 OrderViewModel）：仅「当前筛选下无数据且正在加载」才出骨架。
            // 取消订单成功后 VM 会整表 loadAllOrders()（loadState=Loading），此时 paging 仍保留旧列表，
            // 走 stale-while-revalidate，不再把有数据的列表整屏换成骨架。
            val showSkeleton = loadState is RequestState.Loading && orderList.isEmpty()

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
                            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AppDimens.pagePadding)
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
                                        // 月份标题（设计稿实测）：12sp labelSmall / Medium / textSecondary / 字距 +0.3sp，
                                        // 与上下卡片的间距由 LazyColumn spacedBy(12) 统一提供
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            letterSpacing = 0.3.sp,
                                            color = AppColors.colorScheme.textSecondary
                                        )
                                    }
                                    is OrderListItem.Group -> {
                                        MonthGroupCard(
                                            group = item,
                                            itemClick = { order -> navController.navigate("${OrderRoute.OrderDetail.text}/${order.orderId}") },
                                            paymentClick = { order -> navController.navigate("${PaymentRoute.Payment.text}/${order.orderId}") },
                                            shipmentClick = { order -> navController.navigate("${OrderRoute.PickupDelivery.text}/${order.orderId}/${OrderRoute.PICKUP_TYPE_DELIVERY}") },
                                            pickupClick = { order -> navController.navigate("${OrderRoute.PickupDelivery.text}/${order.orderId}/${OrderRoute.PICKUP_TYPE_PICKUP}") },
                                            cancelClick = { orderId ->
                                                currOrderId = orderId
                                                confirmPayShow = true
                                            }
                                        )
                                    }
                                }
                            }
                            // 分页尾部 loading：AnimatedVisibility 淡入淡出替代 if 硬插拔；
                            // reduced motion 时降级为静态三点灰占位（取消无限旋转，保留"还在加载"的可读性）
                            item(key = "load_more_footer") {
                                AnimatedVisibility(
                                    visible = isLoadingMore,
                                    enter = fadeIn(tween<Float>(CROSSFADE_ALT_MS)),
                                    exit = fadeOut(tween<Float>(CROSSFADE_ALT_MS))
                                ) {
                                    if (LocalReduceMotion.current) {
                                        StaticLoadingDots(
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
                } else {
                    EmptyState(
                        icon = Icons.Default.LocalLaundryService,
                        message = stringResource(R.string.no_orders)
                    )
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
 * reduced motion 降级占位 — 静态三点灰（规范 §8：取消无限旋转，但保留"加载中"状态可读性）。
 * 非动效组件：本身不含任何动画，仅在 [LocalReduceMotion] 为真时替代 CircularProgressIndicator。
 */
@Composable
private fun StaticLoadingDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.outline)
            )
        }
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
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
                    AppColors.colorScheme.primaryLight to AppColors.colorScheme.primaryDark
                OrderStatus.CANCELED.status ->
                    MaterialTheme.colorScheme.errorContainer to AppColors.colorScheme.error
                else -> AppColors.colorScheme.warningContainer to AppColors.colorScheme.warning
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${order.orderNo} · ${order.laundryPackageVo.itemName}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatOrderTime(order.createdAt),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary
            )
        }
        // 右侧：金额顶部与角标对齐，下距 20 后接操作区 / 状态回显（设计稿实测间距）
        // 价格用 primaryDark（green.700 浅色 / #5BC894 深色成对）；已取消置灰 textSecondary
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = stringResource(R.string.currency_format, order.payPrice.toString()),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 取消订单 — 自绘文字动作（规范 §7 禁 M3 交互组件）
                        Box(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .pressable(onClick = { cancelClick(order.orderId) })
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.cancel_order), color = AppColors.colorScheme.textSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(AppDimens.spaceXs))
                        OrderActionPill(label = stringResource(R.string.go_pay), onClick = paymentClick)
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
 * 行内胶囊主按钮（设计稿实测：34dp 高、radiusFull、primary 底，文字 titleMedium 18/600 onPrimary 双主题成对）。
 * 点击统一走防抖 + 中触感。
 */
@Composable
private fun OrderActionPill(label: String, onClick: () -> Unit) {
    val view = currentView()
    Box(
        modifier = Modifier
            .height(34.dp)
            .pressable(onClick = rememberDebouncedClick {
                view.performHaptic(HapticEffect.MEDIUM)
                onClick()
            })
            .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusFull))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
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
