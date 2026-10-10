package com.smartwash.ui.page.pickup

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.utils.pressable
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.R
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.ui.page.PageConstant
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.Background
import com.smartwash.common.ui.theme.Divider
import com.smartwash.common.ui.theme.Primary
import com.smartwash.common.ui.theme.PrimaryLight
import com.smartwash.common.ui.theme.TextSecondary
import com.smartwash.feature.order.api.PickupDeliveryType

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
                    IconBox(
                        icon = Icons.Default.School,
                        size = 32.dp,
                        iconSize = 16.dp,
                        containerColor = AppColors.colorScheme.iconContainerBlue,
                        iconTint = AppColors.colorScheme.iconForegroundBlue
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
                                onClick = { navController.navigate("${OrderRoute.PickupDelivery.text}/${orderList[i]?.orderId ?: -1}/${PickupDeliveryType.PICKUP.type}") }
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
    val pickupCode = order.pickupCode?.split(":")?.getOrNull(2) ?: ""
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    AppCard(onClick = onClick) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.order_no_format, order.orderNo),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary
                    )
                    Text(
                        text = stringResource(R.string.locker_label_format, "${order.lockersVo.lockerNumber}"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.currency_format, "${order.totalPrice}"),
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.primaryDark
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(AppColors.colorScheme.divider))
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.pickup_code),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = AppColors.colorScheme.textSecondary
                    )
                )
                Text(
                    text = pickupCode,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.colorScheme.primaryDark,
                        fontFeatureSettings = "tnum",
                        letterSpacing = 0.sp
                    )
                )
            }

            if (pickupCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .pressable(onClick = {
                                clipboardManager.setText(AnnotatedString(pickupCode))
                                Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                            })
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = stringResource(R.string.copy_pickup_code),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
