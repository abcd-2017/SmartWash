package com.smartwash.feature.order.impl.ui.order

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.model.RequestState
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.feature.order.api.model.ShowOrderStatus
import com.smartwash.feature.order.impl.R
import com.smartwash.feature.payment.api.PaymentRoute

/** 订单列表混合项：月份标题或订单卡片，用于 LazyColumn 单列表渲染 */
private sealed interface OrderListItem {
    data class Header(val monthKey: String) : OrderListItem
    data class Card(val order: OrderInfo) : OrderListItem
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

            // 状态筛选胶囊条（替代原 AppTabBar + HorizontalPager）
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
                            .height(36.dp)
                            .pressable(onClick = { selectedPillIndex = index }),
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                        color = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(entry.descriptionRes),
                                color = if (isSelected) Color.White else AppColors.colorScheme.textPrimary,
                                style = MaterialTheme.typography.bodyMedium
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
                        // 按月分组构建混合列表项（分页逻辑保留，改为单列表驱动）
                        val listItems = remember(orderList) {
                            val sorted = orderList.sortedByDescending { it.createdAt }
                            val grouped = sorted.groupBy { it.createdAt.take(7) }
                            val sortedKeys = grouped.keys.sortedDescending()
                            buildList {
                                for (key in sortedKeys) {
                                    add(OrderListItem.Header(key))
                                    grouped[key]?.forEach { add(OrderListItem.Card(it)) }
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
                                        is OrderListItem.Card -> "card_${item.order.orderId}"
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
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AppColors.colorScheme.textSecondary,
                                            modifier = Modifier.padding(top = AppDimens.spaceMd, bottom = AppDimens.spaceXs)
                                        )
                                    }
                                    is OrderListItem.Card -> {
                                        OrderCard(
                                            order = item.order,
                                            paymentClick = { navController.navigate("${PaymentRoute.Payment.text}/${item.order.orderId}") },
                                            shipmentClick = { navController.navigate("${OrderRoute.PickupDelivery.text}/${item.order.orderId}/${OrderRoute.PICKUP_TYPE_DELIVERY}") },
                                            pickupClick = { navController.navigate("${OrderRoute.PickupDelivery.text}/${item.order.orderId}/${OrderRoute.PICKUP_TYPE_PICKUP}") },
                                            cancelClick = { orderId ->
                                                currOrderId = orderId
                                                confirmPayShow = true
                                            }
                                        ) {
                                            navController.navigate("${OrderRoute.OrderDetail.text}/${item.order.orderId}")
                                        }
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

@Composable
private fun OrderCard(
    order: OrderInfo,
    paymentClick: () -> Unit,
    shipmentClick: () -> Unit,
    pickupClick: () -> Unit,
    cancelClick: (Long) -> Unit,
    itemClick: () -> Unit,
) {
    val view = currentView()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = itemClick, alphaFactor = 0.95f),
        shape = RoundedCornerShape(AppDimens.radiusMd),
        color = AppColors.colorScheme.surfaceVariant,
        shadowElevation = AppElevation.level1
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧状态色点
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            // 图标（无背景，primary 着色）
            Icon(
                imageVector = Icons.Default.LocalLaundryService,
                contentDescription = null,
                tint = AppColors.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            // 主体信息 + 操作按钮
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.laundryPackageVo.itemName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = stringResource(R.string.order_no_label, order.orderNo),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Text(
                    text = stringResource(R.string.order_time_label, order.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = stringResource(R.string.currency_format, order.payPrice.toString()),
                    color = AppColors.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(AppDimens.spaceSm))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    when (order.status) {
                        ShowOrderStatus.PENDING_PAYMENT.status -> {
                            TextButton(onClick = { cancelClick(order.orderId) }) {
                                Text(stringResource(R.string.cancel_order), color = AppColors.colorScheme.textSecondary)
                            }
                            Spacer(Modifier.width(AppDimens.spaceXs))
                            Button(
                                onClick = {
                                    view.performHaptic(HapticEffect.MEDIUM)
                                    paymentClick()
                                },
                                shape = RoundedCornerShape(AppDimens.radiusMd),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.primary, contentColor = Color.White),
                                modifier = Modifier.height(36.dp)
                            ) { Text(stringResource(R.string.go_pay)) }
                        }
                        ShowOrderStatus.PENDING_SHIPMENT.status -> {
                            Button(
                                onClick = {
                                    view.performHaptic(HapticEffect.MEDIUM)
                                    shipmentClick()
                                },
                                shape = RoundedCornerShape(AppDimens.radiusMd),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.primary, contentColor = Color.White),
                                modifier = Modifier.height(36.dp)
                            ) { Text(stringResource(R.string.go_ship)) }
                        }
                        ShowOrderStatus.WASHING.status -> {
                            Text(
                                text = stringResource(R.string.washing),
                                color = AppColors.colorScheme.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        ShowOrderStatus.READY_FOR_PICKUP.status -> {
                            Button(
                                onClick = {
                                    view.performHaptic(HapticEffect.MEDIUM)
                                    pickupClick()
                                },
                                shape = RoundedCornerShape(AppDimens.radiusMd),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.primary, contentColor = Color.White),
                                modifier = Modifier.height(36.dp)
                            ) { Text(stringResource(R.string.go_pickup)) }
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
    }
}
