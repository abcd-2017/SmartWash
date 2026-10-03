package com.smartwash.ui.page.index

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.common.ui.components.AppInfoDialog
import androidx.compose.material3.Icon
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.ui.page.HomePageConstant
import com.smartwash.ui.page.PageConstant
import com.smartwash.feature.coupon.api.CouponRoute
import com.smartwash.feature.laundry.api.LaundryRoute
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.common.model.RequestState
import com.smartwash.common.utils.pressable

@Composable
fun IndexPage(
    pageNavController: NavHostController,
    navController: NavHostController,
    indexViewModel: IndexViewModel = hiltViewModel(),
) {
    var showAlertDialog by remember { mutableStateOf(false) }
    val userInfoStatus by indexViewModel.userInfoStatus.collectAsState()
    val userInfo by indexViewModel.userInfo.collectAsState()
    val orderList by indexViewModel.orderList.collectAsState()

    LaunchedEffect(pageNavController.currentBackStackEntry) {
        indexViewModel.getInfoData()
    }

    val errorContext = LocalContext.current
    LaunchedEffect(userInfoStatus) {
        if (userInfoStatus is RequestState.Error) {
            Toast.makeText(
                errorContext,
                (userInfoStatus as RequestState.Error).getMessage(errorContext),
                Toast.LENGTH_SHORT
            ).show()
            indexViewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (userInfoStatus is RequestState.Loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    StatusHeroCard(
                        orderList = orderList,
                        onOrderClick = { orderId ->
                            navController.navigate("${OrderRoute.OrderDetail.text}/$orderId")
                        }
                    )
                }

                item {
                    AccountDataCard(
                        balance = userInfo?.balance ?: 0f,
                        couponCount = userInfo?.couponCount ?: 0,
                        orderCount = userInfo?.orderCount ?: 0,
                        onRechargeClick = {
                            navController.navigate(PaymentRoute.Recharge.text)
                        },
                        onCouponClick = {
                            navController.navigate(CouponRoute.Coupon.text)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    Text(
                        text = stringResource(R.string.laundry_service),
                        style = AppTextStyles.SectionTitle,
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ServiceGrid(
                        onBookingClick = {
                            navController.navigate(LaundryRoute.Laundry.text) {
                                popUpTo(pageNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onPickupClick = {
                            navController.navigate(PageConstant.Pickup.text)
                        },
                        onCouponClick = {
                            navController.navigate(CouponRoute.Coupon.text)
                        },
                        onToolboxClick = {
                            pageNavController.navigate(HomePageConstant.Service.text)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.pagePadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.in_progress_orders),
                            style = AppTextStyles.SectionTitle
                        )
                        TextButton(onClick = {
                            navController.navigate("${OrderRoute.Order.text}/${OrderStatus.WASHING.status}")
                        }) {
                            Text(
                                stringResource(R.string.view_all),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (orderList.isNotEmpty()) {
                    item {
                        OrderListCard(
                            orderList = orderList,
                            onOrderClick = { orderId ->
                                navController.navigate("${OrderRoute.OrderDetail.text}/$orderId")
                            }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_ongoing_orders),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.colorScheme.textSecondary
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    ServiceTips()
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                }
            }
        }
    }

    if (showAlertDialog) {
        AppInfoDialog(
            message = stringResource(R.string.change_school_contact_service),
            onDismiss = { showAlertDialog = false }
        )
    }
}

/**
 * 首页 Hero 卡 — 品牌渐变底 + 真实订单状态驱动（规范 v3 §3.1）。
 * 显示当前订单状态、进度条，全页唯一视觉锚点。
 */
@Composable
private fun StatusHeroCard(
    orderList: List<OrderBrief>,
    onOrderClick: (Long) -> Unit,
) {
    val currentOrder = orderList.firstOrNull()
    val orderStatus = currentOrder?.let { OrderStatus.fromStatus(it.status) }

    // 品牌渐变 135° #2D9B6A → #1E8C5C（规范 §2.1 brandGradient，仅用于 hero）
    val gradient = Brush.linearGradient(
        colors = listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(AppDimens.radiusXl))
            .background(gradient)
            .padding(22.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 滚筒圆：同心环 + 水波线
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, Color.White.copy(alpha = 0.32f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalLaundryService,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = orderStatus?.descriptionRes?.let { stringResource(it) } ?: "暂无订单",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = currentOrder?.let { "预计 15:00 完成 · 剩余约 40 分钟" } ?: "点击下方按钮开始预约",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceXl))

            // 进度条
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val steps = listOf(
                    stringResource(R.string.step_received),
                    stringResource(R.string.step_washing),
                    stringResource(R.string.step_dried),
                    stringResource(R.string.step_pickup)
                )
                val currentStep = when (orderStatus) {
                    OrderStatus.WASHING -> 1
                    OrderStatus.DRIED -> 2
                    OrderStatus.READY_FOR_PICKUP -> 3
                    else -> 0
                }
                steps.forEachIndexed { index, _ ->
                    val isActive = index <= currentStep
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isActive) Color.White else Color.White.copy(alpha = 0.32f))
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (index < currentStep) Color.White else Color.White.copy(alpha = 0.28f))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(9.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("已接单", "清洗中", "烘干中", "待取件").forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.weight(1f),
                        textAlign = when (index) {
                            0 -> androidx.compose.ui.text.style.TextAlign.Start
                            3 -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        }
                    )
                }
            }
        }
    }
}

/**
 * 账户数据卡 — 白底 + 1px 描边 + 轻阴影（规范 v3 §3.1）。
 * 余额是主角（大字），优惠券和累计订单是小字。
 */
@Composable
private fun AccountDataCard(
    balance: Float,
    couponCount: Int,
    orderCount: Int,
    onRechargeClick: () -> Unit,
    onCouponClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = 16.dp),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline),
        shadowElevation = AppElevation.level1
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 余额（主角）
            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.account_balance_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.currency_format, String.format("%.2f", balance)),
                    style = AppTextStyles.DataLarge,
                    color = AppColors.colorScheme.primaryDark
                )
            }
            // 分隔线
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(42.dp)
                    .background(AppColors.colorScheme.hairline)
            )
            // 优惠券
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$couponCount",
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = stringResource(R.string.home_available_coupons),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textTertiary
                )
            }
            // 分隔线
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(42.dp)
                    .background(AppColors.colorScheme.hairline)
            )
            // 累计订单
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(18.dp, 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$orderCount",
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = stringResource(R.string.home_total_orders),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textTertiary
                )
            }
        }
    }
}

@Composable
private fun ServiceGrid(
    onBookingClick: () -> Unit,
    onPickupClick: () -> Unit,
    onCouponClick: () -> Unit,
    onToolboxClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalLaundryService,
            label = stringResource(R.string.service_booking),
            containerColor = AppColors.colorScheme.iconContainerGreen,
            iconTint = AppColors.colorScheme.primary,
            onClick = onBookingClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalMall,
            label = stringResource(R.string.service_pickup),
            containerColor = AppColors.colorScheme.iconContainerBlue,
            iconTint = AppColors.colorScheme.water,
            onClick = onPickupClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalOffer,
            label = stringResource(R.string.service_coupon),
            containerColor = AppColors.colorScheme.iconContainerOrange,
            iconTint = AppColors.colorScheme.primaryDark,
            onClick = onCouponClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Build,
            label = stringResource(R.string.service_toolbox),
            containerColor = AppColors.colorScheme.iconContainerPurple,
            iconTint = AppColors.colorScheme.textSecondary,
            onClick = onToolboxClick
        )
    }
}

@Composable
private fun ServiceEntry(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconBox(
            icon = icon,
            size = 44.dp,
            iconSize = 22.dp,
            containerColor = containerColor,
            iconTint = iconTint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textTertiary
        )
    }
}

/**
 * 进行中订单列表 — 白底卡片 + 1px 描边 + 轻阴影（规范 v3 §3.1）。
 * 36dp 图标容器 + 等宽价格。
 */
@Composable
private fun OrderListCard(
    orderList: List<OrderBrief>,
    onOrderClick: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline),
        shadowElevation = AppElevation.level1
    ) {
        Column {
            orderList.forEachIndexed { index, order ->
                OrderListItem(
                    orderVo = order,
                    onClick = { onOrderClick(order.orderId) }
                )
                if (index < orderList.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.5.dp)
                            .background(AppColors.colorScheme.hairline)
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderListItem(
    orderVo: OrderBrief,
    onClick: () -> Unit,
) {
    val orderStatus = OrderStatus.fromStatus(orderVo.status)
    val statusText = orderStatus?.descriptionRes?.let { stringResource(it) } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(
            icon = Icons.Default.LocalLaundryService,
            size = 36.dp,
            iconSize = 18.dp,
            containerColor = AppColors.colorScheme.iconContainerGreen,
            iconTint = AppColors.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.order_no_format, orderVo.orderNo),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textTertiary
            )
        }
        Text(
            text = stringResource(R.string.currency_format, orderVo.payPrice.toString()),
            style = AppTextStyles.AmountMedium,
            color = AppColors.colorScheme.primary
        )
    }
}

@Composable
private fun ServiceTips() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(
            icon = Icons.Default.Info,
            size = 32.dp,
            iconSize = 16.dp,
            containerColor = AppColors.colorScheme.iconContainerTeal,
            iconTint = AppColors.colorScheme.textTertiary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.service_tips),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textTertiary
        )
    }
}
