package com.smartwash.ui.page.payment

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.ui.common.AppButton
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.PaymentType
import com.smartwash.utils.RequestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentPage(
    navController: NavHostController,
    orderId: Long?,
    paymentViewModel: PaymentViewModel = hiltViewModel(),
) {
    var showRechargeDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("balance") }
    val current = LocalContext.current

    val orderInfo by paymentViewModel.orderInfo.collectAsState()
    val paymentState by paymentViewModel.paymentState.collectAsState()
    val userCouponList by paymentViewModel.userCouponList.collectAsState()
    val calculationOrderState by paymentViewModel.calculationOrderState.collectAsState()

    var confirmPayShow by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedCoupon by remember { mutableIntStateOf(-1) }

    if (orderId != null) {
        LaunchedEffect(Unit) { paymentViewModel.initData(orderId) }
        LaunchedEffect(navController.currentBackStackEntry) { paymentViewModel.getaUserCoupon(orderId) }
    }

    when (paymentState) {
        is RequestState.Success -> {
            confirmPayShow = false
            LaunchedEffect(paymentState) {
                navController.navigate("${PageConstant.PaySuccess.text}/${orderId}") {
                    navController.navigateUp()
                }
            }
        }
        is RequestState.Error -> {
            Toast.makeText(current, (paymentState as RequestState.Error).getMessage(current), Toast.LENGTH_SHORT).show()
            paymentViewModel.resetPaymentState()
        }
        else -> {}
    }

    when (calculationOrderState) {
        is RequestState.Error -> {
            Toast.makeText(current, (calculationOrderState as RequestState.Error).getMessage(current), Toast.LENGTH_SHORT).show()
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.payment), onBack = { navController.navigateUp() })

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
            ) {
                // 订单摘要
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDimens.cardRadius),
                    color = AppColors.colorScheme.surface,
                    shadowElevation = 0.dp,
                    border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                        Text(stringResource(R.string.order_information), style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoLine(stringResource(R.string.order_number), "${orderInfo?.orderNo ?: ""}")
                        InfoLine(stringResource(R.string.service_type), "${orderInfo?.laundryPackageVo?.itemName ?: ""}")
                        InfoLine(stringResource(R.string.estimated_completion_time), stringResource(R.string.within_24_hours))
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.amount_due), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.currency_format, "${orderInfo?.payPrice ?: ""}"),
                                style = MaterialTheme.typography.displaySmall,
                                color = AppColors.colorScheme.primary
                            )
                        }
                    }
                }

                // 支付方式
                Column {
                    Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.cardRadius),
                        color = AppColors.colorScheme.surface,
                        shadowElevation = 0.dp,
                        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPaymentMethod = "balance" }
                                .padding(AppDimens.cardPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                                    .background(AppColors.colorScheme.primaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AppColors.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.balance_payment), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.current_balance_format, "${orderInfo?.userVo?.balance ?: 0}"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                            }
                            RadioButton(
                                selected = selectedPaymentMethod == "balance",
                                onClick = { selectedPaymentMethod = "balance" },
                                colors = RadioButtonDefaults.colors(selectedColor = AppColors.colorScheme.primary)
                            )
                        }
                    }
                }

                // 优惠券
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBottomSheet = true },
                    shape = RoundedCornerShape(AppDimens.cardRadius),
                    color = AppColors.colorScheme.surface,
                    shadowElevation = 0.dp,
                    border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.cardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                                .background(AppColors.colorScheme.primaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocalOffer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = AppColors.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.coupon), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (selectedCoupon == -1 || userCouponList.isEmpty()) {
                            Text(stringResource(R.string.do_not_use), style = MaterialTheme.typography.bodySmall, color = AppColors.colorScheme.textSecondary)
                        } else {
                            Text(
                                "-￥${userCouponList[selectedCoupon].couponVo.discount ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.textSecondary)
                    }
                }
            }

            // 底部支付按钮
            Column(modifier = Modifier.padding(AppDimens.pagePadding)) {
                AppButton(
                    text = stringResource(R.string.confirm_pay_format, "${orderInfo?.payPrice ?: ""}"),
                    onClick = {
                        if (orderInfo != null) {
                            if (orderInfo!!.userVo.balance >= orderInfo!!.payPrice) {
                                confirmPayShow = true
                            } else {
                                showRechargeDialog = true
                            }
                        }
                    },
                    loading = paymentState is RequestState.Loading
                )
            }
        }
    }

    // 优惠券选择弹窗
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            modifier = Modifier.fillMaxHeight()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.select_coupon), style = MaterialTheme.typography.headlineSmall)
                        TextButton(onClick = {
                            showBottomSheet = false
                            navController.navigate(PageConstant.Coupon.text)
                        }) {
                            Text(stringResource(R.string.go_claim), color = AppColors.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.primary)
                        }
                    }
                }
                if (userCouponList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_coupons), style = MaterialTheme.typography.bodyLarge, color = AppColors.colorScheme.textSecondary)
                        }
                    }
                } else {
                    items(userCouponList.size) { i ->
                        UserCouponItem(userCouponList[i], selectedCoupon == i) {
                            selectedCoupon = i
                            showBottomSheet = false
                            if (orderId != null) {
                                paymentViewModel.calculationOrder(orderId, userCouponList[i].userCouponId)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmPayShow) {
        AlertDialog(
            onDismissRequest = { confirmPayShow = false },
            text = { Text(stringResource(R.string.confirm_pay_question)) },
            confirmButton = {
                TextButton(onClick = {
                    paymentViewModel.paymentOrder(
                        orderId!!,
                        PaymentType.PURSE.type,
                        if (selectedCoupon == -1 || userCouponList.isEmpty()) null
                        else userCouponList[selectedCoupon].userCouponId
                    )
                }) { Text(stringResource(R.string.confirm), color = AppColors.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmPayShow = false }) { Text(stringResource(R.string.cancel), color = AppColors.colorScheme.textSecondary) } }
        )
    }

    if (showRechargeDialog) {
        AlertDialog(
            onDismissRequest = { showRechargeDialog = false },
            title = { Text(stringResource(R.string.insufficient_balance), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(stringResource(R.string.insufficient_balance_tip), color = AppColors.colorScheme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showRechargeDialog = false
                    navController.navigate(PageConstant.Recharge.text)
                }) { Text(stringResource(R.string.go_recharge), color = AppColors.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showRechargeDialog = false }) { Text(stringResource(R.string.cancel), color = AppColors.colorScheme.textSecondary) }
            }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.colorScheme.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UserCouponItem(
    userCouponVo: UserCouponVo,
    isSelected: Boolean,
    itemClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clickable(onClick = itemClick),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.coupon_amount_format, "${userCouponVo.couponVo.discount}"),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (userCouponVo.couponVo.threshold == 0f)
                        stringResource(R.string.coupon_discount_format, "${userCouponVo.couponVo.discount + 0.01}", "${userCouponVo.couponVo.discount}")
                    else stringResource(R.string.coupon_min_amount_format, "${userCouponVo.couponVo.threshold}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Text(
                    text = stringResource(R.string.valid_until, "${userCouponVo.expiredAt}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = { itemClick() },
                colors = RadioButtonDefaults.colors(selectedColor = AppColors.colorScheme.primary)
            )
        }
    }
}
