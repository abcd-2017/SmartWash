package com.smartwash.feature.user.impl.ui.changepassword

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.PasswordInput
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.user.impl.R

/**
 * 修改密码页：原密码 → 新密码 → 确认新密码（本地校验 6-16 位 + 两次一致）。
 * 修改成功后 ViewModel 已清会话（access + refreshToken）并广播登录失效，
 * 导航由壳层统一跳登录页，本页不自理跳转。
 */
@Composable
fun ChangePasswordPage(
    navController: NavController,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showOldPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    // 行内错误（仅提交时报错，输入即清除——§3.7「输入中不报错」）
    var isOldPasswordError by remember { mutableStateOf(false) }
    var isNewPasswordError by remember { mutableStateOf(false) }
    var isMismatchError by remember { mutableStateOf(false) }

    val changePasswordState by viewModel.changePasswordState.collectAsState()
    val context = LocalContext.current
    val view = currentView()

    // 状态驱动的副作用统一放 LaunchedEffect，禁止在组合期直接弹 Toast/回写状态
    LaunchedEffect(changePasswordState) {
        when (changePasswordState) {
            is RequestState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.change_password_success),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setChangePasswordIdle()
                // 会话已清 + 登录失效已广播（ViewModel），壳层统一跳登录页，此处不再导航
            }

            is RequestState.Error -> {
                // 原密码不正确等业务失败直接透传后端文案
                Toast.makeText(
                    context,
                    (changePasswordState as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.setChangePasswordIdle()
            }

            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            // 键盘弹出时内容上收，CTA 可滚动到视口内
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PageHeader(title = stringResource(R.string.change_password), onBack = { navController.navigateUp() })

            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

            GroupCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                contentPadding = PaddingValues(horizontal = AppDimens.cardPadding)
            ) {
                Column(modifier = Modifier.padding(vertical = 20.dp)) {
                    PasswordInput(
                        password = oldPassword,
                        isPasswordError = false,
                        showPassword = showOldPassword,
                        showVisibility = { showOldPassword = !showOldPassword },
                        label = stringResource(R.string.old_password)
                    ) {
                        oldPassword = it
                        isOldPasswordError = false
                    }
                    if (isOldPasswordError) {
                        InlineErrorText(text = stringResource(R.string.error_old_password_empty))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    PasswordInput(
                        password = newPassword,
                        isPasswordError = isNewPasswordError,
                        showPassword = showNewPassword,
                        showVisibility = { showNewPassword = !showNewPassword },
                        label = stringResource(R.string.new_password)
                    ) {
                        newPassword = it
                        isNewPasswordError = false
                        isMismatchError = false
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    PasswordInput(
                        password = confirmPassword,
                        isPasswordError = false,
                        showPassword = showConfirmPassword,
                        showVisibility = { showConfirmPassword = !showConfirmPassword },
                        label = stringResource(R.string.confirm_new_password)
                    ) {
                        confirmPassword = it
                        isMismatchError = false
                    }
                    if (isMismatchError) {
                        InlineErrorText(text = stringResource(R.string.error_password_mismatch))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AppButton(
                        text = stringResource(R.string.confirm_change_password),
                        loading = changePasswordState is RequestState.Loading,
                        onClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                            isOldPasswordError = oldPassword.isEmpty()
                            isNewPasswordError = newPassword.length < 6 || newPassword.length > 16
                            isMismatchError = confirmPassword != newPassword

                            if (!isOldPasswordError && !isNewPasswordError && !isMismatchError) {
                                view.performHaptic(HapticEffect.MEDIUM)
                                viewModel.changePassword(oldPassword, newPassword)
                            } else {
                                view.performHaptic(HapticEffect.ERROR)
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 长度规则说明（辅助文字 13sp 档）
            Text(
                text = stringResource(R.string.change_password_rule_hint),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 行内校验错误文案（12sp 错误色，置于字段下方，输入即清除） */
@Composable
private fun InlineErrorText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AppColors.colorScheme.dangerInk,
        modifier = Modifier.padding(top = 4.dp)
    )
}
