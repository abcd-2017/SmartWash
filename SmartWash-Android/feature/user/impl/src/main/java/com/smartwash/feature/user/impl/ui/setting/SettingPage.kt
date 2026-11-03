package com.smartwash.feature.user.impl.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import androidx.compose.runtime.collectAsState
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
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = currentView()
    // 消息通知行真实授权态（2026-11-01 缺陷 5b）：组合期查一次 + ON_RESUME 重查
    // （用户从权限页/系统设置返回后刷新；申请动作统一收口在权限管理页）
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 触感反馈真实开关（SettingViewModel 持久化 + HapticSettings 内存态）
    val hapticEnabled by settingViewModel.hapticEnabled.collectAsState()
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
                // v0.4 字阶 SectionTitle 即 18/24/600 无字距，不再叠加 copy(letterSpacing)
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingGroupCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                // 消息通知（真实授权态，2026-11-01 缺陷 5b）：行尾显示系统实际授予情况，
                // 点击进权限管理页（申请 / 跳系统设置都在那里收口）
                SettingRow(
                    icon = Icons.Default.Notifications,
                    containerColor = AppColors.colorScheme.iconContainerOrange,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                    title = stringResource(R.string.push_notification),
                    trailing = {
                        Text(
                            text = stringResource(
                                if (notificationGranted) R.string.enabled else R.string.permission_denied
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = { navController.navigate(UserRoute.PermissionManagement.text) }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                // 触感反馈（真实开关，2026-11-01 缺陷 5b）：内存态即时生效 + DataStore 异步落盘
                SettingRow(
                    icon = Icons.Default.TouchApp,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundTeal,
                    title = stringResource(R.string.haptic_feedback),
                    trailing = {
                        Text(
                            text = stringResource(if (hapticEnabled) R.string.enabled else R.string.disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconChevron()
                    },
                    onClick = {
                        val newValue = !hapticEnabled
                        settingViewModel.setHapticEnabled(newValue)
                        // 开启瞬间给一次确认触感（因果明确的时刻，规范 §7.7）
                        if (newValue) view.performHaptic(HapticEffect.LIGHT)
                    }
                )
            }

            // 观象台
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.divination),
                style = AppTextStyles.SectionTitle,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingGroupCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                // 「解读模型」假数据行已移除（2026-11-01 缺陷 5b）：端上无模型选择能力，
                // 硬编码型号属虚假展示；解读模型由服务端网关统一调度
                SettingRow(
                    icon = Icons.AutoMirrored.Default.Help,
                    containerColor = AppColors.colorScheme.iconContainerPurple,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundPurple,
                    title = stringResource(R.string.privacy_disclaimer),
                    trailing = { IconChevron() },
                    onClick = { showPrivacyDialog = true }
                )
            }

            // 其他
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.other),
                style = AppTextStyles.SectionTitle,
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
                    icon = Icons.Default.AdminPanelSettings,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundTeal,
                    title = stringResource(R.string.permission_management),
                    trailing = { IconChevron() },
                    onClick = { navController.navigate(UserRoute.PermissionManagement.text) }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
                SettingRow(
                    icon = Icons.Default.Info,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundTeal,
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
            // = 48dp 胶囊 / surfaceVariant 底 / dangerInk 文字 15sp（AppButton SECONDARY + contentColor），
            // 不套分组卡（按钮自身即浮起物）；危险语义经 contentColor 注入，不改 AppButton 三态。
            // 规范 §3.3 次按钮无描边——2026-11-01 曾补 1dp 描边（对比度），v0.4 唯一画法优先，已移除
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

    // 隐私与免责声明（2026-11-01 缺陷 5b）：原行点击仅 Toast，现展示真实全文
    if (showPrivacyDialog) {
        AppInfoDialog(
            title = stringResource(R.string.privacy_disclaimer),
            message = stringResource(R.string.privacy_disclaimer_content),
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

/**
 * 设置页分组卡 — 收敛为 DESIGN-SPEC v0.4 §3.1 唯一卡片画法（全应用只允许这一种）：
 * 纯白 surface 底 + 1px outline 描边 + 1dp 阴影（level1 ≈ CSS 0 2px 12px rgba(20,40,30,.05)）
 * + radiusLg 16 + cardPadding 20 内边距。
 *
 * 上一版 groupBackground 灰底 + 无阴影属被禁的「灰底卡片 / 无阴影裸白卡」，已废弃。
 * 内边距仅水平 20（画板实测：行内容与行分隔线同缩进 20）；纵向不加内边距——
 * 行自带 64dp 高铺满卡片，卡高 = 行数 × 64，否则上下多出 20 与画板不符。
 * 行内缩进统一由本内边距承载，行组件自身不得再叠 horizontal padding。
 */
@Composable
private fun SettingGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppDimens.cardPadding),
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
    // 水平缩进由分组卡内边距统一承载（画板：行内容与分隔线同缩进 20），
    // 此处不再叠 horizontal padding，否则 24(页) + 20(卡) + 20(行) = 64 与画板 44 不符
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable(onClick = onClick),
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
