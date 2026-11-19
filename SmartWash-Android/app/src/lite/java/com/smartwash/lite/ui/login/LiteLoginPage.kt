package com.smartwash.lite.ui.login

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.R
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppPasswordTextField
import com.smartwash.common.ui.components.AppTextField
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.isValidPhone
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.lite.ui.login.LiteLoginViewModel

/**
 * lite 登录页（占位骨架，布局与 full 的毛玻璃认证页不同）。
 *
 * 能力复用：经 [LiteLoginViewModel] 走 `:feature:user:impl` 的 UserRepository /
 * SessionManager / SessionEventBus（登录网络链路 + 会话落盘 + 登录态广播），
 * 不重复实现账户逻辑；差异仅在 UI 与去向——lite 登录成功 / 已有会话直接进
 * [ShellRoute.Home]（lite 首页），不做 full 的学校信息补填判断。
 *
 * 视觉：浅底 + 白卡（清氧功能页画法），输入组件复用 common:ui 的 [AppTextField] /
 * [AppPasswordTextField]（§3.7 表单规范），主 CTA 走 [AppButton]（48dp 胶囊）。
 */
@Composable
fun LiteLoginPage(
    navController: NavController,
    loginViewModel: LiteLoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = currentView()

    // 手机号保留：去注册再返回时不必重输（密码按隐私红线不落 saved state）
    var phone by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPhoneError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }

    val loginState by loginViewModel.loginState.collectAsState()

    // 已有会话直接进首页（lite 不做学校信息补填判断）
    LaunchedEffect(Unit) {
        if (loginViewModel.hasSavedToken()) {
            navController.navigate(ShellRoute.Home) {
                popUpTo<UserRoute.Login> { inclusive = true }
            }
        }
    }

    // 状态驱动的副作用统一放 LaunchedEffect（禁止组合期 Toast/导航）
    LaunchedEffect(loginState) {
        when (loginState) {
            is RequestState.Success -> {
                // 先导航再 reset：reset 会改 key 取消本协程，颠倒顺序会导致导航不执行
                navController.navigate(ShellRoute.Home) {
                    popUpTo<UserRoute.Login> { inclusive = true }
                }
                loginViewModel.resetLoginState()
            }

            is RequestState.Error -> {
                Toast.makeText(
                    context,
                    (loginState as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                loginViewModel.resetLoginState()
            }

            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(AppDimens.spaceXxl))

        IconBox(
            icon = Icons.Filled.Restaurant,
            size = 44.dp,
            iconSize = 24.dp,
            containerColor = AppColors.colorScheme.primarySoft,
            iconTint = AppColors.colorScheme.textGreen,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text(
            text = stringResource(R.string.lite_brand_name),
            style = AppTextStyles.RootTitle,
            color = AppColors.colorScheme.textPrimary
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        Text(
            text = stringResource(R.string.lite_login_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary
        )

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        // 输入卡（清氧唯一卡片画法）
        GroupCard(modifier = Modifier.fillMaxWidth()) {
            // 字段标签行（设计稿：label 位于输入行上方，间距 4dp）
            Text(
                text = stringResource(com.smartwash.common.ui.R.string.phone_hint),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            AppTextField(
                value = phone,
                onValueChange = {
                    if (it.length <= 11) phone = it
                    // 只在已显示错误后实时更新，避免输入中报错（§3.7）
                    isPhoneError = if (it.length == 11) !isValidPhone(it) else isPhoneError
                    if (it.length == 11 && isValidPhone(it)) {
                        passwordFocusRequester.requestFocus()
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
                isError = isPhoneError,
                leadingIcon = Icons.Rounded.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = {
                    AnimatedVisibility(
                        visible = isPhoneError,
                        enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                            expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
                        exit = fadeOut(motionSpec(exitTween(140))) +
                            shrinkVertically(motionSpec(exitTween(140))),
                    ) {
                        Text(
                            text = stringResource(com.smartwash.common.ui.R.string.invalid_phone),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = AppColors.colorScheme.error
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))

            Text(
                text = stringResource(com.smartwash.common.ui.R.string.password_hint),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = AppColors.colorScheme.textPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            AppPasswordTextField(
                value = password,
                onValueChange = {
                    if (it.length <= 16) password = it
                    isPasswordError = if (it.length >= 6) false else isPasswordError
                },
                modifier = Modifier.focusRequester(passwordFocusRequester),
                isError = isPasswordError,
                supportingText = {
                    AnimatedVisibility(
                        visible = isPasswordError,
                        enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                            expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
                        exit = fadeOut(motionSpec(exitTween(140))) +
                            shrinkVertically(motionSpec(exitTween(140))),
                    ) {
                        Text(
                            text = stringResource(com.smartwash.common.ui.R.string.invalid_password),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = AppColors.colorScheme.error
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceXl))

        AppButton(
            text = stringResource(R.string.lite_login_title),
            loading = loginState is RequestState.Loading,
            onClick = {
                isPhoneError = !isValidPhone(phone)
                isPasswordError = password.isEmpty() || password.length < 6 || password.length > 16
                if (!isPhoneError && !isPasswordError) {
                    view.performHaptic(HapticEffect.MEDIUM)
                    keyboardController?.hide()
                    loginViewModel.loginUser(phone, password)
                } else {
                    view.performHaptic(HapticEffect.ERROR)
                }
            },
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceXl))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.lite_no_account),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
            Text(
                text = stringResource(R.string.lite_register_now),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = AppColors.colorScheme.textGreen,
                modifier = Modifier
                    .pressable(onClick = { navController.navigate(UserRoute.Register) })
                    .padding(horizontal = AppDimens.spaceXxs, vertical = AppDimens.spaceMd)
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceXxl))
    }
}
