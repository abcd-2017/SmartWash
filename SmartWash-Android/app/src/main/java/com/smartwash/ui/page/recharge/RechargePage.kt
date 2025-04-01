package com.smartwash.ui.page.recharge

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.utils.RequestState

@OptIn(ExperimentalMaterial3Api::class)
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
            Toast.makeText(context, "充值成功", Toast.LENGTH_SHORT).show()
            navController.navigateUp()
            rechargeViewModel.setRechargeStateIdle()
        }

        is RequestState.Error -> {
            Toast.makeText(
                context, (rechargeState as RequestState.Error).message, Toast.LENGTH_SHORT
            ).show()
            rechargeViewModel.setRechargeStateIdle()
        }

        else -> {}
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Text(
                "充值", fontSize = 20.sp
            )
        }, navigationIcon = {
            IconButton(onClick = {
                navController.navigateUp()
            }) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
            }
        })
    }, bottomBar = {
        Button(
            onClick = {
                showDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .padding(bottom = 26.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            when (rechargeState) {
                is RequestState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )

                else -> {
                    Text(
                        text = if (selectedAmount != null) "确认支付 ¥${
                            String.format("%.2f", selectedAmount)
                        }" else "确认支付", fontSize = 16.sp, fontWeight = FontWeight.Medium
                    )
                }
            }


        }
    }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { // 充值金额标题
                Text(
                    text = "充值金额",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // 预设金额网格
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                    // 自定义金额输入框
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
                                        onClick = {
                                            isCustomAmountSelected = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                if (showError) {
                    Text(
                        text = "请选择或输入充值金额",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                // 支付方式
                Text(
                    text = "支付方式",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {  // 支付方式选择
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PaymentMethodCard(method = PaymentMethod.WECHAT,
                        isSelected = selectedPaymentMethod == PaymentMethod.WECHAT,
                        onClick = {
                            selectedPaymentMethod = PaymentMethod.WECHAT
                            showPayError = false
                        })
                    PaymentMethodCard(method = PaymentMethod.ALIPAY,
                        isSelected = selectedPaymentMethod == PaymentMethod.ALIPAY,
                        onClick = {
                            selectedPaymentMethod = PaymentMethod.ALIPAY
                            showPayError = false
                        })
                }
                if (showPayError) {
                    Text(
                        text = "请选充值方式",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

            }
        }
    }

    if (showDialog) {
        AlertDialog(onDismissRequest = { showDialog = false },
            title = { Text(text = "提示") },
            text = {
                Text(
                    text = "是否确认充值？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    if (selectedPaymentMethod == null) {
                        showPayError = true
                    }
                    if (selectedAmount == null || selectedAmount!! <= 0) {
                        showError = true
                    }
                    if (selectedAmount != null && selectedAmount!! > 0 && selectedPaymentMethod != null) {
                        rechargeViewModel.userRecharge(
                            selectedAmount!!, selectedPaymentMethod!!.payType
                        )
                    }
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = "取消")
                }
            })
    }
}

@Composable
private fun AmountCard(
    amount: Float, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Text(
                text = "¥${amount.toInt()}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CustomAmountCard(
    value: String,
    isSelected: Boolean,
    onValueChange: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = {
                        Text(
                            "自定义金额",
                            fontSize = 14.sp,
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
                    text = "自定义金额",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    method: PaymentMethod, isSelected: Boolean, onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = when (method) {
                    PaymentMethod.WECHAT -> Icons.AutoMirrored.Default.Message
                    PaymentMethod.ALIPAY -> Icons.Default.Payment
                }, contentDescription = null, tint = when (method) {
                    PaymentMethod.WECHAT -> Color(0xFF07C160)
                    PaymentMethod.ALIPAY -> Color(0xFF1677FF)
                }
            )
            Text(
                text = when (method) {
                    PaymentMethod.WECHAT -> "微信支付"
                    PaymentMethod.ALIPAY -> "支付宝"
                }, fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private enum class PaymentMethod(val payType: String, val description: String) {
    WECHAT("1", "微信支付"), ALIPAY("2", "支付宝支付")
}