package com.smartwash.ui.page.detail

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.InfoSection
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.common.model.RequestState
import com.smartwash.common.utils.pressScale

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

    // 错误提示放 LaunchedEffect，禁止组合期直接弹 Toast
    val context = LocalContext.current
    LaunchedEffect(getOrderDetailState) {
        if (getOrderDetailState is RequestState.Error) {
            Toast.makeText(
                context,
                (getOrderDetailState as RequestState.Error).getMessage(context),
                Toast.LENGTH_SHORT
            ).show()
            orderDetailViewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (getOrderDetailState is RequestState.Loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PageHeader(title = stringResource(R.string.order_detail), onBack = { navController.navigateUp() })

            Column(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                Spacer(modifier = Modifier.height(8.dp))

                // 状态卡片 — 全宽背景
                StatusCard(status = OrderStatus.fromStatus(orderInfo?.status ?: "-1"))

                Spacer(modifier = Modifier.height(24.dp))

                // 进度时间线（规范 §3.4）
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.radiusLg),
                    color = AppColors.colorScheme.surface,
                    shadowElevation = AppElevation.level1,
                    border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.progress_timeline),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(bottom = 18.dp)
                        )
                        ProgressTimeline(currentStatus = OrderStatus.fromStatus(orderInfo?.status ?: "-1"))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 订单信息
                InfoSection(
                    title = stringResource(R.string.order_info),
                    icon = Icons.Rounded.Receipt
                ) {
                    InfoRow(stringResource(R.string.order_number), orderInfo?.orderNo ?: "")
                    InfoRow(stringResource(R.string.package_type), orderInfo?.laundryPackageVo?.itemName ?: "")
                    InfoRow(
                        stringResource(R.string.actual_payment),
                        stringResource(R.string.currency_format, (orderInfo?.payPrice ?: 0f).toString()),
                        valueColor = AppColors.colorScheme.primary,
                        valueStyle = AppTextStyles.AmountMedium
                    )
                    InfoRow(stringResource(R.string.school_name), orderInfo?.schoolsVo?.schoolName ?: "")
                    InfoRow(stringResource(R.string.address), orderInfo?.schoolsVo?.location ?: "")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 底部按钮（规范 §3.3）
                val interactionSource1 = remember { MutableInteractionSource() }
                val interactionSource2 = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { /* 联系客服 */ },
                        interactionSource = interactionSource1,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .pressScale(interactionSource1, 0.97f),
                        shape = RoundedCornerShape(AppDimens.buttonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.colorScheme.surfaceVariant,
                            contentColor = AppColors.colorScheme.textPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.contact_service),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = { /* 查看取件码 */ },
                        interactionSource = interactionSource2,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .pressScale(interactionSource2, 0.97f),
                        shape = RoundedCornerShape(AppDimens.buttonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.view_pickup_code),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        }
    }
}

/**
 * 进度时间线 — 规范 §3.4 竖向时间线
 * 当前状态节点高亮（now），已完成节点品牌色（done），未完成节点灰色
 */
@Composable
fun ProgressTimeline(currentStatus: OrderStatus?) {
    // 定义时间线节点（按订单流程顺序）
    data class TimelineNode(
        val status: OrderStatus,
        val timeText: String,
    )

    val nodes = listOf(
        TimelineNode(OrderStatus.PENDING_PAYMENT, "14:20 · 系统确认订单"),
        TimelineNode(OrderStatus.PENDING_SHIPMENT, "14:05 · 衣物已送达"),
        TimelineNode(OrderStatus.WASHING, "14:25 · 滚筒工作中"),
        TimelineNode(OrderStatus.READY_FOR_PICKUP, "预计 15:00 · 3 号柜 12 格"),
        TimelineNode(OrderStatus.COMPLETED, "预计 15:30 · 取件完成"),
    )

    // 确定当前节点索引
    val currentIndex = nodes.indexOfFirst { it.status == currentStatus }.coerceAtLeast(0)

    Column(modifier = Modifier.padding(start = 26.dp)) {
        nodes.forEachIndexed { index, node ->
            val isDone = index < currentIndex
            val isCurrent = index == currentIndex
            val isLast = index == nodes.lastIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isLast) 0.dp else 18.dp)
            ) {
                // 节点圆点
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> AppColors.colorScheme.primary
                                isDone -> AppColors.colorScheme.primary
                                else -> AppColors.colorScheme.outline
                            }
                        )
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(node.status.descriptionRes),
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCurrent) AppColors.colorScheme.primaryDark
                        else if (isDone) AppColors.colorScheme.textPrimary
                        else AppColors.colorScheme.textTertiary
                    )
                    Text(
                        text = node.timeText,
                        fontSize = 11.sp,
                        color = AppColors.colorScheme.textTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(status: OrderStatus?) {
    data class OrderStatusInfo(
        @androidx.annotation.StringRes val statusTextRes: Int,
        val statusColor: Color,
        val icon: ImageVector?,
        @androidx.annotation.StringRes val estimatedTimeRes: Int,
    )

    val info: OrderStatusInfo = when (status) {
        OrderStatus.PENDING_PAYMENT -> OrderStatusInfo(
            R.string.pending_payment, AppColors.colorScheme.warning, Icons.Rounded.HourglassEmpty, R.string.please_pay_soon
        )
        OrderStatus.WASHING -> OrderStatusInfo(
            OrderStatus.WASHING.descriptionRes, AppColors.colorScheme.primary, null, R.string.please_pay_soon
        )
        OrderStatus.PENDING_SHIPMENT -> OrderStatusInfo(
            R.string.pending_shipment, AppColors.colorScheme.primary, Icons.Rounded.LocalLaundryService, R.string.please_ship_soon
        )
        OrderStatus.READY_FOR_PICKUP -> OrderStatusInfo(
            R.string.pending_pickup, AppColors.colorScheme.primary, Icons.Rounded.Toys, R.string.clothes_in_locker
        )
        OrderStatus.COMPLETED -> OrderStatusInfo(
            R.string.completed, AppColors.colorScheme.success, Icons.Rounded.CheckCircle, R.string.completion_time
        )
        OrderStatus.CANCELED -> OrderStatusInfo(
            R.string.cancelled, AppColors.colorScheme.error, Icons.Rounded.Cancel, R.string.cancelled
        )
        else -> OrderStatusInfo(R.string.dash, MaterialTheme.colorScheme.onBackground, Icons.Rounded.HourglassEmpty, R.string.dash)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level2,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == OrderStatus.WASHING) {
                // 清洗中：使用 DrumIcon 动效组件（规范 §5），动画由真实状态触发
                DrumIcon(
                    state = OrderStatus.WASHING,
                    tint = info.statusColor,
                    drumSize = 52.dp
                )
            } else {
                IconBox(
                    icon = info.icon ?: Icons.Rounded.HourglassEmpty,
                    size = 52.dp,
                    iconSize = 26.dp,
                    containerColor = info.statusColor.copy(alpha = 0.15f),
                    iconTint = info.statusColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(info.statusTextRes),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = info.statusColor,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(info.estimatedTimeRes),
                    fontSize = 12.sp,
                    color = AppColors.colorScheme.textSecondary
                )
            }
        }
    }
}
