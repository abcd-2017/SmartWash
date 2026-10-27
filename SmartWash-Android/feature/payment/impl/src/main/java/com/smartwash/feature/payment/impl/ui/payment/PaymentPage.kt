package com.smartwash.feature.payment.impl.ui.payment

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.smartwash.common.ui.components.SelectionDot
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCoupon by remember { mutableIntStateOf(-1) }

    if (orderId != null) {
        LaunchedEffect(Unit) { paymentViewModel.initData(orderId) }
        LaunchedEffect(Unit) { paymentViewModel.getaUserCoupon(orderId) }
    }

    LaunchedEffect(paymentState) {
        when (paymentState) {
            is RequestState.Success -> {
                confirmPayShow = false
                // 状态重放防护：先复位再导航，避免配置重建时 Success 态重放导致重复跳转
                paymentViewModel.resetPaymentState()
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
            // 状态重放防护：Error 态复位为 Idle，否则配置重建会重放 Toast
            paymentViewModel.resetCalculationOrderState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        // 首载骨架 ↔ 内容：交叉淡变（reduced motion 由 motionSpec 降级为 180ms）
        Crossfade(
            targetState = orderInfo == null,
            animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
            label = "paymentContent"
        ) { isLoading ->
            if (isLoading) {
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
                        // 应付金额卡片（画板实测：24 边距 / r16 / 1px outline 描边 / 内边距 20，无阴影）
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.amount_due),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                                // 应付金额：选券重算后数值 0ms 硬切 → 交叉淡变（DataLarge 已带 tabular numbers）
                                AnimatedContent(
                                    targetState = orderInfo?.payPrice,
                                    transitionSpec = {
                                        fadeIn(enterTween(DURATION_MICRO_MS)) togetherWith
                                            fadeOut(exitTween(120))
                                    },
                                    label = "amountDue"
                                ) { payPrice ->
                                    Text(
                                        text = stringResource(
                                            R.string.currency_format,
                                            "${payPrice ?: ""}"
                                        ),
                                        style = AppTextStyles.DataLarge,
                                        color = AppColors.colorScheme.primaryDark,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = AppColors.colorScheme.hairline,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                                InfoLine(stringResource(R.string.order_number), "${orderInfo?.orderNo ?: ""}")
                                InfoLine(stringResource(R.string.service_type), "${orderInfo?.laundryPackageVo?.itemName ?: ""}")
                                InfoLine(stringResource(R.string.estimated_completion_time), stringResource(R.string.within_24_hours))
                            }
                        }

                        // 支付方式（区块标题 + 余额支付卡 + 优惠券行，卡间距 12 = 列表项间距）
                        Column {
                            Text(
                                text = stringResource(R.string.payment_method),
                                style = AppTextStyles.SectionTitle
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // 选中态画板实测：primaryContainer 底（#F0FBF4/#17322B）+ 1px 描边
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.radiusLg),
                                color = if (selectedPaymentMethod == "balance")
                                    MaterialTheme.colorScheme.primaryContainer
                                else AppColors.colorScheme.surface,
                                border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressable(
                                            onClick = { selectedPaymentMethod = "balance" },
                                            debounce = false,
                                        )
                                        .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(AppDimens.radiusMd))
                                            .background(
                                                // 选中卡上的图标盒：浅色=白 surface、深色=surfaceVariant（画板实测）
                                                if (selectedPaymentMethod == "balance") {
                                                    if (isSystemInDarkTheme()) AppColors.colorScheme.surfaceVariant
                                                    else AppColors.colorScheme.surface
                                                } else {
                                                    AppColors.colorScheme.iconContainerGreen
                                                }
                                            ),
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
                                        Text(
                                            stringResource(R.string.balance_payment),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (selectedPaymentMethod == "balance")
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else AppColors.colorScheme.textPrimary
                                        )
                                        Text(
                                            stringResource(R.string.current_balance_format, "${orderInfo?.userVo?.balance ?: 0}"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppColors.colorScheme.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    CheckMark(
                                        isSelected = selectedPaymentMethod == "balance",
                                        onClick = { selectedPaymentMethod = "balance" },
                                        // 勾与文本列顶对齐（画板实测 y 同为 367），非整卡居中
                                        modifier = Modifier.align(Alignment.Top)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 优惠券
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable(onClick = { showBottomSheet = true }, debounce = false),
                                shape = RoundedCornerShape(AppDimens.radiusLg),
                                color = AppColors.colorScheme.surface,
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
                                            modifier = Modifier.size(20.dp),
                                            tint = AppColors.colorScheme.iconForegroundOrange
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.coupon),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // 「不使用」↔「-￥x」0ms 硬切 → 交叉淡变
                                    AnimatedContent(
                                        targetState = if (selectedCoupon == -1 || userCouponList.isEmpty()) null
                                        else userCouponList[selectedCoupon].discount,
                                        transitionSpec = {
                                            fadeIn(enterTween(DURATION_MICRO_MS)) togetherWith
                                                fadeOut(exitTween(120))
                                        },
                                        label = "couponDiscount"
                                    ) { discount ->
                                        Text(
                                            text = if (discount == null) stringResource(R.string.do_not_use)
                                            else "-￥${discount ?: 0}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFeatureSettings = if (discount == null) "normal" else "tnum"
                                            ),
                                            color = if (discount == null) AppColors.colorScheme.textSecondary
                                            else MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = AppColors.colorScheme.textTertiary
                                    )
                                }
                            }
                        }
                    }

                    // 底部支付按钮（固定在底部，有顶部分隔线；安全区按规范 §4 避让手势条）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.colorScheme.surface)
                    ) {
                        HorizontalDivider(thickness = 1.dp, color = AppColors.colorScheme.outline)
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
                            // 画板实测：按钮左右边距 24、上 16、下 20（画板无手势条，真机叠加 navigationBarsPadding）
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp)
                        )
                    }
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
                        // 去领取 — 自绘文字动作（规范 §7 禁 M3 交互组件；防抖已注入，关闭叠加）
                        Row(
                            modifier = Modifier
                                .pressable(
                                    onClick = rememberDebouncedClick {
                                        showBottomSheet = false
                                        navController.navigate(CouponRoute.Coupon.text)
                                    },
                                    debounce = false,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
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
                    item(key = "no_coupon") {
                        // 「不使用优惠券」显式选项：从已选券切回无券（对齐鸿蒙 Payment.ets:144-177）
                        val noCouponClick = rememberDebouncedClick {
                            selectedCoupon = -1
                            showBottomSheet = false
                            if (orderId != null) {
                                // 后端 calculation 以 0 表示不使用优惠券（-1 会落到 selectById(-1) → 优惠券异常）
                                paymentViewModel.calculationOrder(orderId, NO_COUPON_USER_COUPON_ID)
                            }
                        }
                        NoCouponItem(isSelected = selectedCoupon == -1, itemClick = noCouponClick)
                    }
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFeatureSettings = "tnum"
            ),
            color = AppColors.colorScheme.textPrimary
        )
    }
}

/**
 * 余额支付选中标记 — 复用公共 [SelectionDot]（28dp 外环 + 内圆弹簧长出）。
 * 外围保留 pressable 行点击；无 ripple（规范 §7）。
 */
@Composable
private fun CheckMark(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .pressable(onClick = onClick, debounce = false),
        contentAlignment = Alignment.Center
    ) {
        SelectionDot(selected = isSelected, dotSize = 28.dp)
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
            .pressable(onClick = itemClick, debounce = false),
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
            // 自绘选中行（公共 SelectionDot），替代 Material RadioButton（自带 ripple）
            SelectionDot(selected = isSelected)
        }
    }
}

/** 「不使用优惠券」选项行（形态与 [UserCouponItem] 对齐） */
@Composable
private fun NoCouponItem(
    isSelected: Boolean,
    itemClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .pressable(onClick = itemClick, debounce = false),
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
                    text = stringResource(R.string.do_not_use_coupon),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.do_not_use_coupon_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
            // 自绘选中行（公共 SelectionDot），与优惠券卡保持一致（Material RadioButton 自带 ripple）
            SelectionDot(selected = isSelected)
        }
    }
}

/**
 * 「不使用优惠券」传给试算接口的 userCouponId。
 *
 * 后端 `OrdersServiceImpl.calculationOrder` 以 `null || 0` 判定不使用优惠券；
 * 传 -1 会走到 `userCouponMapper.selectById(-1)` 并抛「优惠券异常」。
 */
private const val NO_COUPON_USER_COUPON_ID = 0L
