package com.smartwash.ui.page.pickup

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.ui.common.InfoRow
import com.smartwash.ui.common.InfoSection
import com.smartwash.utils.PickupDeliveryType
import com.smartwash.utils.RequestState
import com.smartwash.utils.generateQrCodeBitmap

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupDeliveryPage(
    type: Int,
    navController: NavHostController,
    orderId: Long,
    pickupDeliveryViewModel: PickupDeliveryViewModel = hiltViewModel(),
) {
    val getOrderDetailState by pickupDeliveryViewModel.getOrderInfoDetail.collectAsState()
    val setOrderNextState by pickupDeliveryViewModel.setOrderNextState.collectAsState()
    val orderInfo by pickupDeliveryViewModel.orderInfo.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    if (orderId != -1L) {
        LaunchedEffect(Unit) {
            pickupDeliveryViewModel.getOrderDetail(orderId)
        }
    }

    when (getOrderDetailState) {
        is RequestState.Error -> {
            Toast.makeText(
                LocalContext.current,
                (getOrderDetailState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            pickupDeliveryViewModel.resetState()
        }

        else -> {
            pickupDeliveryViewModel.resetState()
        }
    }
    val pickupCode = orderInfo?.pickupCode?.let { it.split(":")[2] } ?: "error"
    val lockerNumber = orderInfo?.lockersVo?.lockerNumber ?: 0

    val qrBitmap = remember(pickupCode) { generateQrCodeBitmap(pickupCode) }
    val imageBitmap = qrBitmap.asImageBitmap()
    val context = LocalContext.current

    when (setOrderNextState) {
        is RequestState.Success -> {
            showDialog = false
//            Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show()
            LaunchedEffect(Unit) {
                navController.navigateUp()
            }
            pickupDeliveryViewModel.resetNextStatusState()
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (setOrderNextState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            pickupDeliveryViewModel.resetNextStatusState()
        }

        else -> {}
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (type == PickupDeliveryType.PICKUP.type) "取件" else "寄件",
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 注意事项
            if (type == PickupDeliveryType.DELIVERY.type) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "注意事项",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "• 请确保在30分钟内完成操作\n" +
                                    "• 如遇问题请联系客服\n" +
                                    "• 请勿将贵重物品放入寄存柜",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 二维码和取件码
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "二维码",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = if (type == PickupDeliveryType.PICKUP.type) "取件码" else "寄件码",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = pickupCode,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (type == PickupDeliveryType.PICKUP.type) "确认已取件" else "确认已寄件")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 学校信息
            InfoSection(
                title = "寄存柜信息",
                icon = Icons.Rounded.School
            ) {
                InfoRow("学校名称", orderInfo?.schoolsVo?.schoolName ?: "")
                InfoRow("地址", orderInfo?.schoolsVo?.location ?: "")
                InfoRow("寄存柜柜号", "$lockerNumber")
                InfoRow(
                    if (type == PickupDeliveryType.PICKUP.type) "取件码" else "寄件码",
                    pickupCode
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            // 操作指引
            InfoSection(
                title = "操作指引",
                icon = Icons.Rounded.Info
            ) {
                if (type == PickupDeliveryType.PICKUP.type) {
                    InstructionRow("1", "找到寄存柜 $lockerNumber")
                    InstructionRow("2", "点击屏幕上的\" 取件 \"按钮")
                    InstructionRow("3", "输入取件码：$pickupCode")
                    InstructionRow("4", "等待柜门打开，取出衣物")
                } else {
                    InstructionRow("1", "找到寄存柜 $lockerNumber")
                    InstructionRow("2", "点击屏幕上的\" 寄件 \"按钮")
                    InstructionRow("3", "输入寄件码：$pickupCode")
                    InstructionRow("4", "等待柜门打开，放入衣物")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDialog) {
        AlertDialog(onDismissRequest = { showDialog = false },
            text = {
                Text(
                    text = if (type == PickupDeliveryType.DELIVERY.type) "确认已将衣服放入寄存柜" else "确认已取出衣服",
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton({
                    pickupDeliveryViewModel.setOrderNextState(
                        type,
                        orderId,
                        orderInfo?.pickupCode ?: ""
                    )
                }) {
                    Text("确定")
                }
            })
    }
}

@Composable
private fun InstructionRow(
    number: String,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = number,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}