package com.smartwash.ui.page.order

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.ui.common.AppConfirmDialog
import com.smartwash.ui.common.AppTabBar
import com.smartwash.ui.common.EmptyState
import com.smartwash.ui.common.LoadingState
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.AppElevation
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.PickupDeliveryType
import com.smartwash.utils.RequestState
import com.smartwash.utils.ShowOrderStatus
import com.smartwash.utils.HapticEffect
import com.smartwash.utils.currentView
import com.smartwash.utils.defaultSpring
import com.smartwash.utils.performHaptic
import com.smartwash.utils.pressAlpha
import kotlinx.coroutines.launch

@Composable
fun OrderPage(
    navController: NavHostController,
    itemId: Int,
    orderViewModel: OrderViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(initialPage = itemId) { ShowOrderStatus.entries.size }
    val scope = rememberCoroutineScope()

    val ordersMap by orderViewModel.ordersMap.collectAsState()
    val loadState by orderViewModel.loadState.collectAsState()
    val loadingMoreMap by orderViewModel.loadingMoreMap.collectAsState()
    val cancelOrderState by orderViewModel.cancelOrderState.collectAsState()

    val context = LocalContext.current
    val view = currentView()
    var confirmPayShow by remember { mutableStateOf(false) }
    var currOrderId by remember { mutableLongStateOf(-1L) }

    when (cancelOrderState) {
        is RequestState.Success -> {
            LaunchedEffect(cancelOrderState) {
                Toast.makeText(context, context.getString(R.string.cancel_success), Toast.LENGTH_SHORT).show()
                orderViewModel.resetCancelOrderState()
            }
        }
        is RequestState.Error -> {
            Toast.makeText(context, (cancelOrderState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
            orderViewModel.resetCancelOrderState()
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.my_orders), onBack = { navController.navigateUp() })

            AppTabBar(
                tabs = ShowOrderStatus.entries.map { stringResource(it.descriptionRes) },
                selectedIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = index,
                            animationSpec = defaultSpring()
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (loadState) {
                is RequestState.Loading -> {
                    LoadingState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val status = ShowOrderStatus.entries[pageIndex].status
                        val orderList = ordersMap[status] ?: emptyList()
                        val isLoadingMore = loadingMoreMap[status] ?: false
                        val hasMore = orderViewModel.hasMoreForStatus(status)

                        if (orderList.isNotEmpty()) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = AppDimens.pagePadding)
                            ) {
                                items(
                                    items = orderList,
                                    key = { it.orderId }
                                ) { order ->
                                    OrderCard(
                                        order,
                                        paymentClick = { navController.navigate("${PageConstant.Payment.text}/${order.orderId}") },
                                        shipmentClick = { navController.navigate("${PageConstant.PickupDelivery.text}/${order.orderId}/${PickupDeliveryType.DELIVERY.type}") },
                                        pickupClick = { navController.navigate("${PageConstant.PickupDelivery.text}/${order.orderId}/${PickupDeliveryType.PICKUP.type}") },
                                        cancelClick = { orderId ->
                                            currOrderId = orderId
                                            confirmPayShow = true
                                        },
                                    ) {
                                        navController.navigate("${PageConstant.OrderDetail.text}/${order.orderId}")
                                    }

                                    if (order == orderList.last() && hasMore && !isLoadingMore) {
                                        LaunchedEffect(status) {
                                            orderViewModel.loadMore(status)
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
            .pressAlpha(0.95f)
            .clickable(onClick = itemClick),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.colorScheme.primaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalLaundryService,
                            contentDescription = null,
                            tint = AppColors.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = order.laundryPackageVo.itemName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = order.laundryPackageVo.description ?: "",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.currency_format, order.payPrice.toString()),
                    color = AppColors.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(AppColors.colorScheme.divider))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.order_no_label, order.orderNo),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.order_time_label, order.createdAt ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (order.status) {
                    ShowOrderStatus.PENDING_PAYMENT.status -> {
                        TextButton(onClick = { cancelClick(order.orderId) }) {
                            Text(stringResource(R.string.cancel_order), color = AppColors.colorScheme.textSecondary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                view.performHaptic(HapticEffect.MEDIUM)
                                paymentClick()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.primary, contentColor = Color.White),
                            modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.go_pay)) }
                    }
                    ShowOrderStatus.PENDING_SHIPMENT.status -> {
                        OutlinedButton(
                            onClick = {
                                view.performHaptic(HapticEffect.MEDIUM)
                                shipmentClick()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.go_ship), color = AppColors.colorScheme.primary) }
                    }
                    ShowOrderStatus.WASHING.status -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppColors.colorScheme.primary))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.washing), color = AppColors.colorScheme.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ShowOrderStatus.READY_FOR_PICKUP.status -> {
                        Button(
                            onClick = {
                                view.performHaptic(HapticEffect.MEDIUM)
                                pickupClick()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.primary, contentColor = Color.White),
                            modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.go_pickup)) }
                    }
                    OrderStatus.COMPLETED.status -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppColors.colorScheme.primary))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.completed), color = AppColors.colorScheme.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppColors.colorScheme.primary))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(OrderStatus.getDescriptionResByStatus(order.status)),
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
