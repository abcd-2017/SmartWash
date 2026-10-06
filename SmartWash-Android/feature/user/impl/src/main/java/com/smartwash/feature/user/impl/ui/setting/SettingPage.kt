package com.smartwash.feature.user.impl.ui.setting

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens

/**
 * 设置页（T5.2 自 app 迁入）。
 *
 * 「检查更新」行经 [checkUpdateContent] 插槽由宿主注入：热更新链路归 :feature:update
 * （app 壳聚合，无 api 模块），user-impl 不得建立 feature-impl 间依赖，故跨域 UI
 * 以插槽形式由 MainActivity 提供（含手动检查的 Toast 反馈，与检查入口同生命周期，
 * 避免启动静默检查误弹 Toast）。
 *
 * T5.3 起会话操作（登出）经 [SettingViewModel] 模块内自取，签名不再暴露
 * SessionManager/SessionEventBus——宿主不触碰用户域实现类型。
 */
@Composable
fun SettingPage(
    navController: NavController,
    checkUpdateContent: (@Composable () -> Unit)? = null,
    settingViewModel: SettingViewModel = hiltViewModel(),
) {
    var showDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 版本号：关于行尾注与关于弹窗同源（D-ST5）
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
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
            PageHeader(title = stringResource(R.string.settings), onBack = { navController.navigateUp() })

            // 通用
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.general),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(10.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Notifications,
                    containerColor = AppColors.colorScheme.iconContainerOrange,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                    title = stringResource(R.string.push_notification),
                    trailing = {
                        Text(
                            text = stringResource(R.string.enabled),
                            fontSize = 12.sp,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline, modifier = Modifier.padding(horizontal = 20.dp))
                SettingRow(
                    icon = Icons.Default.TouchApp,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundTeal,
                    title = stringResource(R.string.haptic_feedback),
                    trailing = {
                        Text(
                            text = stringResource(R.string.on),
                            fontSize = 12.sp,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
            }

            // 观象台
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.divination),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(10.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Security,
                    containerColor = AppColors.colorScheme.iconContainerPurple,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundPurple,
                    title = stringResource(R.string.interpretation_model),
                    trailing = {
                        Text(
                            text = "GLM-4.7",
                            fontSize = 12.sp,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline, modifier = Modifier.padding(horizontal = 20.dp))
                SettingRow(
                    icon = Icons.AutoMirrored.Default.Help,
                    containerColor = AppColors.colorScheme.iconContainerPurple,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundPurple,
                    title = stringResource(R.string.privacy_disclaimer),
                    trailing = { IconChevron() },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
            }

            // 其他
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.other),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(10.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                // 检查更新（宿主注入的跨域插槽，feature:update 归 app 壳聚合）
                checkUpdateContent?.invoke()
                if (checkUpdateContent != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline, modifier = Modifier.padding(horizontal = 20.dp))
                }
                SettingRow(
                    icon = Icons.Default.Info,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundTeal,
                    title = stringResource(R.string.about_smartwash),
                    trailing = {
                        Text(
                            text = stringResource(R.string.version_format, versionName),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.textTertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { showAboutDialog = true }
                )
            }

            // 退出登录
            Spacer(modifier = Modifier.height(16.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                TextButton(
                    onClick = { showDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        stringResource(R.string.logout),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.colorScheme.dangerInk
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.tip),
            message = stringResource(R.string.confirm_logout),
            isDanger = true,
            onConfirm = {
                showDialog = false
                // 登出（清会话 + 主动登出广播，见 SettingViewModel.logout）
                settingViewModel.logout()
                navController.navigate(UserRoute.Login.text) {
                    popUpTo(ShellRoute.HOME) { inclusive = true }
                }
            },
            onDismiss = { showDialog = false }
        )
    }

    if (showAboutDialog) {
        AppInfoDialog(
            title = stringResource(R.string.about_smartwash),
            message = stringResource(R.string.app_version, versionName),
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    containerColor: Color,
    iconForegroundColor: Color,
    title: String,
    trailing: @Composable () -> Unit = { IconChevron() },
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ib32 图标容器（32dp，10px圆角，彩色浅底 + 语义色前景图标）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconForegroundColor
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
private fun IconChevron() {
    androidx.compose.material3.Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = AppColors.colorScheme.textTertiary
    )
}
