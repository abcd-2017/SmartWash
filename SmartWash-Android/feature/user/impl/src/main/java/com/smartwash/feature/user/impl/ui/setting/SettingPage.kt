package com.smartwash.feature.user.impl.ui.setting

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable

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
 *
 * 视觉对齐（Penpot「设置 · 浅色/深色」画板，360 宽 = 360dp）：页面留白 24、
 * 分组卡 = 规范 §3.1 唯一卡片画法（surface 白底 + 1px outline 描边 + level1 阴影
 * + radius 16 + 内边距 20）、行高 64（图标盒 32 顶距 16）、图标盒-文字间距 12、
 * 区块间距 24；退出登录按 §2.1 例外用次按钮形态（52dp / 14dp / surfaceVariant / dangerInk）。
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
    val view = currentView()
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
            Spacer(modifier = Modifier.height(AppDimens.spaceXl))
            Text(
                text = stringResource(R.string.general),
                style = AppTextStyles.SectionTitle.copy(letterSpacing = 0.3.sp),
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingGroupCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Notifications,
                    containerColor = AppColors.colorScheme.iconContainerOrange,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                    title = stringResource(R.string.push_notification),
                    trailing = {
                        Text(
                            text = stringResource(R.string.enabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                SettingRow(
                    icon = Icons.Default.TouchApp,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundGreen,
                    title = stringResource(R.string.haptic_feedback),
                    trailing = {
                        Text(
                            text = stringResource(R.string.on),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
            }

            // 观象台
            Spacer(modifier = Modifier.height(AppDimens.spaceXl))
            Text(
                text = stringResource(R.string.divination),
                style = AppTextStyles.SectionTitle.copy(letterSpacing = 0.3.sp),
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingGroupCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Security,
                    containerColor = AppColors.colorScheme.iconContainerPurple,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundPurple,
                    title = stringResource(R.string.interpretation_model),
                    trailing = {
                        Text(
                            text = stringResource(R.string.interpretation_model_glm),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
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
            Spacer(modifier = Modifier.height(AppDimens.spaceXl))
            Text(
                text = stringResource(R.string.other),
                style = AppTextStyles.SectionTitle.copy(letterSpacing = 0.3.sp),
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingGroupCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                // 检查更新（宿主注入的跨域插槽，feature:update 归 app 壳聚合）
                checkUpdateContent?.invoke()
                if (checkUpdateContent != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                }
                SettingRow(
                    icon = Icons.Default.Info,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundGreen,
                    title = stringResource(R.string.about_smartwash),
                    trailing = {
                        Text(
                            text = stringResource(R.string.version_format, versionName),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { showAboutDialog = true }
                )
            }

            // 退出登录 — §2.1 唯一按钮例外（2026-10-29 用户拍板）：次按钮形态
            // = 52dp 高 / 14dp 圆角 / surfaceVariant 底 / dangerInk 文字 15sp，
            // 不套分组卡（按钮自身即浮起物）；危险语义经 contentColor 注入，不改 AppButton 三态
            Spacer(modifier = Modifier.height(16.dp))
            AppButton(
                text = stringResource(R.string.logout),
                onClick = { showDialog = true },
                variant = AppButtonVariant.SECONDARY,
                contentColor = AppColors.colorScheme.dangerInk,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.tip),
            message = stringResource(R.string.confirm_logout),
            isDanger = true,
            onConfirm = {
                // 登出是不可逆的确认操作：先给中触，再清会话
                view.performHaptic(HapticEffect.MEDIUM)
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

/**
 * 设置页分组卡 — 收敛为 DESIGN-SPEC v0.4 §3.1 唯一卡片画法（全应用只允许这一种）：
 * 纯白 surface 底 + 1px outline 描边 + 1dp 阴影（level1 ≈ CSS 0 2px 12px rgba(20,40,30,.05)）
 * + radiusLg 16 + cardPadding 20 内边距。
 *
 * 上一版 groupBackground 灰底 + 无阴影属被禁的「灰底卡片 / 无阴影裸白卡」，已废弃。
 * 行内分隔线缩进随之由 20 交由卡片内边距承载，视觉与 UpdateUserInfo 字段分组卡一致。
 */
@Composable
private fun SettingGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            content = content
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
            .height(64.dp)
            .pressable(onClick = onClick)
            .padding(horizontal = AppDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ib32 图标容器（32dp，12px圆角，彩色浅底 + 语义色前景图标）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconForegroundColor
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
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
        modifier = Modifier.size(20.dp),
        tint = AppColors.colorScheme.textTertiary
    )
}
