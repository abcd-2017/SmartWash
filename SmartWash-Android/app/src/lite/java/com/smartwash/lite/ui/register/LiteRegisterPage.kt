package com.smartwash.lite.ui.register

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.PasswordInput
import com.smartwash.common.ui.components.PhoneNumberInput
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.ErrorLight
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.isValidPhone
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.lite.ui.register.LiteRegisterViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * lite 注册页（占位骨架，布局与 full 的毛玻璃注册页不同）。
 *
 * 能力复用：经 [LiteRegisterViewModel] 走 `:feature:user:impl` 的 UserRepository /
 * SessionManager / SessionEventBus（验证码下发 + 注册即登录 + 会话落盘 + 登录态广播）；
 * 注册成功后进 [ShellRoute.Home]（lite 首页），不走 full 的「学校信息补填」页。
 */
@Composable
fun LiteRegisterPage(
    navController: NavController,
    registerViewModel: LiteRegisterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = currentView()
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPhoneError by remember { mutableStateOf(false) }
    var isCodeError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var countDown by remember { mutableStateOf(0) }
    var countDownJob by remember { mutableStateOf<Job?>(null) }

    val codeFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    val captchaState by registerViewModel.captchaState.collectAsState()
    val registerState by registerViewModel.registerState.collectAsState()

    // 验证码下发状态：成功 Toast + 启动倒计时；失败 Toast + 取消倒计时立即恢复可点
    LaunchedEffect(captchaState) {
        when (captchaState) {
            is RequestState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(com.smartwash.feature.user.impl.R.string.captcha_sent),
                    Toast.LENGTH_SHORT
                ).show()
                registerViewModel.setCaptchaIdle()
            }

            is RequestState.Error -> {
                Toast.makeText(
                    context,
                    (captchaState as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                countDownJob?.cancel()
                countDownJob = null
                countDown = 0
                registerViewModel.setCaptchaIdle()
            }

            else -> {}
        }
    }

    LaunchedEffect(registerState) {
        when (registerState) {
            is RequestState.Success -> {
                // 先导航再 reset：reset 会改 key 取消本协程，颠倒顺序会导致导航不执行
                navController.navigate(ShellRoute.Home) {
                    popUpTo<UserRoute.Login> { inclusive = true }
                }
                registerViewModel.setRegisterIdle()
            }

            is RequestState.Error -> {
                Toast.makeText(
                    context,
                    (registerState as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                registerViewModel.setRegisterIdle()
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
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        // 顶部返回 + 标题（返回热区 48dp，自绘无 state layer）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressable(onClick = { navController.popBackStack() }, debounce = false),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(com.smartwash.common.ui.R.string.back),
                    modifier = Modifier.size(24.dp),
                    tint = AppColors.colorScheme.textPrimary
                )
            }
            Text(
                text = stringResource(R.string.lite_register_title),
                style = AppTextStyles.PageHeader,
                color = AppColors.colorScheme.textPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceXl))

        Text(
            text = stringResource(R.string.lite_brand_name),
            style = AppTextStyles.RootTitle,
            color = AppColors.colorScheme.textPrimary
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        Text(
            text = stringResource(R.string.lite_register_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary
        )

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        GroupCard(modifier = Modifier.fillMaxWidth()) {
            PhoneNumberInput(
                phone = phone,
                isPhoneError = isPhoneError,
                contentColor = AppColors.colorScheme.textPrimary,
                label = stringResource(com.smartwash.common.ui.R.string.phone_hint),
            ) {
                if (it.length <= 11) phone = it
                isPhoneError = when {
                    it.isEmpty() -> false
                    it.length == 11 -> !isValidPhone(it)
                    else -> isPhoneError
                }
                if (it.length == 11 && isValidPhone(it)) {
                    codeFocusRequester.requestFocus()
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))

            LiteVerificationCodeRow(
                code = verificationCode,
                isCodeError = isCodeError,
                countDown = countDown,
                captchaLoading = captchaState is RequestState.Loading,
                contentColor = AppColors.colorScheme.textPrimary,
                focusRequester = codeFocusRequester,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() }
                    if (filtered.length <= 6) {
                        verificationCode = filtered
                        isCodeError = false
                    }
                    if (filtered.length == 6) passwordFocusRequester.requestFocus()
                },
                onSendCaptcha = {
                    if (isValidPhone(phone)) {
                        registerViewModel.getCaptcha(phone)
                        countDownJob?.cancel()
                        countDown = UserImplConstant.SEND_CAPTCHA
                        countDownJob = scope.launch {
                            while (countDown > 0) {
                                delay(1000)
                                countDown--
                            }
                            countDown = 0
                            registerViewModel.setCaptchaIdle()
                        }
                    } else {
                        isPhoneError = true
                    }
                },
            )

            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.hairline)
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))

            PasswordInput(
                password = password,
                isPasswordError = isPasswordError,
                showPassword = showPassword,
                showVisibility = { showPassword = !showPassword },
                contentColor = AppColors.colorScheme.textPrimary,
                label = stringResource(com.smartwash.feature.user.impl.R.string.password_hint_register),
                modifier = Modifier.focusRequester(passwordFocusRequester),
            ) {
                if (it.length <= 16) password = it
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceXl))

        AppButton(
            text = stringResource(R.string.lite_register_title),
            loading = registerState is RequestState.Loading,
            onClick = {
                isPhoneError = !isValidPhone(phone)
                isCodeError = verificationCode.length != 6
                isPasswordError = password.length < 6 || password.length > 16
                if (!isPhoneError && !isCodeError && !isPasswordError) {
                    view.performHaptic(HapticEffect.MEDIUM)
                    keyboardController?.hide()
                    registerViewModel.userRegister(phone, password, verificationCode)
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
                text = stringResource(R.string.lite_has_account),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
            Text(
                text = stringResource(R.string.lite_login_now),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = AppColors.colorScheme.textGreen,
                modifier = Modifier
                    .pressable(onClick = { navController.popBackStack() }, debounce = false)
                    .padding(horizontal = AppDimens.spaceXxs, vertical = AppDimens.spaceMd)
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceXxl))
    }
}

/**
 * 验证码输入行 — 对齐 §3.7：输入正文 16sp、前缀图标 16dp、尾缀发送为描边按钮
 * （1dp 描边 + 12dp 圆角 + 高 40dp + 横向 14dp），倒计时/加载禁用态降至 .55 透明度。
 */
@Composable
private fun LiteVerificationCodeRow(
    code: String,
    isCodeError: Boolean,
    countDown: Int,
    captchaLoading: Boolean,
    contentColor: Color,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onSendCaptcha: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isCodeError) ErrorLight else contentColor.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            BasicTextField(
                value = code,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = contentColor
                ),
                cursorBrush = SolidColor(if (isCodeError) ErrorLight else contentColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (code.isEmpty()) {
                            Text(
                                text = stringResource(com.smartwash.feature.user.impl.R.string.verification_code_hint),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))

            val sendEnabled = countDown == 0 && !captchaLoading
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .then(
                        if (sendEnabled) Modifier.pressable(onClick = onSendCaptcha, debounce = false)
                        else Modifier
                    )
                    .border(
                        width = 1.dp,
                        color = if (sendEnabled) contentColor.copy(alpha = 0.3f)
                        else contentColor.copy(alpha = 0.3f * 0.55f),
                        shape = RoundedCornerShape(AppDimens.radiusMd)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (countDown > 0) {
                        stringResource(com.smartwash.feature.user.impl.R.string.countdown_format, countDown)
                    } else {
                        stringResource(com.smartwash.feature.user.impl.R.string.get_verification_code)
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = contentColor.copy(
                        alpha = if (sendEnabled) 0.92f else 0.92f * 0.55f
                    )
                )
            }
        }

        if (isCodeError) {
            Text(
                text = stringResource(com.smartwash.feature.user.impl.R.string.invalid_verification_code),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = ErrorLight,
                modifier = Modifier.padding(top = AppDimens.spaceXxs)
            )
        }
    }
}
