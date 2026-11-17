package com.smartwash.lite.ui.setting

import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.work.WorkManager
import com.smartwash.R
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.user.api.UserRoute
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * lite 设置页——「账户 / 检查更新 / 退出登录」三块。
 *
 * 账户信息只读（经 [LiteSettingViewModel] 的 UserApi 拉取）；检查更新经
 * [LiteUpdateSection]（`:feature:update:core` 的 AppUpdateRepository + ApkDownloadWorker，
 * 手动触发：拉版本 → 有新版下载 → 安装）；退出登录复用既有会话能力
 * （清 token + 主动登出广播），确认后回登录页并清栈。
 */
@Composable
fun LiteSettingPage(
    navController: NavController,
    settingViewModel: LiteSettingViewModel = hiltViewModel(),
    workManager: WorkManager = hiltViewModelWorkManager(),
) {
    val context = LocalContext.current
    val view = currentView()
    val userInfo by settingViewModel.userInfo.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }

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
                title = stringResource(R.string.lite_setting_title),
                onBack = { navController.navigateUp() }
            )

            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            SectionLabel(text = stringResource(R.string.lite_setting_section_account))
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            GroupCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppDimens.cardPadding
                ),
            ) {
                LiteSettingRow(
                    icon = Icons.Filled.Person,
                    containerColor = AppColors.colorScheme.iconContainerTeal,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundTeal,
                    title = stringResource(R.string.lite_setting_account_phone),
                    value = userInfo?.phoneNumber.orEmpty().ifBlank { "-" },
                    onClick = null,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            SectionLabel(text = stringResource(R.string.lite_setting_section_diet))
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            GroupCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppDimens.cardPadding
                ),
            ) {
                // 饮食偏好（契约 §三.2：忌口 + 每日热量/钠目标，页面在 user 域两变体共用）
                LiteSettingRow(
                    icon = Icons.Filled.Restaurant,
                    containerColor = AppColors.colorScheme.iconContainerOrange,
                    iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                    title = stringResource(com.smartwash.feature.user.impl.R.string.dietary_preference_title),
                    onClick = { navController.navigate(UserRoute.DietaryPreference) },
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = AppColors.colorScheme.textTertiary,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            SectionLabel(text = stringResource(R.string.lite_setting_section_general))
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            GroupCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppDimens.cardPadding
                ),
            ) {
                // 检查更新（lite 手动链路：拉版本 → 下载 → 安装，见 LiteUpdateSection）
                LiteUpdateSection(workManager)
            }

            // 退出登录 — 次按钮形态 + dangerInk（规范 §2.1 唯一按钮例外）
            Spacer(modifier = Modifier.height(AppDimens.cardSpacing))
            AppButton(
                text = stringResource(R.string.lite_setting_logout),
                onClick = { showLogoutDialog = true },
                variant = AppButtonVariant.SECONDARY,
                contentColor = AppColors.colorScheme.dangerInk,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceXxl))
        }
    }

    if (showLogoutDialog) {
        AppConfirmDialog(
            title = stringResource(com.smartwash.feature.user.impl.R.string.tip),
            message = stringResource(com.smartwash.feature.user.impl.R.string.confirm_logout),
            isDanger = true,
            onConfirm = {
                view.performHaptic(HapticEffect.MEDIUM)
                showLogoutDialog = false
                settingViewModel.logout()
                navController.navigate(UserRoute.Login) {
                    popUpTo<ShellRoute.Home> { inclusive = true }
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AppTextStyles.SectionTitle,
        color = AppColors.colorScheme.textSecondary,
        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
    )
}

/**
 * lite 设置行 — 图标容器 32dp（12dp 圆角，六色浅底按语义取色）+ 标题/副文案 + 右侧值或箭头。
 * [onClick] 为 null 时整行不可点（只读账户信息）。
 */
@Composable
private fun LiteSettingRow(
    icon: ImageVector,
    containerColor: Color,
    iconForegroundColor: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, scaleFactor = 0.97f) else Modifier)
            // 行高对齐 full SettingRow（56dp，规范 §3.5 列表行）
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(
            icon = icon,
            size = 32.dp,
            iconSize = 20.dp,
            containerColor = containerColor,
            iconTint = iconForegroundColor,
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/**
 * 取 [WorkManager] 实例（lite 手动检查更新的下载调度用）。
 *
 * 经 [EntryPointAccessors] 从 Application 取 Hilt 供给的实例（`UpdateCoreModule` 的
 * `@Provides @Singleton WorkManager`）——不在 Composable 里注入 Activity 级依赖，
 * 与 full 的 UpdateFlow 取用方式一致。
 */
@Composable
private fun hiltViewModelWorkManager(): WorkManager {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LiteUpdateEntryPoint::class.java,
        ).workManager()
    }
}

/** [WorkManager] 取用入口（Hilt 全组件共享 `UpdateCoreModule` 的供给） */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LiteUpdateEntryPoint {
    fun workManager(): WorkManager
}
