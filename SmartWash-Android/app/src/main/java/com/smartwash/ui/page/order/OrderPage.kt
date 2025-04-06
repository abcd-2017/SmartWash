package com.smartwash.ui.page.order

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.PickupDeliveryType
import com.smartwash.utils.ShowOrderStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderPage(
    navController: NavHostController,
    itemId: Int,
    orderViewModel: OrderViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(initialPage = itemId) { ShowOrderStatus.entries.size }
    val scope = rememberCoroutineScope()

    val orderStatus by orderViewModel.orderState.collectAsState()
    val orderList = orderViewModel.pagingFlow.collectAsLazyPagingItems()

    LaunchedEffect(pagerState.currentPage) {
        orderViewModel.updateOrderStatus(ShowOrderStatus.entries[pagerState.currentPage].status)
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("我的订单", fontSize = 18.sp) },
            navigationIcon = {
                IconButton(modifier = Modifier.padding(10.dp), onClick = {
                    navController.navigateUp()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = null
                    )
                }
            })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp,
            ) {
                ShowOrderStatus.entries.forEachIndexed { index, title ->
                    Tab(selected = pagerState.currentPage == index, onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }, text = { Text(title.description) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
            ) {
                if (orderList.itemCount > 0) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .animateContentSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(orderList.itemCount) { index ->
                            orderList[index]?.let {
                                OrderCard(it,
                                    paymentClick = { navController.navigate("${PageConstant.Payment.text}/${it.orderId}") },
                                    shipmentClick = { navController.navigate("${PageConstant.PickupDelivery.text}/${it.orderId}/${PickupDeliveryType.DELIVERY.type}") },
                                    pickupClick = { navController.navigate("${PageConstant.PickupDelivery.text}/${it.orderId}/${PickupDeliveryType.PICKUP.type}") }) {
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
                                                .wrapContentWidth(Alignment.CenterHorizontally)
                                        )
                                    }
                                }

                                loadState.append is LoadState.Loading -> {
                                    item {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                                .wrapContentWidth(Alignment.CenterHorizontally)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无订单",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: OrderInfo,
    paymentClick: () -> Unit,
    shipmentClick: () -> Unit,
    pickupClick: () -> Unit,
    itemClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, onClick = itemClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalLaundryService,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = order.laundryPackageVo.itemName,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "2件衣物",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Text(
                    text = "¥${order.totalPrice}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "订单号：${order.orderNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "下单时间：2024-02-05 14:30",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                when (order.status) {
                    ShowOrderStatus.PENDING_PAYMENT.status -> {
                        Button(
                            onClick = paymentClick,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("去支付")
                        }
                    }

                    ShowOrderStatus.PENDING_SHIPMENT.status -> {
                        Button(
                            onClick = shipmentClick,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text("去寄件")
                        }
                    }

                    ShowOrderStatus.WASHING.status -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "清洗中",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    ShowOrderStatus.READY_FOR_PICKUP.status -> {
                        Button(
                            onClick = pickupClick,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("去取件")
                        }
                    }

                    OrderStatus.COMPLETED.status -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已完成",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    else -> {
                        FilledTonalButton(
                            onClick = {},
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("查看详情")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (backgroundColor, textColor, icon) = when (status) {
        ShowOrderStatus.PENDING_PAYMENT.status -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Rounded.Payment
        )

        ShowOrderStatus.PENDING_SHIPMENT.status -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Rounded.LocalShipping
        )

        ShowOrderStatus.WASHING.status -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.Loop
        )

        ShowOrderStatus.READY_FOR_PICKUP.status -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Rounded.CheckCircle
        )

        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            null
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = textColor
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "${OrderStatus.getDescriptionByStatus(status)}", // 根据状态返回对应文案
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}