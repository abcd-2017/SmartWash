package com.smartwash.ui.page.recharge

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.smartwash.ui.theme.AlipayBlue
import com.smartwash.ui.theme.WeChatGreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.R
import com.smartwash.ui.common.AppButton
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.RequestState


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
    val context = LocalContext.current

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
                    IconButton(onClick = { navController.navigate(PageConstant.RechargeRecord.text) }) {
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
                verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
            ) {
                // 充值金额
                item {
                    Text(
                        text = stringResource(R.string.recharge_amount),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in presetAmounts.indices step 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                                showError = false
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
                                                    showError = false
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
                    }
                    if (showError) {
                        Text(
                            text = stringResource(R.string.select_or_input_amount),
                            color = AppColors.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // 支付方式
                item {
                    Text(
                        text = stringResource(R.string.payment_method),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.cardRadius),
                        color = AppColors.colorScheme.surface,
                        shadowElevation = 0.dp,
                        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
                    ) {
                        Column {
                            PaymentMethodCard(
                                method = PaymentMethod.WECHAT,
                                isSelected = selectedPaymentMethod == PaymentMethod.WECHAT,
                                onClick = { selectedPaymentMethod = PaymentMethod.WECHAT; showPayError = false }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .padding(horizontal = 16.dp)
                                    .background(AppColors.colorScheme.divider)
                            )
                            PaymentMethodCard(
                                method = PaymentMethod.ALIPAY,
                                isSelected = selectedPaymentMethod == PaymentMethod.ALIPAY,
                                onClick = { selectedPaymentMethod = PaymentMethod.ALIPAY; showPayError = false }
                            )
                        }
                    }
                    if (showPayError) {
                        Text(
                            text = stringResource(R.string.select_payment_method),
                            color = AppColors.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(AppDimens.pagePadding)) {
                AppButton(
                    text = if (selectedAmount != null) stringResource(R.string.confirm_pay_format, String.format("%.2f", selectedAmount)) else stringResource(R.string.payment),
                    onClick = { showDialog = true },
                    loading = rechargeState is RequestState.Loading
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = stringResource(R.string.tip), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(text = stringResource(R.string.confirm_recharge_question), color = AppColors.colorScheme.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    if (selectedPaymentMethod == null) showPayError = true
                    if (selectedAmount == null || selectedAmount!! <= 0) showError = true
                    if (selectedAmount != null && selectedAmount!! > 0 && selectedPaymentMethod != null) {
                        rechargeViewModel.userRecharge(selectedAmount!!, selectedPaymentMethod!!.payType)
                    }
                }) { Text(text = stringResource(R.string.confirm), color = AppColors.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(text = stringResource(R.string.cancel), color = AppColors.colorScheme.textSecondary) }
            }
        )
    }
}

@Composable
private fun AmountCard(
    amount: Float, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AppColors.colorScheme.primaryLight else AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = if (isSelected) BorderStroke(1.5.dp, AppColors.colorScheme.primary) else BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.currency_format, "${amount.toInt()}"),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.textPrimary
            )
        }
    }
}

@Composable
private fun CustomAmountCard(
    value: String, isSelected: Boolean, onValueChange: (String) -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AppColors.colorScheme.primaryLight else AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = if (isSelected) BorderStroke(1.5.dp, AppColors.colorScheme.primary) else BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        fontWeight = MaterialTheme.typography.headlineSmall.fontWeight
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = {
                        Text(stringResource(R.string.custom), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            } else {
                Text(text = stringResource(R.string.custom_amount), style = MaterialTheme.typography.bodyMedium, color = AppColors.colorScheme.textSecondary)
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    method: PaymentMethod, isSelected: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimens.cardPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
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
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AppColors.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private enum class PaymentMethod(val payType: String, val descriptionRes: Int) {
    WECHAT("1", R.string.weixin_pay), ALIPAY("2", R.string.alipay)
}
