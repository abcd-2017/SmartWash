package com.smartwash.feature.user.impl.ui.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavController
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.pressable
import com.smartwash.feature.user.impl.R

/**
 * 权限管理页（2026-11-01 缺陷 5c 新增，设置页跳入）：
 * 集中展示本应用声明的全部权限与当前授予状态，点击可申请（运行时权限）或跳转
 * 系统设置对应页（特殊权限）。
 *
 * 为什么没有 ViewModel：本页数据全部来自系统 PackageManager / 权限 API 的同步查询，
 * 无任何网络 / 本地数据层参与，MVVM 的 Page+VM 分层在此只有转发成本；状态在组合内
 * 以 remember 持有并在 ON_RESUME 重新核查（从系统设置返回后刷新授予态）。
 * 权限清单与 AndroidManifest 严格对应（合并宿主后）：通知 / 安装未知应用 / 网络 /
 * 后台下载服务。跳系统设置的模板取自 feature:update 的 ApkInstaller（同款 Intent）。
 */
@Composable
fun PermissionManagementPage(navController: NavController) {
    val context = LocalContext.current

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
                style = AppTextStyles.SectionTitle.copy(letterSpacing = 0.3.sp),
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionGroupCard {
                // 通知权限（运行时权限：可直接弹系统申请）
                PermissionRow(
                    icon = Icons.Default.Notifications,
                    containerColor = AppColors.colorScheme.iconContainerOrange,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                    title = stringResource(R.string.permission_notification),
                    description = stringResource(R.string.permission_notification_desc),
                    granted = granted[Perm.NOTIFICATION] == true,
                    onClick = {
                        if (granted[Perm.NOTIFICATION] != true) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppDetails(context)
                            }
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
                    granted = granted[Perm.INSTALL] == true,
                    onClick = { openUnknownAppSources(context) }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                // 网络访问（normal 权限：安装即授予，仅展示 + 说明）
                PermissionRow(
                    icon = Icons.Default.Wifi,
                    containerColor = AppColors.colorScheme.iconContainerBlue,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundBlue,
                    title = stringResource(R.string.permission_internet),
                    description = stringResource(R.string.permission_internet_desc),
                    granted = isInternetGranted(context),
                    showAction = false
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                // 前台下载服务（normal 权限：安装即授予，仅展示）
                PermissionRow(
                    icon = Icons.Default.Sync,
                    containerColor = AppColors.colorScheme.iconContainerGreen,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundGreen,
                    title = stringResource(R.string.permission_foreground_service),
                    description = stringResource(R.string.permission_foreground_service_desc),
                    granted = true,
                    showAction = false
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
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = AppDimens.cardPadding)) {
            content()
        }
    }
}

/**
 * 权限行 — 口径对齐设置页私有 SettingRow（64dp 高 / 32dp 图标盒 / 行自身零水平 padding），
 * 增加描述行；状态与动作收在行尾。
 */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    containerColor: Color,
    iconForegroundColor: Color,
    title: String,
    description: String,
    granted: Boolean,
    showAction: Boolean = true,
    onClick: () -> Unit = {},
) {
    // pressable 自绘按压反馈（§7 禁 Material 交互组件）；normal 权限行无动作，仅静态展示
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(if (showAction) Modifier.pressable(onClick = onClick) else Modifier),
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
        if (showAction) {
            Text(
                text = stringResource(
                    if (granted) R.string.permission_granted else R.string.permission_action_request
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) AppColors.colorScheme.success else AppColors.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.colorScheme.textTertiary
            )
        } else {
            Text(
                text = stringResource(R.string.permission_granted),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.success
            )
        }
    }
}

private fun isNotificationGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun isInternetGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.INTERNET
    ) == PackageManager.PERMISSION_GRANTED

private fun shouldShowNotificationRationale(context: Context): Boolean =
    (context as? androidx.activity.ComponentActivity)?.let { activity ->
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity, android.Manifest.permission.POST_NOTIFICATIONS
        )
    } ?: true

/** 跳应用详情设置页（权限被永久拒绝后的兜底入口） */
private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
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
