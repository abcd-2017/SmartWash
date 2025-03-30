package com.smartwash.ui.payment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentPage(
    navController: NavHostController,
    long: Long?
) {
    var showRechargeDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("balance") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("支付") },
                navigationIcon = {
                    IconButton(onClick = { /* 返回上一页 */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 订单信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "订单信息",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("订单号：202402050101")
                        Text("¥1111", color = MaterialTheme.colorScheme.primary)
                    }
                    Text("服务类型：标准洗衣")
                    Text("预计完成时间：24小时内")
                }
            }

            // 支付方式
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "支付方式",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPaymentMethod = "balance" }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPaymentMethod == "balance",
                            onClick = { selectedPaymentMethod = "balance" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("余额支付")
                            Text(
                                "当前余额：¥1111",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // 支付按钮
            Button(
                onClick = {
//                    if (userBalance >= orderAmount) {
//                        showSuccessDialog = true
//                    } else {
                    showRechargeDialog = true
//                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("确认支付 ¥$111")
            }
        }
    }

    // 余额不足对话框
    if (showRechargeDialog) {
        AlertDialog(
            onDismissRequest = { showRechargeDialog = false },
            title = { Text("余额不足") },
            text = { Text("当前余额不足，请先充值") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRechargeDialog = false
                    }
                ) {
                    Text("去充值")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRechargeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 支付成功对话框
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("支付成功") },
            text = { Text("请前往A区12号寄存柜存放衣物") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }
}