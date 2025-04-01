package com.smartwash.ui.page.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.utils.RequestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailPage(
    navController: NavHostController,
    orderId: Long,
    orderDetailViewModel: OrderDetailViewModel = hiltViewModel()
) {
    val getOrderDetailState by orderDetailViewModel.getOrderInfoDetail.collectAsState()
    val orderInfo by orderDetailViewModel.orderInfo.collectAsState()

    if (orderId != -1L) {
        LaunchedEffect(Unit) {
            orderDetailViewModel.getOrderDetail(orderId)
        }
    }

    when (getOrderDetailState) {
        is RequestState.Error -> {
            Toast.makeText(
                LocalContext.current,
                (getOrderDetailState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            orderDetailViewModel.resetState()
        }

        else -> {
            orderDetailViewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("订单详情", fontSize = 18.sp) },
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
            // 订单状态卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "进行中",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "预计完成时间：2024-02-06 16:30",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 学校信息
            InfoSection(
                title = "学校信息",
                icon = Icons.Rounded.School
            ) {
                InfoRow("学校名称", orderInfo?.schoolsVo?.schoolName ?: "")
                InfoRow("校区", "紫荆公寓")
                InfoRow("楼栋", "1号楼")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 寄存柜信息
            InfoSection(
                title = "寄存柜信息",
                icon = Icons.Rounded.Storage
            ) {
                InfoRow("柜号", "A-123")
                InfoRow("位置", "1号楼1层洗衣房")
                InfoRow("取件码", "1234")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 套餐信息
            InfoSection(
                title = "套餐信息",
                icon = Icons.Rounded.LocalLaundryService
            ) {
                InfoRow("套餐类型", orderInfo?.laundryPackageVo?.itemName ?: "")
                InfoRow("衣物数量", "2件")
                InfoRow("价格", "${orderInfo?.totalPrice ?: 0}")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 订单信息
            InfoSection(
                title = "订单信息",
                icon = Icons.Rounded.Receipt
            ) {
                InfoRow("订单编号", orderInfo?.orderNo ?: "")
                InfoRow("下单时间", "2024-02-05 14:30")
                InfoRow("当前状态", "进行中")
                InfoRow("完成时间", "-")
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}