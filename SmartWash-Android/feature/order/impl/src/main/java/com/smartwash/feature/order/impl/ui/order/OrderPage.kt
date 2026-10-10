package com.smartwash.feature.order.impl.ui.order

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
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
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
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

            // 状态筛选胶囊条（规范 §3.3：34dp 高度，圆角 17dp；未选中 surface 底 + 1px outline 描边，选中 brand 底无边框）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)
            ) {
                ShowOrderStatus.entries.forEachIndexed { index, entry ->
                    val isSelected = index == selectedPillIndex
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .pressable(onClick = { selectedPillIndex = index }, debounce = false),
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                        color = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.surface,
                        border = if (isSelected) null else BorderStroke(1.dp, AppColors.colorScheme.outline)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(entry.descriptionRes),
                                color = if (isSelected) Color.White else AppColors.colorScheme.textSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            when (loadState) {
                is RequestState.Loading -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    if (orderList.isNotEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
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
                                        // 月份标题（D-O3）：12sp labelSmall / SemiBold / textSecondary / 字距 +0.4sp，上距 32 下距 10
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            letterSpacing = 0.3.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.colorScheme.textSecondary,
                                            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
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

                            if (isLoadingMore) {
                                item {
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
    // 规范 §3.1：标准卡片画法 — 白底 + 1px 描边 + 轻阴影
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline),
        shadowElevation = AppElevation.level1
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
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
    val view = currentView()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = itemClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧图标容器（规范 §3.2 + 设计稿屏 5）：IconBox 按订单状态映射语义色
        val (container, foreground) = when (order.status) {
            OrderStatus.PENDING_SHIPMENT.status -> AppColors.colorScheme.iconContainerBlue to AppColors.colorScheme.iconForegroundBlue
            OrderStatus.COMPLETED.status -> AppColors.colorScheme.iconContainerTeal to AppColors.colorScheme.iconForegroundTeal
            OrderStatus.CANCELED.status -> AppColors.colorScheme.iconContainerPink to AppColors.colorScheme.iconForegroundPink
            else -> AppColors.colorScheme.iconContainerOrange to AppColors.colorScheme.iconForegroundOrange
        }
        IconBox(
            icon = Icons.Default.LocalLaundryService,
            size = 36.dp,
            iconSize = 18.dp,
            containerColor = container,
            iconTint = foreground
        )
        Spacer(modifier = Modifier.width(12.dp))
        // 主体信息
        Column(modifier = Modifier.weight(1f)) {
            // 状态文字 + 状态色点（规范 §3.5：ongoing=warning / done=success / 已取消 danger）
            val dotColor = when (order.status) {
                OrderStatus.COMPLETED.status -> AppColors.colorScheme.success
                OrderStatus.CANCELED.status -> AppColors.colorScheme.danger
                else -> AppColors.colorScheme.warning
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(OrderStatus.getDescriptionResByStatus(order.status)),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(color = dotColor)
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "${order.orderNo} · ${order.laundryPackageVo.itemName}",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary
            )
            Text(
                text = formatOrderTime(order.createdAt),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        // 右侧价格（金额中字 AmountMedium：20sp/Bold/tnum）；已取消置灰 textTertiary
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = stringResource(R.string.currency_format, order.payPrice.toString()),
                color = if (order.status == OrderStatus.CANCELED.status) {
                    AppColors.colorScheme.textTertiary
                } else {
                    AppColors.colorScheme.primary
                },
                style = AppTextStyles.AmountMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 操作按钮（功能保留，逻辑与路由不动）
            when (order.status) {
                ShowOrderStatus.PENDING_PAYMENT.status -> {
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
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .pressable(onClick = rememberDebouncedClick {
                                view.performHaptic(HapticEffect.MEDIUM)
                                paymentClick()
                            })
                            .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.buttonRadius))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.go_pay), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
                ShowOrderStatus.PENDING_SHIPMENT.status -> {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .pressable(onClick = rememberDebouncedClick {
                                view.performHaptic(HapticEffect.MEDIUM)
                                shipmentClick()
                            })
                            .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.buttonRadius))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.go_ship), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
                ShowOrderStatus.WASHING.status -> {
                    Text(
                        text = stringResource(R.string.washing),
                        color = AppColors.colorScheme.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                ShowOrderStatus.READY_FOR_PICKUP.status -> {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .pressable(onClick = rememberDebouncedClick {
                                view.performHaptic(HapticEffect.MEDIUM)
                                pickupClick()
                            })
                            .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.buttonRadius))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.go_pickup), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
                OrderStatus.COMPLETED.status -> {
                    Text(
                        text = stringResource(R.string.completed),
                        color = AppColors.colorScheme.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                else -> {
                    Text(
                        text = stringResource(OrderStatus.getDescriptionResByStatus(order.status)),
                        color = AppColors.colorScheme.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
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
