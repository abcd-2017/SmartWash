package com.smartwash.feature.payment.impl.ui.recharge

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import com.smartwash.common.ui.theme.AlipayBlue
import com.smartwash.common.ui.theme.WeChatGreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SelectionDot
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.pressScale
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.selectedColor


@Composable
fun RechargePage(
    navController: NavController, rechargeViewModel: RechargeViewModel = hiltViewModel()
) {
    var selectedAmount by remember { mutableStateOf<Float?>(null) }
    var customAmount by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var isCustomAmountSelected by remember { mutableStateOf(false) }
    var showPayError by remember { mutableStateOf(false) }
    // 金额校验失败原因（null 为通过）：文案随原因细分，见 RechargeAmountError
    var amountError by remember { mutableStateOf<RechargeAmountError?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val presetAmounts = listOf(10f, 20f, 50f, 100f, 200f)
    val rechargeState by rechargeViewModel.rechargeState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(rechargeState) {
        when (rechargeState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.recharge_success), Toast.LENGTH_SHORT).show()
                navController.navigateUp()
                rechargeViewModel.setRechargeStateIdle()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (rechargeState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                rechargeViewModel.setRechargeStateIdle()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.recharge),
                onBack = { navController.navigateUp() },
                actions = {
                    // §7 禁 Material 交互组件：M3 IconButton 自带 state layer 涟漪（颜色/形状不受令牌控制），
                    // 改自绘按压热区。pressable 内置 indication = null + pressScale(0.97f)，与 PageHeader 返回键一致
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .pressable(onClick = rememberDebouncedClick { navController.navigate(PaymentRoute.RechargeRecord.text) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.recharge_record),
                            modifier = Modifier.size(24.dp),
                            tint = AppColors.colorScheme.textSecondary
                        )
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
            ) {
                // 充值金额
                item {
                    Text(
                        text = stringResource(R.string.recharge_amount),
                        style = AppTextStyles.SectionTitle
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in presetAmounts.indices step 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (j in 0..2) {
                                    if (i + j < presetAmounts.size) {
                                        val amount = presetAmounts[i + j]
                                        AmountCard(
                                            amount = amount,
                                            isSelected = selectedAmount == amount && !isCustomAmountSelected,
                                            onClick = {
                                                selectedAmount = amount
                                                isCustomAmountSelected = false
                                                amountError = null
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else if (i + j == presetAmounts.size) {
                                        CustomAmountCard(
                                            value = customAmount,
                                            isSelected = isCustomAmountSelected,
                                            onValueChange = { value ->
                                                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                    customAmount = value
                                                    if (value.isNotEmpty()) {
                                                        selectedAmount = value.toFloatOrNull()
                                                        isCustomAmountSelected = true
                                                    }
                                                    amountError = null
                                                }
                                            },
                                            onClick = { isCustomAmountSelected = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        // 赠额说明行（画板实测：胶囊行下 12，caption 档；赠额映射与档位一致）
                        Text(
                            text = stringResource(R.string.recharge_bonus_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                    // 错误提示 0ms 出现/消失导致布局跳变 → 淡入 + 纵向展开/收起
                    AnimatedVisibility(
                        visible = amountError != null,
                        enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                            expandVertically(enterTween(DURATION_MICRO_MS)),
                        exit = fadeOut(exitTween(140)) + shrinkVertically(exitTween(140)),
                    ) {
                        amountError?.let { error ->
                            Text(
                                text = resolveAmountErrorText(error),
                                color = AppColors.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // 支付方式
                item {
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
                        Column {
                            PaymentMethodCard(
                                method = PaymentMethod.WECHAT,
                                isSelected = selectedPaymentMethod == PaymentMethod.WECHAT,
                                onClick = { selectedPaymentMethod = PaymentMethod.WECHAT; showPayError = false }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = AppColors.colorScheme.hairline,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            PaymentMethodCard(
                                method = PaymentMethod.ALIPAY,
                                isSelected = selectedPaymentMethod == PaymentMethod.ALIPAY,
                                onClick = { selectedPaymentMethod = PaymentMethod.ALIPAY; showPayError = false }
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = showPayError,
                        enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                            expandVertically(enterTween(DURATION_MICRO_MS)),
                        exit = fadeOut(exitTween(140)) + shrinkVertically(exitTween(140)),
                    ) {
                        Text(
                            text = stringResource(R.string.select_payment_method),
                            color = AppColors.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // 底部按钮（固定在底部，有顶部分隔线）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.colorScheme.surface)
            ) {
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.outline)
                AppButton(
                    text = if (selectedAmount != null) stringResource(R.string.recharge_now_format, String.format("%.2f", selectedAmount)) else stringResource(R.string.recharge_now),
                    onClick = { showDialog = true },
                    loading = rechargeState is RequestState.Loading,
                    modifier = Modifier.padding(16.dp, 24.dp)
                )
            }
        }
    }

    if (showDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.tip),
            message = stringResource(R.string.confirm_recharge_question),
            onConfirm = {
                showDialog = false
                // 金额与支付方式都在弹窗关闭后校验：任一不通过则不提交，错误文案显示在对应区块
                val amountResult = RechargeAmountValidator.resolve(
                    isCustomAmountSelected = isCustomAmountSelected,
                    presetAmount = selectedAmount,
                    customAmountRaw = customAmount
                )
                showPayError = selectedPaymentMethod == null
                amountError = amountResult.error
                if (amountResult.error == null && selectedPaymentMethod != null) {
                    rechargeViewModel.userRecharge(amountResult.amount!!, selectedPaymentMethod!!.payType)
                }
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun AmountCard(
    amount: Float, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    // 选中态颜色统一走公共 selectedColor（200ms 档弹簧，reduced motion 自动降级 180ms）
    val bgColor = selectedColor(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else AppColors.colorScheme.surface
    )
    val textColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primaryDark else AppColors.colorScheme.textPrimary
    )
    val borderColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.outline
    )

    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pressScale(interactionSource, 0.97f),
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        color = bgColor,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.currency_format, "${amount.toInt()}"),
                    style = AppTextStyles.AmountMedium,
                    color = textColor
                )
                // 赠送标签
                val bonus = when (amount) {
                    50f -> "送 ¥2"
                    100f -> "送 ¥8"
                    200f -> "送 ¥20"
                    else -> null
                }
                if (bonus != null) {
                    Text(
                        text = bonus,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.colorScheme.ongoingInk,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomAmountCard(
    value: String, isSelected: Boolean, onValueChange: (String) -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val bgColor = selectedColor(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else AppColors.colorScheme.surface
    )
    val borderColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.outline
    )
    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = modifier
            .height(72.dp)
            .pressable(onClick = onClick, debounce = false),
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        color = bgColor,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 选中瞬间 Text ↔ 输入框 0ms 硬切 → 交叉淡变
            Crossfade(
                targetState = isSelected,
                animationSpec = motionSpec(tween(CROSSFADE_ALT_MS)),
                label = "customAmountContent"
            ) { selected ->
                if (selected) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .focusRequester(focusRequester),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        placeholder = {
                            Text(
                                stringResource(R.string.custom),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                } else {
                    Text(
                        text = stringResource(R.string.custom_amount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textTertiary
                    )
                }
            }
        }
    }
    // 选中后自动聚焦金额输入框
    LaunchedEffect(isSelected) {
        if (isSelected) focusRequester.requestFocus()
    }
}

@Composable
private fun PaymentMethodCard(
    method: PaymentMethod, isSelected: Boolean, onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pressScale(interactionSource, 0.97f)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(
                    when (method) {
                        PaymentMethod.WECHAT -> WeChatGreen.copy(alpha = 0.1f)
                        PaymentMethod.ALIPAY -> AlipayBlue.copy(alpha = 0.1f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (method) {
                    PaymentMethod.WECHAT -> Icons.AutoMirrored.Default.Message
                    PaymentMethod.ALIPAY -> Icons.Default.Payment
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when (method) {
                    PaymentMethod.WECHAT -> WeChatGreen
                    PaymentMethod.ALIPAY -> AlipayBlue
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = when (method) {
                PaymentMethod.WECHAT -> stringResource(R.string.weixin_pay)
                PaymentMethod.ALIPAY -> stringResource(R.string.alipay)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        // 圆形选中标记 — 复用公共 SelectionDot（内圆弹簧长出），替代手绘硬切
        SelectionDot(selected = isSelected)
    }
}

private enum class PaymentMethod(val payType: String, val descriptionRes: Int) {
    WECHAT("1", R.string.weixin_pay), ALIPAY("2", R.string.alipay)
}

/** 金额校验失败原因 → 文案（校验规则见 [RechargeAmountValidator]） */
@Composable
private fun resolveAmountErrorText(error: RechargeAmountError): String = when (error) {
    RechargeAmountError.NOT_SELECTED -> stringResource(R.string.select_or_input_amount)
    RechargeAmountError.EMPTY -> stringResource(R.string.recharge_amount_empty)
    RechargeAmountError.INVALID_FORMAT -> stringResource(R.string.recharge_amount_invalid_format)
    RechargeAmountError.NOT_POSITIVE -> stringResource(R.string.recharge_amount_not_positive)
    RechargeAmountError.EXCEED_MAX -> stringResource(
        R.string.recharge_amount_exceed_max_format,
        RechargeAmountValidator.MAX_RECHARGE_AMOUNT.toInt().toString()
    )
}
