package com.smartwash.ui.page.index

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.smartwash.network.vo.order.OrderVo
import com.smartwash.ui.page.HomePageConstant
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.RequestState
import java.util.Calendar

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
    when (userInfoStatus) {
        is RequestState.Error -> {
            val context = LocalContext.current
            Toast.makeText(
                context,
                (userInfoStatus as RequestState.Error).getMessage(context),
                Toast.LENGTH_SHORT
            ).show()
            indexViewModel.resetState()
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // 问候头部
            item {
                GreetingHeader(
                    schoolName = userInfo?.schoolVo?.schoolName ?: "",
                    onAvatarClick = {
                        pageNavController.navigate(HomePageConstant.UserInfo.text) {
                            popUpTo(pageNavController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // 账户概览卡
            item {
                Spacer(modifier = Modifier.height(20.dp))
                AccountOverviewCard(
                    balance = userInfo?.balance ?: 0f,
                    onRechargeClick = {
                        navController.navigate(PageConstant.Recharge.text)
                    }
                )
            }

            // 服务网格
            item {
                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                Text(
                    text = stringResource(R.string.laundry_service),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(12.dp))
                ServiceGrid(
                    onBookingClick = {
                        navController.navigate(PageConstant.Laundry.text) {
                            popUpTo(pageNavController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onPickupClick = {
                        navController.navigate(PageConstant.Pickup.text)
                    },
                    onCouponClick = {
                        navController.navigate(PageConstant.Coupon.text)
                    }
                )
            }

            // 进行中订单
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
                        style = MaterialTheme.typography.headlineMedium
                    )
                    TextButton(onClick = {
                        navController.navigate("${PageConstant.Order.text}/${OrderStatus.WASHING.status}")
                    }) {
                        Text(
                            stringResource(R.string.view_all),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (orderList.isNotEmpty()) {
                items(orderList.size) { index ->
                    OrderCardWithProgress(
                        orderVo = orderList[index],
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    ) {
                        navController.navigate("${PageConstant.OrderDetail.text}/${orderList[index].orderId}")
                    }
                    if (index < orderList.size - 1) {
                        Spacer(modifier = Modifier.height(AppDimens.cardSpacing))
                    }
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

            // 服务须知
            item {
                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                ServiceTips()
                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            }
        }
    }

    if (showAlertDialog) {
        AlertDialog(
            onDismissRequest = { showAlertDialog = false },
            text = { Text(stringResource(R.string.change_school_contact_service)) },
            confirmButton = {
                TextButton(onClick = { showAlertDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

@Composable
private fun GreetingHeader(
    schoolName: String,
    onAvatarClick: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> stringResource(R.string.home_greeting_morning)
        hour < 18 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.pagePadding,
                end = AppDimens.pagePadding,
                top = 16.dp,
                bottom = 8.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting + stringResource(R.string.home_greeting_suffix),
                style = MaterialTheme.typography.displayLarge,
                color = AppColors.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = schoolName,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppColors.colorScheme.primaryLight)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AccountOverviewCard(
    balance: Float,
    onRechargeClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark)),
                    shape = RoundedCornerShape(AppDimens.cardRadius)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.account_balance_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.currency_format, String.format("%.2f", balance)),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.home_available_coupons),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.clickable(onClick = onRechargeClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.go_recharge),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceGrid(
    onBookingClick: () -> Unit,
    onPickupClick: () -> Unit,
    onCouponClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalLaundryService,
            label = stringResource(R.string.service_booking),
            onClick = onBookingClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalMall,
            label = stringResource(R.string.service_pickup),
            onClick = onPickupClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalOffer,
            label = stringResource(R.string.service_coupon),
            onClick = onCouponClick
        )
    }
}

@Composable
private fun ServiceEntry(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.smallCardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.colorScheme.primaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = AppColors.colorScheme.primary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.textPrimary
            )
        }
    }
}

@Composable
private fun OrderCardWithProgress(
    orderVo: OrderVo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val orderStatus = OrderStatus.fromStatus(orderVo.status)
    val progress = orderProgress(orderVo.status)
    val statusText = orderStatus?.descriptionRes?.let { stringResource(it) } ?: ""

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                            .background(AppColors.colorScheme.primaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalLaundryService,
                            contentDescription = null,
                            tint = AppColors.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.order_no_format, orderVo.orderNo),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.currency_format, orderVo.payPrice.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AppColors.colorScheme.primary,
                trackColor = AppColors.colorScheme.primaryLight,
            )
        }
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
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = AppColors.colorScheme.textTertiary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.service_tips),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textTertiary
        )
    }
}

private fun orderProgress(status: String): Float {
    return when (status) {
        "0" -> 0.10f   // PENDING_PAYMENT
        "1" -> 0.25f   // PENDING_SHIPMENT
        "2" -> 0.35f   // RECEIVED
        "3" -> 0.55f   // WASHING
        "4" -> 0.70f   // DRIED
        "5" -> 0.85f   // IN_DELIVERY
        "6" -> 0.95f   // READY_FOR_PICKUP
        "7" -> 1.00f   // COMPLETED
        else -> 0f
    }
}
