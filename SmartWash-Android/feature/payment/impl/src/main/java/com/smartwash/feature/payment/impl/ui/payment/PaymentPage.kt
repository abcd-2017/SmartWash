package com.smartwash.feature.payment.impl.ui.payment

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.feature.coupon.api.CouponRoute
import com.smartwash.feature.coupon.api.model.UsableCoupon
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.rememberDebouncedClick

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
        LaunchedEffect(Unit) { paymentViewModel.getaUserCoupon(orderId) }
    }

    LaunchedEffect(paymentState) {
        when (paymentState) {
            is RequestState.Success -> {
                confirmPayShow = false
                navController.navigate("${PaymentRoute.PaySuccess.text}/${orderId}") {
                    popUpTo(ShellRoute.HOME)
                }
            }
            is RequestState.Error -> {
                Toast.makeText(current, (paymentState as RequestState.Error).getMessage(current), Toast.LENGTH_SHORT).show()
                paymentViewModel.resetPaymentState()
            }
            else -> {}
        }
    }

    LaunchedEffect(calculationOrderState) {
        if (calculationOrderState is RequestState.Error) {
            Toast.makeText(
                current,
                (calculationOrderState as RequestState.Error).getMessage(current),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (orderInfo == null) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PageHeader(title = stringResource(R.string.payment), onBack = { navController.navigateUp() })

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
                ) {
                    // 应付金额卡片（独立卡片，设计稿 §10）
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.radiusLg),
                        color = AppColors.colorScheme.surface,
                        shadowElevation = AppElevation.level1,
                        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(22.dp, 20.dp)) {
                            Text(
                                text = stringResource(R.string.amount_due),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                                color = AppColors.colorScheme.textSecondary
                            )
                            Text(
                                text = stringResource(
                                    R.string.currency_format,
                                    "${orderInfo?.payPrice ?: ""}"
                                ),
                                style = AppTextStyles.DataLarge,
                                color = AppColors.colorScheme.primaryDark,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = AppColors.colorScheme.hairline,
                                modifier = Modifier.padding(vertical = 18.dp)
                            )
                            InfoLine(stringResource(R.string.order_number), "${orderInfo?.orderNo ?: ""}")
                            InfoLine(stringResource(R.string.service_type), "${orderInfo?.laundryPackageVo?.itemName ?: ""}")
                            InfoLine(stringResource(R.string.estimated_completion_time), stringResource(R.string.within_24_hours))
                        }
                    }

                    // 支付方式
                    Column {
                        Text(
                            text = stringResource(R.string.payment_method),
                            style = AppTextStyles.SectionTitle
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            shadowElevation = AppElevation.level1,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPaymentMethod = "balance" }
                                    .padding(horizontal = 20.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(AppDimens.radiusMd))
                                        .background(AppColors.colorScheme.iconContainerGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalOffer,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                        tint = AppColors.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.balance_payment),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        stringResource(R.string.current_balance_format, "${orderInfo?.userVo?.balance ?: 0}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.colorScheme.textTertiary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                CheckMark(
                                    isSelected = selectedPaymentMethod == "balance",
                                    onClick = { selectedPaymentMethod = "balance" }
                                )
                            }
                        }
                    }

                    // 优惠券
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBottomSheet = true },
                        shape = RoundedCornerShape(AppDimens.radiusLg),
                        color = AppColors.colorScheme.surface,
                        shadowElevation = AppElevation.level1,
                        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(AppDimens.radiusMd))
                                    .background(AppColors.colorScheme.iconContainerOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                    tint = AppColors.colorScheme.iconForegroundOrange
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                stringResource(R.string.coupon),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (selectedCoupon == -1 || userCouponList.isEmpty()) {
                                Text(
                                    stringResource(R.string.do_not_use),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                            } else {
                                Text(
                                    "-￥${userCouponList[selectedCoupon].discount ?: 0}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = AppColors.colorScheme.textTertiary
                            )
                        }
                    }
                }

                // 底部支付按钮（固定在底部，有顶部分隔线）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.colorScheme.surface)
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.outline)
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
                        loading = paymentState is RequestState.Loading,
                        modifier = Modifier.padding(16.dp, 24.dp)
                    )
                }
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
                        Text(
                            stringResource(R.string.select_coupon),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        TextButton(
                            onClick = rememberDebouncedClick {
                                showBottomSheet = false
                                navController.navigate(CouponRoute.Coupon.text)
                            }
                        ) {
                            Text(stringResource(R.string.go_claim), color = AppColors.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.colorScheme.primary
                            )
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
                            Text(
                                stringResource(R.string.no_coupons),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppColors.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        count = userCouponList.size,
                        key = { i -> userCouponList[i].userCouponId }
                    ) { i ->
                        // 选券点击防抖：calculationOrder 无防重，连点重复发起重算请求
                        val couponClick = rememberDebouncedClick {
                            selectedCoupon = i
                            showBottomSheet = false
                            if (orderId != null) {
                                paymentViewModel.calculationOrder(orderId, userCouponList[i].userCouponId)
                            }
                        }
                        UserCouponItem(userCouponList[i], selectedCoupon == i, itemClick = couponClick)
                    }
                }
            }
        }
    }

    if (confirmPayShow) {
        AppConfirmDialog(
            message = stringResource(R.string.confirm_pay_question),
            onConfirm = {
                // 资金关键：立即关弹窗。原实现等 Success 回调才关窗，期间确认键持续可点，
                // 配合后端无幂等 = 连点重复扣款入口；防抖窗口过期后的二次点击也由此阻断
                confirmPayShow = false
                paymentViewModel.paymentOrder(
                    orderId!!,
                    PaymentType.PURSE.type,
                    if (selectedCoupon == -1 || userCouponList.isEmpty()) null
                    else userCouponList[selectedCoupon].userCouponId
                )
            },
            onDismiss = { confirmPayShow = false }
        )
    }

    if (showRechargeDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.insufficient_balance),
            message = stringResource(R.string.insufficient_balance_tip),
            confirmText = stringResource(R.string.go_recharge),
            onConfirm = {
                showRechargeDialog = false
                navController.navigate(PaymentRoute.Recharge.text)
            },
            onDismiss = { showRechargeDialog = false }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.colorScheme.textSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = "tnum"
            ),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CheckMark(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(
                width = 1.6.dp,
                color = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun UserCouponItem(
    userCouponVo: UsableCoupon,
    isSelected: Boolean,
    itemClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clickable(onClick = itemClick),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
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
                    text = stringResource(R.string.coupon_amount_format, "${userCouponVo.discount}"),
                    style = AppTextStyles.AmountMedium
                )
                Text(
                    text = if (userCouponVo.threshold == 0f)
                        stringResource(R.string.coupon_discount_format, "${userCouponVo.discount + 0.01}", "${userCouponVo.discount}")
                    else stringResource(R.string.coupon_min_amount_format, "${userCouponVo.threshold}"),
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
