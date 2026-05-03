package com.smartwash.ui.page.payment

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.PaymentType
import com.smartwash.utils.RequestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentPage(
    navController: NavHostController,
    orderId: Long?,
    paymentViewModel: PaymentViewModel = hiltViewModel(),
) {
    var showRechargeDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("balance") }
    val current = LocalContext.current

    val orderInfo by paymentViewModel.orderInfo.collectAsState()
    val initState by paymentViewModel.initState.collectAsState()
    val paymentState by paymentViewModel.paymentState.collectAsState()
    val userCouponList by paymentViewModel.userCouponList.collectAsState()
    val getCouponState by paymentViewModel.getUserCouponState.collectAsState()
    val calculationOrderState by paymentViewModel.calculationOrderState.collectAsState()

    var confirmPayShow by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedCoupon by remember { mutableIntStateOf(-1) }

    if (orderId != null) {
        LaunchedEffect(Unit) {
            paymentViewModel.initData(orderId)
        }
        LaunchedEffect(navController.currentBackStackEntry) {
            paymentViewModel.getaUserCoupon(orderId)
        }
    }

    when (paymentState) {
        is RequestState.Success -> {
            confirmPayShow = false
            LaunchedEffect(paymentState) {
                navController.navigate("${PageConstant.PaySuccess.text}/${orderId}") {
                    navController.navigateUp()
                }
            }
        }

        is RequestState.Error -> {
            Toast.makeText(
                current,
                (paymentState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            paymentViewModel.resetPaymentState()
        }

        else -> {}
    }

    when (initState) {
        is RequestState.Error -> {
            Toast.makeText(current, (initState as RequestState.Error).message, Toast.LENGTH_SHORT)
                .show()
        }

        else -> {
            paymentViewModel.resetInitState()
        }
    }

    when (calculationOrderState) {
        is RequestState.Error -> {
            Toast.makeText(
                current,
                (calculationOrderState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
        }

        else -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("支付", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("订单信息", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "订单号：${orderInfo?.orderNo ?: 202402050101}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "¥${orderInfo?.totalPrice ?: 1111}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "服务类型：${orderInfo?.laundryPackageVo?.itemName ?: "标准洗衣"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "预计完成时间：24小时内",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 支付方式
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("支付方式", style = MaterialTheme.typography.titleSmall)
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
                            Text("余额支付", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "当前余额：¥${orderInfo?.userVo?.balance ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 优惠券
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = { showBottomSheet = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("优惠券", style = MaterialTheme.typography.titleSmall)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedCoupon == -1 || userCouponList.isEmpty()) {
                            Text("不使用", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                "-￥${userCouponList[selectedCoupon].couponVo.discount ?: 0}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 支付按钮
            Button(
                onClick = {
                    if (orderInfo != null) {
                        if (orderInfo!!.userVo.balance >= orderInfo!!.payPrice) {
                            confirmPayShow = true
                        } else {
                            showRechargeDialog = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                when (paymentState) {
                    is RequestState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )

                    else -> {
                        Text(
                            "确认支付 ¥${orderInfo?.payPrice}",
                            style = MaterialTheme.typography.labelLarge
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
                    .padding(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("选择优惠券", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = {
                            showBottomSheet = false
                            navController.navigate(PageConstant.Coupon.text)
                        }) {
                            Text("去领取", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
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
                                text = "暂无优惠券",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(userCouponList.size) { i ->
                        UserCouponItem(
                            userCouponList[i],
                            selectedCoupon == i
                        ) {
                            selectedCoupon = i
                            showBottomSheet = false
                            if (orderId != null) {
                                paymentViewModel.calculationOrder(
                                    orderId,
                                    userCouponList[i].userCouponId
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmPayShow) {
        AlertDialog(
            onDismissRequest = { confirmPayShow = false },
            text = { Text("确定要支付？") },
            confirmButton = {
                TextButton(onClick = {
                    paymentViewModel.paymentOrder(
                        orderId!!,
                        PaymentType.PURSE.type,
                        if (selectedCoupon == -1 || userCouponList.isEmpty()) null
                        else userCouponList[selectedCoupon].userCouponId
                    )
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { confirmPayShow = false }) { Text("取消") } }
        )
    }

    if (showRechargeDialog) {
        AlertDialog(
            onDismissRequest = { showRechargeDialog = false },
            title = { Text("余额不足") },
            text = { Text("当前余额不足，请先充值") },
            confirmButton = {
                TextButton(onClick = {
                    showRechargeDialog = false
                    navController.navigate(PageConstant.Recharge.text)
                }) { Text("去充值") }
            },
            dismissButton = {
                TextButton(onClick = { showRechargeDialog = false }) { Text("取消") }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("支付成功") },
            text = { Text("请前往A区12号寄存柜存放衣物") },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) { Text("确定") }
            }
        )
    }
}

@Composable
private fun UserCouponItem(
    userCouponVo: UserCouponVo,
    isSelected: Boolean,
    itemClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { itemClick() },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${userCouponVo.couponVo.discount}元优惠券",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (userCouponVo.couponVo.threshold == 0f)
                        "满${userCouponVo.couponVo.discount + 0.01}减${userCouponVo.couponVo.discount}"
                    else
                        "满${userCouponVo.couponVo.threshold}元可用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "有效期至：${userCouponVo.expiredAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = isSelected, onClick = { itemClick() })
        }
    }
}
