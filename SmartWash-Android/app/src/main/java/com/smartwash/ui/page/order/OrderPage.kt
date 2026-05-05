package com.smartwash.ui.page.order

import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.R
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.ui.common.AppTabBar
import com.smartwash.ui.common.EmptyState
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.Background
import com.smartwash.ui.theme.Divider
import com.smartwash.ui.theme.Primary
import com.smartwash.ui.theme.PrimaryLight
import com.smartwash.ui.theme.TextSecondary
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.PickupDeliveryType
import com.smartwash.utils.RequestState
import com.smartwash.utils.ShowOrderStatus
import kotlinx.coroutines.launch

@Composable
fun OrderPage(
    navController: NavHostController,
    itemId: Int,
    orderViewModel: OrderViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(initialPage = itemId) { ShowOrderStatus.entries.size }
    val scope = rememberCoroutineScope()

    val orderList = orderViewModel.pagingFlow.collectAsLazyPagingItems()
    val cancelOrderState by orderViewModel.cancelOrderState.collectAsState()
    val context = LocalContext.current
    var confirmPayShow by remember { mutableStateOf(false) }
    var currOrderId by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(pagerState.currentPage) {
        orderViewModel.updateOrderStatus(ShowOrderStatus.entries[pagerState.currentPage].status)
    }

    when (cancelOrderState) {
        is RequestState.Success -> {
            LaunchedEffect(cancelOrderState) {
                Toast.makeText(context, context.getString(R.string.cancel_success), Toast.LENGTH_SHORT).show()
                orderList.refresh()
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
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (orderList.itemCount > 0) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .animateContentSize()
                            .padding(horizontal = AppDimens.pagePadding)
                    ) {
                        items(orderList.itemCount) { index ->
                            orderList[index]?.let {
                                OrderCard(
                                    it,
                                    paymentClick = { navController.navigate("${PageConstant.Payment.text}/${it.orderId}") },
                                    shipmentClick = { navController.navigate("${PageConstant.PickupDelivery.text}/${it.orderId}/${PickupDeliveryType.DELIVERY.type}") },
                                    pickupClick = { navController.navigate("${PageConstant.PickupDelivery.text}/${it.orderId}/${PickupDeliveryType.PICKUP.type}") },
                                    cancelClick = { orderId ->
                                        currOrderId = orderId
                                        confirmPayShow = true
                                    },
                                ) {
                                    navController.navigate("${PageConstant.OrderDetail.text}/${it.orderId}")
                                }
                            }
                        }

                        orderList.apply {
                            when {
                                loadState.refresh is LoadState.Loading -> {
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
                                loadState.append is LoadState.Loading -> {
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
        AlertDialog(
            onDismissRequest = { confirmPayShow = false },
            text = { Text(stringResource(R.string.confirm_cancel_order)) },
            confirmButton = {
                TextButton({
                    if (currOrderId != -1L) orderViewModel.cancelOrder(currOrderId)
                    currOrderId = -1L
                    confirmPayShow = false
                }) { Text(stringResource(R.string.confirm), color = AppColors.colorScheme.primary) }
            },
            dismissButton = { TextButton({ confirmPayShow = false }) { Text(stringResource(R.string.cancel), color = AppColors.colorScheme.textSecondary) } }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = itemClick),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
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
                            overflow = TextOverflow.Ellipsis,
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
                            onClick = paymentClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.primary, contentColor = Color.White),
                            modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.go_pay)) }
                    }
                    ShowOrderStatus.PENDING_SHIPMENT.status -> {
                        OutlinedButton(
                            onClick = shipmentClick,
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
                            onClick = pickupClick,
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
