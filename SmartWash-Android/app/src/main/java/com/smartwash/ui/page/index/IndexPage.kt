package com.smartwash.ui.page.index

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.ui.page.HomePageConstant
import com.smartwash.ui.page.PageConstant
import com.smartwash.feature.coupon.api.CouponRoute
import com.smartwash.feature.laundry.api.LaundryRoute
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.order.api.model.OrderStatus
import com.smartwash.common.model.RequestState
import com.smartwash.common.utils.pressable
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
                    HomeHeroCard(
                        schoolName = userInfo?.school?.schoolName ?: "",
                        balance = userInfo?.balance ?: 0f,
                        onAvatarClick = {
                            pageNavController.navigate(HomePageConstant.UserInfo.text) {
                                popUpTo(pageNavController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
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
                    items(
                        count = orderList.size,
                        key = { index -> orderList[index].orderId }
                    ) { index ->
                        OrderListItem(
                            orderVo = orderList[index],
                            modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                        ) {
                            navController.navigate("${OrderRoute.OrderDetail.text}/${orderList[index].orderId}")
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
 * 首页 Hero 卡 — 品牌渐变底 + 白字 + 白色 CTA 胶囊（规范 §3.1 Hero 卡）。
 * 全页唯一视觉锚点：36sp 余额大字（tabular numbers）。
 */
@Composable
private fun HomeHeroCard(
    schoolName: String,
    balance: Float,
    onAvatarClick: () -> Unit,
    onRechargeClick: () -> Unit,
    onCouponClick: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> stringResource(R.string.home_greeting_morning)
        hour < 18 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }

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
            .clip(RoundedCornerShape(AppDimens.radiusXl))
            .background(gradient)
            .padding(AppDimens.cardPadding)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting + stringResource(R.string.home_greeting_suffix),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = schoolName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(Color.White.copy(alpha = 0.2f))
                        .pressable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceLg))

            Text(
                text = stringResource(R.string.account_balance_label),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = stringResource(R.string.currency_format, String.format("%.2f", balance)),
                style = AppTextStyles.DataLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
                WhiteCapsuleButton(
                    text = stringResource(R.string.go_recharge),
                    onClick = onRechargeClick
                )
                WhiteCapsuleButton(
                    text = stringResource(R.string.home_available_coupons),
                    onClick = onCouponClick
                )
            }
        }
    }
}

/** Hero 卡内白色 CTA 胶囊（规范 §3.1 Hero 卡 — 白色 CTA 胶囊） */
@Composable
private fun WhiteCapsuleButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(Color.White)
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.colorScheme.primary
        )
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
        horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
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
            containerColor = AppColors.colorScheme.iconContainerOrange,
            iconTint = AppColors.colorScheme.onWarningContainer,
            onClick = onPickupClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalOffer,
            label = stringResource(R.string.service_coupon),
            containerColor = AppColors.colorScheme.iconContainerTeal,
            iconTint = AppColors.colorScheme.primaryDark,
            onClick = onCouponClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Build,
            label = stringResource(R.string.service_toolbox),
            containerColor = AppColors.colorScheme.iconContainerBlue,
            iconTint = AppColors.colorScheme.water,
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
            containerColor = containerColor,
            iconTint = iconTint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textPrimary
        )
    }
}

@Composable
private fun OrderListItem(
    orderVo: OrderBrief,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val orderStatus = OrderStatus.fromStatus(orderVo.status)
    val statusText = orderStatus?.descriptionRes?.let { stringResource(it) } ?: ""

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(
                icon = Icons.Default.LocalLaundryService,
                size = 36.dp,
                iconSize = 18.dp,
                containerColor = AppColors.colorScheme.iconContainerGreen,
                iconTint = AppColors.colorScheme.primary
            )
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
            Text(
                text = stringResource(R.string.currency_format, orderVo.payPrice.toString()),
                style = AppTextStyles.AmountMedium,
                color = AppColors.colorScheme.primary
            )
        }
        HorizontalDivider(color = AppColors.colorScheme.divider)
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
