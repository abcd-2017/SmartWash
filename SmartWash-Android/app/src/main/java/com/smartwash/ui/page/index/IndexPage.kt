package com.smartwash.ui.page.index

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.smartwash.common.ui.components.DrumMark
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.components.LoadingState
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
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import androidx.compose.ui.text.style.TextAlign

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
                        // 行内「查看全部」— 12sp SemiBold primary，无 ripple，48dp 热区（D-I7）
                        Text(
                            text = stringResource(R.string.view_all),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AppColors.colorScheme.primary,
                            modifier = Modifier
                                .pressable(
                                    onClick = rememberDebouncedClick {
                                        navController.navigate("${OrderRoute.Order.text}/${OrderStatus.WASHING.status}")
                                    },
                                    debounce = false,
                                )
                                .padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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
 * 订单状态 → hero 滚筒进度（0..1），8 态映射自旧版恢复。
 */
private fun orderProgress(status: String): Float = when (status) {
    "0" -> 0.10f
    "1" -> 0.25f
    "2" -> 0.35f
    "3" -> 0.55f
    "4" -> 0.70f
    "5" -> 0.85f
    "6" -> 0.95f
    "7" -> 1.00f
    else -> 0f
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
                // 滚筒圆 — 规范 §5 母题：同心环 + 进度弧 + 单条水波（D-I1）
                DrumMark(
                    size = 64.dp,
                    progress = currentOrder?.let { orderProgress(it.status) },
                    tint = Color.White,
                    trackTint = Color.White.copy(alpha = 0.32f),
                    innerRingTint = Color.White.copy(alpha = 0.45f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = orderStatus?.descriptionRes?.let { stringResource(it) }
                            ?: stringResource(R.string.home_hero_no_order),
                        style = AppTextStyles.StatusLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    if (currentOrder != null) {
                        Text(
                            text = stringResource(R.string.home_hero_order_no, currentOrder.orderNo),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.home_hero_no_order_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceXl))

            // 步骤标签 — stringResource 资源化，进度点行与标签行共用（D-I2 硬编码清理）
            val steps = listOf(
                stringResource(R.string.step_received),
                stringResource(R.string.step_washing),
                stringResource(R.string.step_dried),
                stringResource(R.string.step_pickup)
            )

            // 进度条
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                steps.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
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
            // 余额（主角）——按可用宽度分档缩字号，窄屏不折行不溢出
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1.35f)
                    .pressable(onClick = rememberDebouncedClick(onClick = onRechargeClick), debounce = false)
                    .padding(top = 18.dp, bottom = 18.dp, start = 20.dp, end = 20.dp)
            ) {
                val availableWidth = maxWidth
                Column {
                    Text(
                        text = stringResource(R.string.account_balance_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.currency_format, String.format("%.2f", balance)),
                        style = when {
                            availableWidth >= 160.dp -> AppTextStyles.DataLarge
                            availableWidth >= 130.dp -> AppTextStyles.DataLarge.copy(
                                fontSize = 30.sp, lineHeight = 38.sp
                            )
                            else -> AppTextStyles.DataLarge.copy(
                                fontSize = 24.sp, lineHeight = 32.sp
                            )
                        },
                        color = AppColors.colorScheme.primaryDark,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$couponCount",
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.textPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = stringResource(R.string.home_available_coupons),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    softWrap = false
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
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$orderCount",
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.textPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = stringResource(R.string.home_total_orders),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ServiceGrid(
    onBookingClick: () -> Unit,
    onPickupClick: () -> Unit,
    onRechargeClick: () -> Unit,
    onCouponClick: () -> Unit,
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
            iconTint = AppColors.colorScheme.iconForegroundGreen,
            onClick = onBookingClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalMall,
            label = stringResource(R.string.service_pickup),
            containerColor = AppColors.colorScheme.iconContainerOrange,
            iconTint = AppColors.colorScheme.iconForegroundOrange,
            onClick = onPickupClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.AccountBalanceWallet,
            label = stringResource(R.string.service_recharge),
            containerColor = AppColors.colorScheme.iconContainerTeal,
            iconTint = AppColors.colorScheme.iconForegroundTeal,
            onClick = onRechargeClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalOffer,
            label = stringResource(R.string.service_coupon),
            containerColor = AppColors.colorScheme.iconContainerBlue,
            iconTint = AppColors.colorScheme.iconForegroundBlue,
            onClick = onCouponClick
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
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary
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
        // 图标容器色按状态语义（D-I8）：清洗中→绿，其余→橙（进行中待办语义）
        IconBox(
            icon = Icons.Default.LocalLaundryService,
            size = 36.dp,
            iconSize = 18.dp,
            containerColor = if (orderStatus == OrderStatus.WASHING) {
                AppColors.colorScheme.iconContainerGreen
            } else {
                AppColors.colorScheme.iconContainerOrange
            },
            iconTint = if (orderStatus == OrderStatus.WASHING) {
                AppColors.colorScheme.iconForegroundGreen
            } else {
                AppColors.colorScheme.iconForegroundOrange
            }
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
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.textSecondary
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
    // 服务须知 — 居中纯文字（D-I9）
    Text(
        text = stringResource(R.string.service_tips),
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        textAlign = TextAlign.Center
    )
}
