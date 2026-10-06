package com.smartwash.feature.user.impl.ui.register

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientMid
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.ErrorLight
import com.smartwash.common.ui.theme.GlassBg
import com.smartwash.common.ui.theme.GlassBgSubtle
import com.smartwash.common.ui.theme.GlassBorder
import com.smartwash.common.ui.theme.GlassBorderSubtle
import com.smartwash.common.ui.theme.GlassTextDisabled
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.PasswordInput
import com.smartwash.common.ui.components.PhoneNumberInput
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.isValidPhone
import com.smartwash.common.utils.rememberDebouncedClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GradientTop = AuthGradientTop
private val GradientBottom = AuthGradientBottom

// 认证页白色 CTA 按钮色
private val AuthCtaText = Color(0xFF1E8C5C)
// 认证页底部文字色
private val AuthBottomText = Color.White.copy(alpha = 0.78f)
private val AuthBottomTextActive = Color.White.copy(alpha = 0.92f)
// 进度条非激活态
private val ProgressInactive = Color.White.copy(alpha = 0.28f)

@Composable
fun RegisterPage(
    navController: NavController,
    registerViewModel: RegisterViewModel = hiltViewModel()
) {
    var phone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPhoneError by remember { mutableStateOf(false) }
    var isVerificationCodeError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val verificationCodeFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    var countDown by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // 连点防抖：本页返回箭头/去登录均为 popBackStack 出栈，转场残影期被重复触发会连 Login 一起弹掉，
    // 弹空导航栈导致黑屏（已确认 bug 本体），故用比转场更长的 800ms 窗口
    val debouncedBackClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
        navController.popBackStack()
    }
    val debouncedLoginClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
        navController.popBackStack()
    }

    val captchaState by registerViewModel.captchaState.collectAsState()
    val registerState by registerViewModel.registerState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = currentView()

    // D-R5 品牌渐变页深浅色同款：状态栏图标强制白色；离页恢复主题默认（与 LoginPage 对齐）
    val darkTheme = isSystemInDarkTheme()
    DisposableEffect(darkTheme) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        onDispose {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // 状态驱动的副作用统一放 LaunchedEffect，禁止在组合期直接弹 Toast/回写状态
    LaunchedEffect(captchaState) {
        when (captchaState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.captcha_sent), Toast.LENGTH_SHORT).show()
                registerViewModel.setCaptchaIdle()
            }

            is RequestState.Error -> {
                Toast.makeText(
                    context,
                    (captchaState as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                registerViewModel.setCaptchaIdle()
                countDown = UserImplConstant.SEND_CAPTCHA
            }

            else -> {}
        }
    }

    LaunchedEffect(registerState) {
        when (registerState) {
            is RequestState.Success -> {
                showPassword = false
                Toast.makeText(context, context.getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                registerViewModel.setRegisterIdle()
                // 注册成功后跳转填写学校学号信息
                navController.navigate(UserRoute.UpdateUserInfo.text) {
                    popUpTo(UserRoute.Login.text) { inclusive = true }
                }
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

    val glassShape = RoundedCornerShape(AppDimens.radiusXl)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(0f to GradientTop, 0.5f to AuthGradientMid, 1f to GradientBottom)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部导航栏 — 添加 statusBarsPadding 防止与状态栏重叠
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // D-R9 返回箭头包进 48dp 热区，右侧占位同宽保持标题视觉居中
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = debouncedBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = stringResource(R.string.register),
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            // D-R2 布局顶部流式：brand 距 nav 行 16px
            Spacer(modifier = Modifier.height(16.dp))

            // 品牌标识 — 92dp 单层圆形
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(GlassBgSubtle)
                    .border(1.dp, GlassBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // D-R1 品牌图标：设计稿屏 2「水滴+加号」，替代 emoji
                Icon(
                    painter = painterResource(com.smartwash.common.ui.R.drawable.ic_water_add),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = Color.White
                )
            }

            // D-R2 brand margin-bottom 14px
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.create_account),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = stringResource(R.string.start_laundry_journey),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.78f)
            )

            // 进度条
            Spacer(modifier = Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 激活态 — 纯白
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ProgressInactive)
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ProgressInactive)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 毛玻璃输入卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(glassShape)
                    .background(GlassBgSubtle)
                    .border(1.dp, GlassBorderSubtle, glassShape)
                    .padding(20.dp, 16.dp)
            ) {
                PhoneNumberInput(
                    phone = phone,
                    isPhoneError = isPhoneError,
                    contentColor = Color.White
                ) {
                    if (it.length <= 11) phone = it
                    // 只在输入完成（11位）或清空时验证
                    isPhoneError = when {
                        it.isEmpty() -> false
                        it.length == 11 -> !isValidPhone(it)
                        else -> false
                    }
                    // 输入完成自动跳转到验证码框
                    if (it.length == 11 && isValidPhone(it)) {
                        verificationCodeFocusRequester.requestFocus()
                    }
                }

                GlassDivider()

                // 验证码行
                VerificationCodeRow(
                    verificationCode = verificationCode,
                    isVerificationCodeError = isVerificationCodeError,
                    countDown = countDown,
                    captchaState = captchaState,
                    focusRequester = verificationCodeFocusRequester,
                    onValueChange = { newValue ->
                        // 只允许输入数字，过滤非数字字符
                        val filtered = newValue.filter { it.isDigit() }
                        if (filtered.length <= 6) {
                            verificationCode = filtered
                            isVerificationCodeError = false
                        }
                        // 输入完成自动跳转到密码框
                        if (filtered.length == 6) {
                            passwordFocusRequester.requestFocus()
                        }
                    },
                    onSendCaptcha = {
                        // 先校验手机号格式，通过后再请求验证码，避免无效手机号也发起请求
                        if (isValidPhone(phone)) {
                            registerViewModel.getCaptcha(phone)
                            countDown = UserImplConstant.SEND_CAPTCHA
                            coroutineScope.launch {
                                while (countDown > 0) {
                                    delay(1000)
                                    countDown--
                                }
                                // 倒计时结束归零，允许重新发送
                                countDown = 0
                                withContext(Dispatchers.Main) {
                                    registerViewModel.setCaptchaIdle()
                                }
                            }
                        } else {
                            isPhoneError = true
                        }
                    }
                )

                GlassDivider()

                PasswordInput(
                    password = password,
                    isPasswordError = isPasswordError,
                    showPassword = showPassword,
                    showVisibility = { showPassword = !showPassword },
                    contentColor = Color.White,
                    modifier = Modifier.focusRequester(passwordFocusRequester)
                ) {
                    // 只限制长度，不实时显示错误（错误只在点击提交按钮时检测）
                    if (it.length <= 16) password = it
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 注册按钮 — 白色背景 + 品牌深绿文字 + 阴影
                Button(
                    onClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                        isPhoneError = !isValidPhone(phone)
                        isVerificationCodeError = verificationCode.length != 6
                        isPasswordError = password.length < 6 || password.length > 16

                        if (!isPhoneError && !isVerificationCodeError && !isPasswordError) {
                            keyboardController?.hide()
                            registerViewModel.userRegister(phone, password, verificationCode)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(AppDimens.buttonRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = AuthCtaText,
                    )
                ) {
                    when (registerState) {
                        is RequestState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = AuthCtaText,
                            strokeWidth = 2.dp
                        )

                        else -> {
                            Text(
                                stringResource(R.string.register),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 登录入口 — 只有点击"去登录"才跳转
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.has_account),
                    fontSize = 13.sp,
                    color = AuthBottomText
                )
                Text(
                    text = stringResource(R.string.login_now),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AuthBottomTextActive,
                    modifier = Modifier
                        .clickable(onClick = debouncedLoginClick)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

@Composable
private fun VerificationCodeRow(
    verificationCode: String,
    isVerificationCodeError: Boolean,
    countDown: Int,
    captchaState: RequestState,
    focusRequester: FocusRequester = FocusRequester(),
    onValueChange: (String) -> Unit,
    onSendCaptcha: () -> Unit,
    contentColor: Color = Color.White
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 紧凑输入行：总高 48dp、垂直居中（对齐 PhoneNumberInput，D-R4）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isVerificationCodeError) ErrorLight
                else contentColor.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = verificationCode,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = contentColor),
                cursorBrush = SolidColor(if (isVerificationCodeError) ErrorLight else contentColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (verificationCode.isEmpty()) {
                            Text(
                                text = stringResource(R.string.verification_code),
                                fontSize = 14.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // D-R3 尾缀行内发送按钮：去掉 TextButton 边框块，行高 48dp 保证垂直热区、水平 padding 补热区
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(
                        enabled = countDown == 0 && captchaState !is RequestState.Loading,
                        onClick = onSendCaptcha
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (countDown > 0) stringResource(R.string.countdown_format, countDown)
                    else stringResource(R.string.get_verification_code),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor.copy(alpha = 0.92f)
                )
            }
        }
        if (isVerificationCodeError) {
            // 错误文案沿用既有 supportingText 内容（原 TextField supportingText 平替）
            Text(
                text = stringResource(R.string.invalid_verification_code),
                fontSize = 13.sp,
                color = ErrorLight,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
