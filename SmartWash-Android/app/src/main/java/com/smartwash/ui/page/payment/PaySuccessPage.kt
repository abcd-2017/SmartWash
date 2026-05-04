package com.smartwash.ui.page.payment

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.ui.common.AppButton
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.InfoRow
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.detail.OrderDetailViewModel
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.IconBox
import com.smartwash.utils.PickupDeliveryType
import com.smartwash.utils.RequestState

@Composable
fun PaySuccessPage(
    navController: NavHostController,
    orderId: Long,
    orderDetailViewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val getOrderDetailState by orderDetailViewModel.getOrderInfoDetail.collectAsState()
    val orderInfo by orderDetailViewModel.orderInfo.collectAsState()
    val context = LocalContext.current

    if (orderId != -1L) {
        LaunchedEffect(Unit) {
            orderDetailViewModel.getOrderDetail(orderId)
        }
    }

    when (getOrderDetailState) {
        is RequestState.Error -> {
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
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.pay_success), onBack = { navController.navigateUp() })

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.pagePadding),
            ) {
                // 成功庆祝区域
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.cardRadius),
                    color = AppColors.colorScheme.primaryLight,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(AppColors.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.pay_success),
                                modifier = Modifier.size(40.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.pay_success),
                            style = MaterialTheme.typography.displayLarge,
                            color = AppColors.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.thanks_for_using),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 订单信息卡
                Text(
                    text = stringResource(R.string.order_info),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(stringResource(R.string.order_number), orderInfo?.orderNo ?: "")
                        InfoRow(stringResource(R.string.payment_amount), stringResource(R.string.currency_format, "${orderInfo?.payPrice ?: 0}"), valueColor = AppColors.colorScheme.primary)
                        InfoRow(stringResource(R.string.order_time_display), orderInfo?.createdAt ?: "")
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 寄存柜提示卡
                Text(
                    text = stringResource(R.string.locker_info_section),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        IconBox(
                            icon = Icons.Default.LocalShipping,
                            size = 40.dp,
                            iconSize = 20.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.locker_info_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.go_to_locker_format, "${orderInfo?.lockersVo?.lockerNumber ?: -1}"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.locker_process_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 底部按钮
            Column(modifier = Modifier.padding(AppDimens.pagePadding)) {
                AppButton(
                    text = stringResource(R.string.go_ship_now),
                    onClick = {
                        navController.navigate(
                            "${PageConstant.PickupDelivery.text}/${orderInfo?.orderId ?: -1}/${PickupDeliveryType.DELIVERY.type}"
                        ) {
                            navController.navigateUp()
                        }
                    }
                )
            }
        }
    }
}
