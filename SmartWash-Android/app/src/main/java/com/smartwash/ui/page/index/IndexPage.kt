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
import androidx.compose.material.icons.filled.ChevronRight
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
import com.smartwash.common.ui.theme.ServiceLuxury
import com.smartwash.common.ui.theme.ServicePress
import com.smartwash.common.ui.theme.ServiceWash
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
                    GreetingHeader(
                        schoolName = userInfo?.school?.schoolName ?: "",
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

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    AccountSummaryRow(
                        balance = userInfo?.balance ?: 0f,
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
                        style = MaterialTheme.typography.headlineMedium,
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
                            style = MaterialTheme.typography.headlineMedium
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
            .padding(horizontal = AppDimens.pagePadding, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting + stringResource(R.string.home_greeting_suffix),
                style = MaterialTheme.typography.headlineMedium,
                color = AppColors.colorScheme.textPrimary
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
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.colorScheme.primaryLight)
                .pressable(onClick = onAvatarClick),
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
private fun AccountSummaryRow(
    balance: Float,
    onRechargeClick: () -> Unit,
    onCouponClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.account_balance_label),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.currency_format, String.format("%.2f", balance)),
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.colorScheme.primary
            )
        }
        Row(
            modifier = Modifier.pressable(onClick = onCouponClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_available_coupons),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AppColors.colorScheme.textSecondary
            )
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
        horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalLaundryService,
            label = stringResource(R.string.service_booking),
            iconColor = ServiceWash,
            onClick = onBookingClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalMall,
            label = stringResource(R.string.service_pickup),
            iconColor = ServiceLuxury,
            onClick = onPickupClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalOffer,
            label = stringResource(R.string.service_coupon),
            iconColor = ServicePress,
            onClick = onCouponClick
        )
        ServiceEntry(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Build,
            label = stringResource(R.string.service_toolbox),
            iconColor = AppColors.colorScheme.primary,
            onClick = onToolboxClick
        )
    }
}

@Composable
private fun ServiceEntry(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    iconColor: Color = AppColors.colorScheme.primary,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .pressable(onClick = onClick, scaleFactor = 0.95f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = iconColor
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
                .pressable(onClick = onClick, alphaFactor = 0.95f)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalLaundryService,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = AppColors.colorScheme.primary
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
                style = MaterialTheme.typography.titleMedium,
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
