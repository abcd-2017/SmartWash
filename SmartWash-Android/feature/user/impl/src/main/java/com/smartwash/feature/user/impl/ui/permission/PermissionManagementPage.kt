package com.smartwash.feature.user.impl.ui.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.user.impl.R

/**
 * 权限管理页（2026-11-01 缺陷 5c 新增，设置页跳入；2026-11-12 重构）：
 * 只归集「向用户申请的、用户可随时开关」的权限与设置——通知权限 / 安装未知应用 /
 * 触感反馈。安装即授予、用户无法随时开关的系统权限（网络访问、前台下载服务）
 * 不再列出，避免「展示当前授予状态」的误读。
 *
 * 为什么有自己的 ViewModel：触感开关是端内持久化状态（DataStore），需要
 * [PermissionManagementViewModel] 承载；权限授予态仍来自系统 PackageManager 的
 * 同步查询，在组合内以 remember 持有并在 ON_RESUME 重新核查（从系统设置返回后刷新）。
 * 跳系统设置的 Intent 模板取自 feature:update 的 ApkInstaller（同款）。
 */
@Composable
fun PermissionManagementPage(
    navController: NavController,
    permissionManagementViewModel: PermissionManagementViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = currentView()

    // 权限授予态：key = 条目标识，ON_RESUME 全量重查（从系统设置返回后刷新）
    val granted = remember { mutableStateMapOf(Perm.NOTIFICATION to false, Perm.INSTALL to false) }
    fun refreshGranted() {
        granted[Perm.NOTIFICATION] = isNotificationGranted(context)
        granted[Perm.INSTALL] = context.packageManager.canRequestPackageInstalls()
    }
    refreshGranted()

    // 从系统设置 / 系统弹窗返回后重查授予态（权限变更不经任何回调，只能靠生命周期兜底）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 运行时权限申请（通知）：拒绝且不再询问 → 引导去应用详情页
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted[Perm.NOTIFICATION] = isGranted
        if (!isGranted && !shouldShowNotificationRationale(context)) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.permission_request_denied_hint),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 触感反馈开关（自设置页迁入，2026-11-12）：与权限同页，都是用户可随时开关的项
    val hapticEnabled by permissionManagementViewModel.hapticEnabled.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PageHeader(
                title = stringResource(R.string.permission_management),
                onBack = { navController.navigateUp() }
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceXl))
            Text(
                text = stringResource(R.string.general),
                // U-116：v0.4 字阶已废字距，删除 letterSpacing = 0.3.sp
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionGroupCard {
                // 通知权限（运行时权限：未授权 → 弹系统申请；已授权 → 去系统设置关闭，
                // 保证「用户能够随时开关」双向可达）
                PermissionRow(
                    icon = Icons.Default.Notifications,
                    containerColor = AppColors.colorScheme.iconContainerOrange,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                    title = stringResource(R.string.permission_notification),
                    description = stringResource(R.string.permission_notification_desc),
                    trailingText = stringResource(
                        if (granted[Perm.NOTIFICATION] == true) {
                            R.string.permission_granted
                        } else {
                            R.string.permission_action_request
                        }
                    ),
                    trailingColor = if (granted[Perm.NOTIFICATION] == true) {
                        AppColors.colorScheme.success
                    } else {
                        AppColors.colorScheme.textGreen
                    },
                    onClick = {
                        if (granted[Perm.NOTIFICATION] == true) {
                            openNotificationSettings(context)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                // 安装未知应用（特殊权限：只能跳系统设置对应页，模板同 ApkInstaller）
                PermissionRow(
                    icon = Icons.Default.SystemUpdate,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundGreen,
                    title = stringResource(R.string.permission_install),
                    description = stringResource(R.string.permission_install_desc),
                    trailingText = stringResource(
                        if (granted[Perm.INSTALL] == true) {
                            R.string.permission_granted
                        } else {
                            R.string.permission_action_request
                        }
                    ),
                    trailingColor = if (granted[Perm.INSTALL] == true) {
                        AppColors.colorScheme.success
                    } else {
                        AppColors.colorScheme.textGreen
                    },
                    onClick = { openUnknownAppSources(context) }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                // 触感反馈（端内开关，非系统权限；与上面两项同页归集，2026-11-12 自设置页迁入）
                PermissionRow(
                    icon = Icons.Default.TouchApp,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundTeal,
                    title = stringResource(R.string.permission_haptic),
                    description = stringResource(R.string.permission_haptic_desc),
                    trailingText = stringResource(
                        if (hapticEnabled) R.string.enabled else R.string.disabled
                    ),
                    trailingColor = AppColors.colorScheme.textSecondary,
                    onClick = {
                        val newValue = !hapticEnabled
                        permissionManagementViewModel.setHapticEnabled(newValue)
                        // 开启瞬间给一次确认触感（因果明确的时刻，规范 §7.7）
                        if (newValue) view.performHaptic(HapticEffect.LIGHT)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_footer),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 行内 granted / 操作标识（非用户可见，仅作 mutableStateMapOf 的键） */
private enum class Perm { NOTIFICATION, INSTALL }

/** 分组卡 — 与设置页 SettingGroupCard 同款（规范 §3.1 唯一卡片画法） */
@Composable
private fun PermissionGroupCard(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding).softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = AppDimens.cardPadding)) {
            content()
        }
    }
}

/**
 * 权限/设置行 — 口径对齐设置页私有 SettingRow（64dp 高 / 32dp 图标盒 / 行自身零水平 padding），
 * 增加描述行；行尾状态与动作文案由调用方按条目语义给定（权限用「已授权/去授权」，
 * 端内开关用「已开启/已关闭」）。
 */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    containerColor: Color,
    iconForegroundColor: Color,
    title: String,
    description: String,
    trailingText: String,
    trailingColor: Color,
    onClick: () -> Unit,
) {
    // pressable 自绘按压反馈（§7 禁 Material 交互组件）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .pressable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconForegroundColor)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = AppColors.colorScheme.textPrimary)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
        }
        Text(
            text = trailingText,
            style = MaterialTheme.typography.bodySmall,
            color = trailingColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.colorScheme.textTertiary
        )
    }
}

private fun isNotificationGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun shouldShowNotificationRationale(context: Context): Boolean =
    (context as? androidx.activity.ComponentActivity)?.let { activity ->
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity, android.Manifest.permission.POST_NOTIFICATIONS
        )
    } ?: true

/** 跳系统应用通知设置页（已授权时供用户关闭；minSdk 30 高于 API 26，无需应用详情页兜底） */
private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

/** 跳「安装未知应用」系统授权页（Intent 模板同 feature:update 的 ApkInstaller） */
private fun openUnknownAppSources(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
