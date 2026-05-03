package com.smartwash.ui.page.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.AppConstant
import com.smartwash.utils.SharePreferenceUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPage(
    navController: NavController
) {
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                // 设置标题
                Text(
                    text = "设置",
                    fontSize = 20.sp
                )
            }, navigationIcon = {
                IconButton(modifier = Modifier.padding(10.dp), onClick = {
                    navController.navigateUp()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = null
                    )
                }
            })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 通知设置
            item {
                SettingsSection(title = "通知设置") {
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = "消息通知",
                        subtitle = "订单状态、活动提醒等",
                        onClick = {}
                    )
                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = "邮件通知",
                        subtitle = "账单、优惠信息等",
                        onClick = {}
                    )
                }
            }

            // 隐私设置
            item {
                SettingsSection(title = "隐私设置") {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "隐私设置",
                        subtitle = "个人信息、数据收集等",
                        onClick = {}
                    )
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "账号安全",
                        subtitle = "密码、登录设备等",
                        onClick = {}
                    )
                }
            }

            // 其他设置
            item {
                SettingsSection(title = "其他") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "关于我们",
                        subtitle = "版本信息、用户协议等",
                        onClick = {}
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Default.Help,
                        title = "帮助与反馈",
                        subtitle = "常见问题、意见反馈",
                        onClick = { }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(36.dp))
                // 退出登录按钮
                Button(
                    onClick = {
                        showDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("退出登录")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "提示") },
            text = {
                Text(
                    text = "是否要退出登录？"
                )
            }, confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    SharePreferenceUtils.saveDataBlocking(AppConstant.TOKEN, "")
                    navController.navigate(PageConstant.Login.text)
                }) {
                    Text(text = "确认")
                }
            }, dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = "取消")
                }
            })
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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