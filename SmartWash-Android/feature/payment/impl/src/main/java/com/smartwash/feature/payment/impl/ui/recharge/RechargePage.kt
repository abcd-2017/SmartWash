package com.smartwash.feature.payment.impl.ui.recharge

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.painterResource
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
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.selectedColor


@Composable
fun RechargePage(
    navController: NavController, rechargeViewModel: RechargeViewModel = hiltViewModel()
) {
    // 默认选中第一档（2026-11-01 缺陷 8c：原初始全空导致页面无任何高亮项）
    var selectedAmount by remember { mutableStateOf<Float?>(10f) }
    var customAmount by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var isCustomAmountSelected by remember { mutableStateOf(false) }
    var showPayError by remember { mutableStateOf(false) }
    // 金额校验失败原因（null 为通过）：文案随原因细分，见 RechargeAmountError
    var amountError by remember { mutableStateOf<RechargeAmountError?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val presetAmounts = listOf(10f, 20f, 50f, 100f, 200f)
    val rechargeState by rechargeViewModel.rechargeState.collectAsState()
    // 画板实测：页面顶部直出账户余额（无卡片），数据 VM 已有 balance 流
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
                    // §7 禁 Material 交互组件：M3 IconButton 自带 state layer 涟漪（颜色/形状不受令牌控制），
                    // 改自绘按压热区。pressable 内置 indication = null + pressScale(0.97f)，与 PageHeader 返回键一致
                    Box(
                        // 触控热区 48dp（与 PageHeader 返回键同规范 §3.2）
                        modifier = Modifier
                            .size(48.dp)
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
                // 画板实测区块间距 24（余额→充值金额标题、输入框→支付方式）
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXl)
            ) {
                // 账户余额（画板实测：直出页面底，无卡片；12/400 + DataLarge primaryDark）
                item(key = "balance") {
                    Column {
                        Text(
                            text = stringResource(R.string.account_balance_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Text(
                            text = stringResource(R.string.currency_format, String.format("%.2f", balance ?: 0f)),
                            style = AppTextStyles.DataLarge,
                            color = AppColors.colorScheme.primaryDark
                        )
                    }
                }

                // 充值金额（2026-11-01 缺陷 8b：档位 + 自选收进标准卡 §3.1 唯一画法，
                // 原灰底胶囊直接铺页面底对比弱、与支付方式卡层级语言不一）
                item(key = "amount") {
                    Column {
                        Text(
                            text = stringResource(R.string.recharge_amount),
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            shadowElevation = AppElevation.level1,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                                // 画板实测：5 档单行等宽（间距 12），非 3 列两行
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    presetAmounts.forEach { amount ->
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
                                    }
                                }
                                // 赠额说明行（画板实测：胶囊行下 12，caption 档；赠额映射与档位一致）
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.recharge_bonus_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                                // 画板实测：自定义金额为独立 label + 48 高输入框（非第 6 个档位格）
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.custom_amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CustomAmountInput(
                                    value = customAmount,
                                    isSelected = isCustomAmountSelected,
                                    onValueChange = { value ->
                                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                            customAmount = value
                                            if (value.isNotEmpty()) {
                                                selectedAmount = value.toFloatOrNull()
                                                isCustomAmountSelected = true
                                            } else {
                                                // 清空即清选中态（2026-11-01 缺陷 8c：原清空后
                                                // 残留旧值，底部按钮金额与高亮都不回落）
                                                selectedAmount = null
                                                isCustomAmountSelected = false
                                            }
                                            amountError = null
                                        }
                                    }
                                )
                            }
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
                }

                // 支付方式
                item {
                    Text(
                        text = stringResource(R.string.payment_method),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.colorScheme.textPrimary
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
                                // 画板实测：分隔线与文字列对齐（x=92 = 卡内 20 + 图标盒 36 + 间距 12）
                                modifier = Modifier.padding(start = 68.dp, end = 20.dp)
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
                    // 画板实测：左右 24、上 16、下 20（真机叠加手势条避让）；原 padding(16, 24)
                    // 是 Compose 双参重载 = 水平 16 / 垂直 24，与画板正好相反（§3.7 已警示该坑）
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
    // 画板实测：档位胶囊 48 高 r14（buttonRadius）；
    // 未选 surfaceVariant(#F5F6F3) + textPrimary，选中 primarySoft(#E8F6EF) + onPrimarySoft(#0B3625)
    // + 1dp primary 描边（2026-11-01 缺陷 8b：灰底胶囊进白卡后与页面同灰调对比不足，加描边强化选中辨识）。
    // 选中态颜色统一走公共 selectedColor（200ms 档弹簧，reduced motion 自动降级 180ms）
    val bgColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primarySoft else AppColors.colorScheme.surfaceVariant
    )
    val textColor = selectedColor(
        if (isSelected) AppColors.colorScheme.onPrimarySoft else AppColors.colorScheme.textPrimary
    )
    val borderColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.surfaceVariant
    )
    Surface(
        modifier = modifier
            .height(48.dp)
            .pressable(onClick = onClick, debounce = false),
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 金额 = 金额中字档（AmountMedium 18/600 tnum）；赠送信息由说明行承载，不再入格
            Text(
                text = stringResource(R.string.currency_format, "${amount.toInt()}"),
                style = AppTextStyles.AmountMedium,
                color = textColor
            )
        }
    }
}

/**
 * 自定义金额输入（画板实测：独立 48 高输入框 r12、占位 16/400 textSecondary；
 * 输入正文 16sp 符合 §3.7 输入控件标准）。
 * isSelected（2026-11-01 缺陷 8c）：选中自选金额时输入框同步高亮
 * （primarySoft 底 + primary 描边），与档位胶囊互斥高亮，页面始终有明确选中项。
 */
@Composable
private fun CustomAmountInput(
    value: String,
    isSelected: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primarySoft else AppColors.colorScheme.surfaceVariant
    )
    val borderColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.surfaceVariant
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(AppDimens.radiusMd),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            placeholder = {
                Text(
                    stringResource(R.string.custom_amount_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true
        )
    }
}

@Composable
private fun PaymentMethodCard(
    method: PaymentMethod, isSelected: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, debounce = false)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 官方品牌图标（2026-11-01 缺陷 8a）：原微信=Message/支付宝=Payment 通用 Material 图标
        // 违反规范 §4.4「第三方品牌图标不得换成通用组件」红线，替换为官方矢量（Simple Icons，CC0）；
        // 着色用官方品牌色（红线豁免：品牌色不得换），容器仍走六色浅底令牌
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(
                    when (method) {
                        PaymentMethod.WECHAT -> AppColors.colorScheme.iconContainerGreen
                        PaymentMethod.ALIPAY -> AppColors.colorScheme.iconContainerBlue
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    when (method) {
                        PaymentMethod.WECHAT -> R.drawable.ic_wechat
                        PaymentMethod.ALIPAY -> R.drawable.ic_alipay
                    }
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (method) {
                    // 官方品牌色：微信 #09BB07 / 支付宝 #1677FF（Simple Icons 口径）
                    PaymentMethod.WECHAT -> WeChatBrandGreen
                    PaymentMethod.ALIPAY -> AlipayBrandBlue
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = when (method) {
                PaymentMethod.WECHAT -> stringResource(R.string.weixin_pay)
                PaymentMethod.ALIPAY -> stringResource(R.string.alipay)
            },
            // 画板实测支付行标题 14/400 textPrimary（bodyMedium 档），非 18/600
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        // 圆形选中标记 — 画板实测 24（复用公共 SelectionDot 内圆弹簧长出）
        SelectionDot(selected = isSelected, dotSize = 24.dp)
    }
}

private enum class PaymentMethod(val payType: String, val descriptionRes: Int) {
    WECHAT("1", R.string.weixin_pay), ALIPAY("2", R.string.alipay)
}

/** 官方品牌色（规范 §10 红线豁免：第三方品牌图标与品牌色不得换；充值页/记录页共用） */
internal val WeChatBrandGreen = Color(0xFF09BB07)
internal val AlipayBrandBlue = Color(0xFF1677FF)

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
