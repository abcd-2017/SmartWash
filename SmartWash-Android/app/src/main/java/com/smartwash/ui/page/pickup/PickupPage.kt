package com.smartwash.ui.page.pickup

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
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.R
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.EmptyState
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.Background
import com.smartwash.ui.theme.Divider
import com.smartwash.ui.theme.Primary
import com.smartwash.ui.theme.PrimaryLight
import com.smartwash.ui.theme.TextSecondary
import com.smartwash.utils.PickupDeliveryType

@Composable
fun PickupPage(
    navController: NavHostController,
    pickupViewModel: PickupViewModel = hiltViewModel(),
) {
    val orderList = pickupViewModel.pagingFlow.collectAsLazyPagingItems()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.pickup), onBack = { navController.navigateUp() })

            if (orderList.itemCount > 0) {
                // 学校信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = AppColors.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = orderList[0]?.schoolsVo?.schoolName ?: "",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    items(orderList.itemCount) { i ->
                        orderList[i]?.let {
                            PickupOrderCard(
                                order = it,
                                onClick = { navController.navigate("${PageConstant.PickupDelivery.text}/${orderList[i]?.orderId ?: -1}/${PickupDeliveryType.PICKUP.type}") }
                            )
                        }
                    }
                }
            } else {
                EmptyState(
                    icon = Icons.Default.LocalLaundryService,
                    message = stringResource(R.string.no_pickup_orders)
                )
            }
        }
    }
}

@Composable
private fun PickupOrderCard(
    order: OrderInfo,
    onClick: () -> Unit,
) {
    val pickupCode = order.pickupCode.let { it.split(":")[2] } ?: "error"
    AppCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.order_no_format, order.orderNo),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                    Text(
                        text = stringResource(R.string.locker_label_format, "${order.lockersVo.lockerNumber}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.currency_format, "${order.totalPrice}"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(AppColors.colorScheme.divider))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.pickup_code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textSecondary
                )
                Text(
                    text = pickupCode,
                    style = MaterialTheme.typography.displaySmall,
                    color = AppColors.colorScheme.primary
                )
            }
        }
    }
}
