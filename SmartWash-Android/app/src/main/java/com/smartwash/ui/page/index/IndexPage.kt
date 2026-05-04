package com.smartwash.ui.page.index

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.network.vo.order.OrderVo
import com.smartwash.ui.page.HomePageConstant
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.home.ServiceCard
import com.smartwash.ui.page.home.ServiceCardOutlined
import com.smartwash.ui.page.home.StatusCard
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.Background
import com.smartwash.ui.theme.Primary
import com.smartwash.ui.theme.PrimaryLight
import com.smartwash.ui.theme.TextSecondary
import com.smartwash.utils.OrderStatus
import com.smartwash.utils.RequestState

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
                context.getString((userInfoStatus as RequestState.Error).messageResId),
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
            // 自定义顶部区域
            item {
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
                            text = stringResource(R.string.smart_laundry),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
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
                                text = userInfo?.schoolVo?.schoolName ?: "",
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
                            .clickable {
                                pageNavController.navigate(HomePageConstant.UserInfo.text) {
                                    popUpTo(pageNavController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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

            // 快速服务区
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.quick_service),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ServiceCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalLaundryService,
                        title = stringResource(R.string.book_now),
                        subtitle = stringResource(R.string.price_from),
                        onClick = {
                            navController.navigate(PageConstant.Laundry.text) {
                                popUpTo(pageNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    ServiceCardOutlined(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalMall,
                        title = stringResource(R.string.retrieve_clothes),
                        subtitle = stringResource(R.string.scan_to_pickup),
                        onClick = {
                            navController.navigate(PageConstant.Pickup.text)
                        }
                    )
                }
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
                        text = stringResource(R.string.in_progress),
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
                    IndexOrderItem(orderList[index], modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
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

            // 服务状态
            item {
                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                Text(
                    text = stringResource(R.string.service_status),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .padding(horizontal = AppDimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = Icons.Default.AccessTime,
                        title = stringResource(R.string.business_hours),
                        content = "7:00-22:00"
                    )
                    StatusCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = Icons.Default.LocalShipping,
                        title = stringResource(R.string.delivery_status),
                        content = stringResource(R.string.normal_operation)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
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
private fun IndexOrderItem(
    orderVo: OrderVo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = stringResource(R.string.factory_washing),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.order_no_format, orderVo.orderNo),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
            Text(
                text = stringResource(R.string.currency_format, orderVo.totalPrice.toString()),
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.colorScheme.primary
            )
        }
    }
}
