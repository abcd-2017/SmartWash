package com.smartwash.ui.page.setting

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smartwash.R
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.AppButton
import com.smartwash.ui.common.AppConfirmDialog
import com.smartwash.ui.common.AppInfoDialog
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.common.SettingRow
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.AppConstant
import com.smartwash.utils.SharePreferenceUtils

@Composable
fun SettingPage(
    navController: NavController
) {
    var showDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

            // 通知设置
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.notification),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.push_notification),
                    subtitle = stringResource(R.string.push_notification_desc),
                    trailing = { IconChevron() },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider, modifier = Modifier.padding(horizontal = AppDimens.cardPadding))
                SettingRow(
                    icon = Icons.Default.Email,
                    title = stringResource(R.string.email_notification),
                    subtitle = stringResource(R.string.email_notification_desc),
                    trailing = { IconChevron() },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
            }

            // 隐私设置
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.privacy_security),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.privacy_settings),
                    subtitle = stringResource(R.string.privacy_settings_desc),
                    trailing = { IconChevron() },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider, modifier = Modifier.padding(horizontal = AppDimens.cardPadding))
                SettingRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.account_security),
                    subtitle = stringResource(R.string.account_security_desc),
                    trailing = { IconChevron() },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
            }

            // 其他
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.other),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about_us),
                    subtitle = stringResource(R.string.about_us_desc),
                    trailing = { IconChevron() },
                    onClick = { showAboutDialog = true }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider, modifier = Modifier.padding(horizontal = AppDimens.cardPadding))
                SettingRow(
                    icon = Icons.AutoMirrored.Default.Help,
                    title = stringResource(R.string.help_feedback),
                    subtitle = stringResource(R.string.help_feedback_desc),
                    trailing = { IconChevron() },
                    onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                )
            }

            // 退出登录
            Spacer(modifier = Modifier.height(36.dp))
            Surface(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                shape = RoundedCornerShape(AppDimens.buttonRadius),
                color = AppColors.colorScheme.surface,
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.error)
            ) {
                TextButton(
                    onClick = { showDialog = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .height(52.dp)
                ) {
                    Text(
                        stringResource(R.string.logout),
                        style = MaterialTheme.typography.titleLarge,
                        color = AppColors.colorScheme.error
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
                SharePreferenceUtils.saveDataBlocking(AppConstant.TOKEN, "")
                navController.navigate(PageConstant.Login.text)
            },
            onDismiss = { showDialog = false }
        )
    }

    if (showAboutDialog) {
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
        AppInfoDialog(
            title = stringResource(R.string.about_us),
            message = stringResource(R.string.app_version, versionName),
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
private fun IconChevron() {
    androidx.compose.material3.Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        modifier = Modifier.height(16.dp),
        tint = AppColors.colorScheme.textSecondary
    )
}
