package com.smartwash.ui.page.userinfo

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.RequestState

@SuppressLint("DefaultLocale")
@Composable
fun UserInfoPage(
    navController: NavHostController,
    homePageNavController: NavHostController,
    userInfoViewModel: UserInfoViewModel = hiltViewModel(),
) {
    val userInfoStatus by userInfoViewModel.userInfoStatus.collectAsState()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    val orderItemCountVo by userInfoViewModel.orderItemCount.collectAsState()
    val bindCampusState by userInfoViewModel.bindCampusState.collectAsState()
    val unBindCampusState by userInfoViewModel.unBindCampusState.collectAsState()

    var showBindDialog by remember { mutableStateOf(false) }
    var showUnbindDialog by remember { mutableStateOf(false) }
    var cardNumber by remember { mutableStateOf("") }
    var cardNumberError by remember { mutableStateOf(false) }
    var context = LocalContext.current

    LaunchedEffect(homePageNavController.currentBackStackEntry) {
        userInfoViewModel.getUserInfo()
    }
    when (userInfoStatus) {
        is RequestState.Error -> {
            Toast.makeText(
                LocalContext.current,
                (userInfoStatus as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            userInfoViewModel.resetState()
        }

        else -> {
            userInfoViewModel.resetState()
        }
    }
    when (bindCampusState) {
        is RequestState.Success -> {
            showBindDialog = false
            cardNumber = ""
            cardNumberError = false
            LaunchedEffect(bindCampusState) {
                Toast.makeText(
                    context,
                    "绑定成功",
                    Toast.LENGTH_SHORT
                ).show()
            }
            userInfoViewModel.resetBindCampusState()
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (bindCampusState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            userInfoViewModel.resetBindCampusState()
        }

        else -> {}
    }
    when (unBindCampusState) {
        is RequestState.Success -> {
            showUnbindDialog = false
            LaunchedEffect(unBindCampusState) {
                Toast.makeText(
                    context,
                    "解绑成功",
                    Toast.LENGTH_SHORT
                ).show()
            }
            userInfoViewModel.resetUnBindCampusState()
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (unBindCampusState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            userInfoViewModel.resetUnBindCampusState()
        }

        else -> {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "个人中心",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = {
                navController.navigate(PageConstant.Setting.text)
            }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        // 用户信息区域
        UserInfoSection(userInfo)

        // 账户信息区域
        SectionTitle("账户信息")
        Spacer(modifier = Modifier.height(8.dp))
        // 账户余额卡片
        AccountInfoCard(
            title = "账户余额",
            actionText = "¥${String.format("%s", userInfo?.balance ?: 0)}",
            buttonText = "充值",
            imageVector = Icons.Default.AccountBalance
        ) {
            navController.navigate(PageConstant.Recharge.text)
        }
        Spacer(modifier = Modifier.height(12.dp))
        // 校园卡卡片
        AccountInfoCard(
            title = "校园卡",
            actionText = if (userInfo?.campusCard != null) userInfo?.campusCard ?: "" else "未绑定",
            buttonText = if (userInfo?.campusCard != null) "解绑" else "绑定",
            imageVector = Icons.Default.CreditCard
        ) {
            if (userInfo?.campusCard != null) {
                showUnbindDialog = true
            } else {
                showBindDialog = true
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 优惠券卡片
        AccountInfoCard(
            title = "优惠券",
            actionText = "可领取优惠券",
            buttonText = "领取",
            imageVector = Icons.Default.LocalOffer
        ) {
            navController.navigate(PageConstant.Coupon.text)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 订单管理区域
        SectionTitle("订单管理")
        Spacer(modifier = Modifier.height(8.dp))

        OrderManagementSection(
            pendingPaymentCount = orderItemCountVo?.pendingPaymentCount ?: 0,
            processingCount = orderItemCountVo?.processingCount ?: 0,
            pendingPickupCount = orderItemCountVo?.pendingPickupCount ?: 0,
            onViewAllOrders = { item ->
                navController.navigate("${PageConstant.Order.text}/$item")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        // 其他功能区域
        SectionTitle("其他功能")
        Spacer(modifier = Modifier.height(8.dp))

        OtherFunctionsSection(
            onCustomerServiceClick = {},
            onFAQClick = {}
        )
    }

    // 绑定校园卡弹窗
    if (showBindDialog) {
        AlertDialog(
            onDismissRequest = { showBindDialog = false },
            title = { Text("绑定校园卡", fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = {
                        cardNumberError = false
                        cardNumber = it
                    },
                    label = { Text("请输入校园卡号") },
                    supportingText = { if (cardNumberError) Text("校园卡号不能为空") },
                    singleLine = true,
                    isError = cardNumberError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (cardNumber.isEmpty()) {
                            cardNumberError = true
                        } else {
                            userInfoViewModel.bindCampus(cardNumber)
                        }
                    }
                ) { Text("确认绑定") }
            },
            dismissButton = {
                TextButton(onClick = { showBindDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 解绑校园卡弹窗
    if (showUnbindDialog) {
        AlertDialog(
            onDismissRequest = { showUnbindDialog = false },
            title = { Text("解绑校园卡", fontSize = 18.sp) },
            text = { Text("确定要解绑当前绑定的校园卡吗？") },
            confirmButton = {
                TextButton(onClick = { userInfoViewModel.unBindCampus() }) { Text("确认解绑") }
            },
            dismissButton = { TextButton(onClick = { showUnbindDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun UserInfoSection(
    userInfo: UserInfoVo?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 用户头像
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "头像",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        // 用户信息
        Column {
            Text(
                text = userInfo?.phoneNumber ?: "用户名",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "学号：${userInfo?.studentId ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun AccountInfoCard(
    title: String,
    actionText: String,
    buttonText: String,
    imageVector: ImageVector,
    onCardClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
    ) {
        // 账户余额卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCardClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column() {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                TextButton(onClick = onCardClick) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderManagementSection(
    pendingPaymentCount: Int,
    processingCount: Int,
    pendingPickupCount: Int,
    onViewAllOrders: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OrderStatusCard(
                    icon = Icons.Default.Schedule,
                    title = "待支付",
                    count = pendingPaymentCount,
                ) { onViewAllOrders(1) }
            }

            item {
                OrderStatusCard(
                    icon = Icons.Default.LocalLaundryService,
                    title = "清洗中",
                    count = processingCount,
                ) { onViewAllOrders(3) }
            }
            item {
                OrderStatusCard(
                    icon = Icons.Default.Inventory,
                    title = "待取件",
                    count = pendingPickupCount,
                ) { onViewAllOrders(4) }
            }
            item {
                OrderStatusCard(
                    icon = Icons.Default.History,
                    title = "历史订单",
                    subtitle = "查看全部",
                    onClick = { onViewAllOrders(0) },
                )
            }
        }
    }
}

@Composable
private fun OrderStatusCard(
    icon: ImageVector,
    title: String,
    count: Int? = null,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(80.dp)
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
            if (count != null) {
                Text(
                    text = "${count}个订单",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun OtherFunctionsSection(
    onCustomerServiceClick: () -> Unit,
    onFAQClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        FunctionItem(
            icon = Icons.Default.Headset,
            title = "联系客服",
            subtitle = "在线咨询",
            onClick = onCustomerServiceClick
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        FunctionItem(
            icon = Icons.AutoMirrored.Filled.Help,
            title = "常见问题",
            subtitle = "使用指南",
            onClick = onFAQClick
        )
    }
}

@Composable
private fun FunctionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}