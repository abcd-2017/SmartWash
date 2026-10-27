package com.smartwash.feature.payment.impl.ui.recharge

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
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
    var showError by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    val presetAmounts = listOf(10f, 20f, 50f, 100f, 200f)
    val rechargeState by rechargeViewModel.rechargeState.collectAsState()
    val balance by rechargeViewModel.balance.collectAsState()
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
                    IconButton(
                        onClick = rememberDebouncedClick { navController.navigate(PaymentRoute.RechargeRecord.text) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.recharge_record),
                            tint = AppColors.colorScheme.textSecondary
                        )
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.pagePadding),
                // 画板实测区块间距 24（余额数字底→充值金额 157→181、输入框底→支付方式 373→397）
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXl)
            ) {
                // 余额区（画板实测：无卡片直接页面底；标签 12/16，大数字 36/44/700 green.700）
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.account_balance_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Text(
                            text = balance?.let {
                                stringResource(R.string.currency_format, String.format("%.2f", it))
                            } ?: "--",
                            style = AppTextStyles.DataLarge,
                            color = AppColors.colorScheme.primaryDark,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // 充值金额：一行档位胶囊 + 赠额说明 + 带可见标签的自定义金额输入框
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.recharge_amount),
                            style = AppTextStyles.SectionTitle
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // 档位胶囊行（画板实测：一行 5 个等分、间距 4、高 48、圆角 999、无描边无阴影）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            presetAmounts.forEach { amount ->
                                AmountPill(
                                    amount = amount,
                                    isSelected = selectedAmount == amount && !isCustomAmountSelected,
                                    onClick = {
                                        selectedAmount = amount
                                        isCustomAmountSelected = false
                                        showError = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        // 赠额说明行（画板实测：胶囊行下 12，caption 档；赠额映射与档位一致）
                        Text(
                            text = stringResource(R.string.recharge_bonus_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        // 输入框可见标签（画板实测：body 档，占位符只做示例不做标签）
                        Text(
                            text = stringResource(R.string.custom_amount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        CustomAmountField(
                            value = customAmount,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    customAmount = value
                                    if (value.isNotEmpty()) {
                                        selectedAmount = value.toFloatOrNull()
                                        isCustomAmountSelected = true
                                    }
                                    showError = false
                                }
                            },
                            onFocus = { isCustomAmountSelected = true },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        // 错误提示 0ms 出现/消失导致布局跳变 → 淡入 + 纵向展开/收起
                        AnimatedVisibility(
                            visible = showError,
                            enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                                expandVertically(enterTween(DURATION_MICRO_MS)),
                            exit = fadeOut(exitTween(140)) + shrinkVertically(exitTween(140)),
                        ) {
                            Text(
                                text = stringResource(R.string.select_or_input_amount),
                                color = AppColors.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // 支付方式（画板实测：标题下 12 接卡片；卡片 r16 + 1px outline，无阴影）
                item {
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
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Column {
                                PaymentMethodCard(
                                    method = PaymentMethod.WECHAT,
                                    isSelected = selectedPaymentMethod == PaymentMethod.WECHAT,
                                    onClick = { selectedPaymentMethod = PaymentMethod.WECHAT; showPayError = false }
                                )
                                // 画板实测：分隔线从文本左缘起（卡内左 68）至右缘内 20，hairline 色
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = AppColors.colorScheme.hairline,
                                    modifier = Modifier.padding(start = 68.dp, end = 20.dp)
                                )
                                PaymentMethodCard(
                                    method = PaymentMethod.ALIPAY,
                                    isSelected = selectedPaymentMethod == PaymentMethod.ALIPAY,
                                    onClick = { selectedPaymentMethod = PaymentMethod.ALIPAY; showPayError = false }
                                )
                            }
                        }
                        // 错误提示 0ms 出现/消失导致布局跳变 → 淡入 + 纵向展开/收起
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
            }

            // 底部按钮（固定在底部，有顶部分隔线；画板实测：顶线 1px、按钮左右 24/上 16/下 20，
            // 真机叠加 navigationBarsPadding 避让手势条）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.colorScheme.surface)
            ) {
                HorizontalDivider(thickness = 1.dp, color = AppColors.colorScheme.outline)
                AppButton(
                    text = if (selectedAmount != null) stringResource(R.string.recharge_now_format, String.format("%.2f", selectedAmount)) else stringResource(R.string.recharge_now),
                    onClick = { showDialog = true },
                    loading = rechargeState is RequestState.Loading,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp)
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
                if (selectedPaymentMethod == null) showPayError = true
                if (selectedAmount == null || selectedAmount!! <= 0) showError = true
                if (selectedAmount != null && selectedAmount!! > 0 && selectedPaymentMethod != null) {
                    rechargeViewModel.userRecharge(selectedAmount!!, selectedPaymentMethod!!.payType)
                }
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 档位胶囊（画板实测：高 48、圆角 999、无描边无阴影；未选 surfaceVariant 底 textPrimary 字，
 * 选中 primary 底 onPrimary 字；金额字 AmountMedium 18/24/600 + tnum）
 */
@Composable
private fun AmountPill(
    amount: Float, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    // 选中态颜色统一走公共 selectedColor（200ms 档弹簧，reduced motion 自动降级 180ms）
    val bgColor = selectedColor(
        if (isSelected) MaterialTheme.colorScheme.primary else AppColors.colorScheme.surfaceVariant
    )
    val textColor = selectedColor(
        if (isSelected) MaterialTheme.colorScheme.onPrimary else AppColors.colorScheme.textPrimary
    )

    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pressScale(interactionSource, 0.97f),
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = bgColor
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.currency_format, "${amount.toInt()}"),
                style = AppTextStyles.AmountMedium,
                color = textColor
            )
        }
    }
}

/**
 * 自定义金额输入框（画板实测：312×48、圆角 12、surfaceVariant 底、无描边无阴影；
 * 占位 caption 档 textTertiary，输入文字 body 档 textPrimary + tnum；
 * 获焦即进入自定义选中态）
 */
@Composable
private fun CustomAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(AppDimens.radiusMd),
        color = AppColors.colorScheme.surfaceVariant
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { if (it.isFocused) onFocus() },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = AppColors.colorScheme.textPrimary,
                fontFeatureSettings = "tnum"
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.custom_amount_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textTertiary
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * 支付方式行（画板实测：卡内左右 20、行高 60；图标盒 36 r12、图标 20、与文字间距 12；
 * 文字 body 档 textPrimary；选中标 24dp 圆——选中 primary 实心 + 20dp onPrimary 勾，
 * 未选 surface 底 + 1px outline 圈）
 */
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(
                    when (method) {
                        // 画板实测：微信 #F0FBF4/#17322B（= primaryContainer 双主题实测值）、
                        // 支付宝 #EEF2FB/#1F2637（= iconContainerBlue）
                        PaymentMethod.WECHAT -> MaterialTheme.colorScheme.primaryContainer
                        PaymentMethod.ALIPAY -> AppColors.colorScheme.iconContainerBlue
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (method) {
                    PaymentMethod.WECHAT -> Icons.AutoMirrored.Filled.Chat
                    PaymentMethod.ALIPAY -> Icons.Default.Payment
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (method) {
                    PaymentMethod.WECHAT -> AppColors.colorScheme.iconForegroundGreen
                    PaymentMethod.ALIPAY -> AppColors.colorScheme.iconForegroundBlue
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = when (method) {
                PaymentMethod.WECHAT -> stringResource(R.string.weixin_pay)
                PaymentMethod.ALIPAY -> stringResource(R.string.alipay)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        // 圆形选中标记 — 复用公共 SelectionDot（内圆弹簧长出），替代手绘硬切
        SelectionDot(selected = isSelected, dotSize = 24.dp)
    }
}

private enum class PaymentMethod(val payType: String, val descriptionRes: Int) {
    WECHAT("1", R.string.weixin_pay), ALIPAY("2", R.string.alipay)
}
