package com.smartwash.ui.page.detail

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Wash
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.InfoRow
import com.smartwash.ui.common.InfoSection
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.IconBox
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.RequestState

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
            val context = LocalContext.current
            Toast.makeText(
                context,
                context.getString((getOrderDetailState as RequestState.Error).messageResId),
                Toast.LENGTH_SHORT
            ).show()
            orderDetailViewModel.resetState()
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PageHeader(title = stringResource(R.string.order_detail), onBack = { navController.navigateUp() })

            Column(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                Spacer(modifier = Modifier.height(8.dp))

                // 状态卡片 — 全宽背景
                StatusCard(orderStatus = orderInfo?.status ?: "-1")

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 套餐信息 — 最重要的业务信息放前面
                InfoSection(
                    title = stringResource(R.string.package_info),
                    icon = Icons.Rounded.LocalLaundryService
                ) {
                    InfoRow(stringResource(R.string.package_type), orderInfo?.laundryPackageVo?.itemName ?: "")
                    InfoRow(stringResource(R.string.price), "￥${orderInfo?.totalPrice ?: 0}")
                    InfoRow(stringResource(R.string.actual_payment), "￥${orderInfo?.payPrice ?: 0}", valueColor = AppColors.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 学校信息
                InfoSection(
                    title = stringResource(R.string.school_info),
                    icon = Icons.Rounded.School
                ) {
                    InfoRow(stringResource(R.string.school_name), orderInfo?.schoolsVo?.schoolName ?: "")
                    InfoRow(stringResource(R.string.address), orderInfo?.schoolsVo?.location ?: "")
                }

                // 寄存柜信息
                if (orderInfo != null && (orderInfo!!.status == OrderStatus.PENDING_SHIPMENT.status || orderInfo!!.status == OrderStatus.READY_FOR_PICKUP.status)) {
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    InfoSection(
                        title = stringResource(R.string.locker_info),
                        icon = Icons.Rounded.Storage
                    ) {
                        InfoRow(stringResource(R.string.locker_number), "${orderInfo?.lockersVo?.lockerNumber ?: 0}")
                        InfoRow(stringResource(R.string.pickup_code), orderInfo?.pickupCode?.let { it.split(":")[2] } ?: "")
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 订单信息
                InfoSection(
                    title = stringResource(R.string.order_info),
                    icon = Icons.Rounded.Receipt
                ) {
                    InfoRow(stringResource(R.string.order_number), orderInfo?.orderNo ?: "")
                    InfoRow(stringResource(R.string.order_time), orderInfo?.createdAt ?: "")
                    InfoRow(stringResource(R.string.current_status), stringResource(OrderStatus.getDescriptionResByStatus(orderInfo?.status ?: "001")))
                    InfoRow(stringResource(R.string.completion_time), stringResource(R.string.dash))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatusCard(orderStatus: String) {
    data class OrderStatusInfo(
        @androidx.annotation.StringRes val statusTextRes: Int,
        val statusColor: Color,
        val icon: ImageVector,
        @androidx.annotation.StringRes val estimatedTimeRes: Int,
        val bgColor: Color,
    )

    val info: OrderStatusInfo = when (orderStatus) {
        OrderStatus.PENDING_PAYMENT.status -> OrderStatusInfo(
            R.string.pending_payment, AppColors.colorScheme.warning, Icons.Rounded.HourglassEmpty, R.string.please_pay_soon, AppColors.colorScheme.warningContainer
        )
        OrderStatus.WASHING.status -> OrderStatusInfo(
            R.string.order_status_washing, AppColors.colorScheme.primary, Icons.Rounded.Wash, R.string.please_pay_soon, AppColors.colorScheme.primaryLight
        )
        OrderStatus.PENDING_SHIPMENT.status -> OrderStatusInfo(
            R.string.pending_shipment, AppColors.colorScheme.primary, Icons.Rounded.LocalLaundryService, R.string.please_ship_soon, AppColors.colorScheme.primaryLight
        )
        OrderStatus.READY_FOR_PICKUP.status -> OrderStatusInfo(
            R.string.pending_pickup, AppColors.colorScheme.primary, Icons.Rounded.Toys, R.string.clothes_in_locker, AppColors.colorScheme.primaryLight
        )
        OrderStatus.COMPLETED.status -> OrderStatusInfo(
            R.string.completed, AppColors.colorScheme.success, Icons.Rounded.CheckCircle, R.string.completion_time, AppColors.colorScheme.primaryLight
        )
        OrderStatus.CANCELED.status -> OrderStatusInfo(
            R.string.cancelled, AppColors.colorScheme.error, Icons.Rounded.Cancel, R.string.cancelled, AppColors.colorScheme.divider
        )
        else -> OrderStatusInfo(R.string.dash, MaterialTheme.colorScheme.onBackground, Icons.Rounded.HourglassEmpty, R.string.dash, AppColors.colorScheme.divider)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = info.bgColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧大图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(info.statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = info.icon,
                    contentDescription = null,
                    tint = info.statusColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // 右侧文字
            Column {
                Text(
                    text = stringResource(info.statusTextRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = info.statusColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(info.estimatedTimeRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = info.statusColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
