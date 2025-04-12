package com.smartwash.ui.page.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Wash
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.ui.common.InfoRow
import com.smartwash.ui.common.InfoSection
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.RequestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailPage(
    navController: NavHostController,
    orderId: Long,
    orderDetailViewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val getOrderDetailState by orderDetailViewModel.getOrderInfoDetail.collectAsState()
    val orderInfo by orderDetailViewModel.orderInfo.collectAsState()

    if (orderId != -1L) {
        LaunchedEffect(Unit) {
            orderDetailViewModel.getOrderDetail(orderId)
        }
    }

    when (getOrderDetailState) {
        is RequestState.Error -> {
            Toast.makeText(
                LocalContext.current,
                (getOrderDetailState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            orderDetailViewModel.resetState()
        }

        else -> {}
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("订单详情", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 订单状态卡片
            StatusCard(orderStatus = orderInfo?.status ?: "-1")

            Spacer(modifier = Modifier.height(24.dp))

            // 学校信息
            InfoSection(
                title = "学校信息",
                icon = Icons.Rounded.School
            ) {
                InfoRow("学校名称", orderInfo?.schoolsVo?.schoolName ?: "")
                InfoRow("地址", orderInfo?.schoolsVo?.location ?: "")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 寄存柜信息
            if (orderInfo != null && (orderInfo!!.status == OrderStatus.PENDING_SHIPMENT.status || orderInfo!!.status == OrderStatus.READY_FOR_PICKUP.status)) {
                InfoSection(
                    title = "寄存柜信息",
                    icon = Icons.Rounded.Storage
                ) {
                    InfoRow("柜号", "${orderInfo?.lockersVo?.lockerNumber ?: 0}")
                    InfoRow("取件码", orderInfo?.pickupCode?.let { it.split(":")[2] } ?: "")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 套餐信息
            InfoSection(
                title = "套餐信息",
                icon = Icons.Rounded.LocalLaundryService
            ) {
                InfoRow("套餐类型", orderInfo?.laundryPackageVo?.itemName ?: "")
                InfoRow("价格", "￥${orderInfo?.totalPrice ?: 0}")
                InfoRow("实付款", "￥${orderInfo?.payPrice ?: 0}")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 订单信息
            InfoSection(
                title = "订单信息",
                icon = Icons.Rounded.Receipt
            ) {
                InfoRow("订单编号", orderInfo?.orderNo ?: "")
                InfoRow("下单时间", orderInfo?.createdAt ?: "")
                InfoRow(
                    "当前状态",
                    "${OrderStatus.getDescriptionByStatus(orderInfo?.status ?: "001")}"
                )
                InfoRow("完成时间", "-")
            }
        }
    }
}

@Composable
fun StatusCard(orderStatus: String) {
    data class OrderStatusInfo(
        val statusText: String,
        val statusColor: Color,
        val icon: ImageVector,
        val estimatedTime: String,
    )

    val orderInfo: OrderStatusInfo = when (orderStatus) {
        OrderStatus.PENDING_PAYMENT.status -> OrderStatusInfo(
            "待支付",
            MaterialTheme.colorScheme.tertiary,
            Icons.Rounded.HourglassEmpty,
            "请尽快支付"
        )

        OrderStatus.WASHING.status -> OrderStatusInfo(
            "清洗中",
            MaterialTheme.colorScheme.primary,
            Icons.Rounded.Wash,
            "预计完成时间：2024-02-06 16:30"
        )

        OrderStatus.PENDING_SHIPMENT.status -> OrderStatusInfo(
            "待寄件",
            MaterialTheme.colorScheme.primary,
            Icons.AutoMirrored.Rounded.Send,
            "请尽快去寄件"
        )

        OrderStatus.READY_FOR_PICKUP.status -> OrderStatusInfo(
            "待取件",
            MaterialTheme.colorScheme.primary,
            Icons.Rounded.Toys,
            "衣物已到寄存柜"
        )

        OrderStatus.COMPLETED.status -> OrderStatusInfo(
            "已完成",
            MaterialTheme.colorScheme.secondary,
            Icons.Rounded.CheckCircle,
            "完成时间：2024-02-06 16:30"
        )

        OrderStatus.CANCELED.status -> OrderStatusInfo(
            "已取消",
            MaterialTheme.colorScheme.error,
            Icons.Rounded.Cancel,
            "取消时间：2024-02-06 14:30"
        )

        else -> {
            OrderStatusInfo(
                "",
                MaterialTheme.colorScheme.onBackground,
                Icons.Rounded.HourglassEmpty,
                ""
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = orderInfo.statusColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = orderInfo.icon,
                contentDescription = null,
                tint = orderInfo.statusColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = orderInfo.statusText,
                style = MaterialTheme.typography.titleLarge,
                color = orderInfo.statusColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = orderInfo.estimatedTime,
                style = MaterialTheme.typography.bodyMedium,
                color = orderInfo.statusColor
            )
        }
    }
}