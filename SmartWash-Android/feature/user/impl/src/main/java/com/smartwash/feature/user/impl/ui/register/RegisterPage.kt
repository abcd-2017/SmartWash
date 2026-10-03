package com.smartwash.feature.user.impl.ui.register

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.ErrorLight
import com.smartwash.common.ui.theme.GlassBg
import com.smartwash.common.ui.theme.GlassBgSubtle
import com.smartwash.common.ui.theme.GlassBorder
import com.smartwash.common.ui.theme.GlassBorderSubtle
import com.smartwash.common.ui.theme.GlassInput
import com.smartwash.common.ui.theme.GlassTextActive
import com.smartwash.common.ui.theme.GlassTextDisabled
import com.smartwash.common.ui.theme.GlassTextHint
import com.smartwash.common.ui.theme.GlassTextSecondary
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.PasswordInput
import com.smartwash.common.ui.components.PhoneNumberInput
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.common.model.RequestState
import com.smartwash.common.utils.isValidPhone
import com.smartwash.common.ui.navigation.ShellRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GradientTop = AuthGradientTop
private val GradientBottom = AuthGradientBottom

// 认证页白色 CTA 按钮色
private val AuthCtaText = Color(0xFF1E8C5C)
// 认证页底部文字色
private val AuthBottomText = Color.White.copy(alpha = 0.55f)
private val AuthBottomTextActive = Color.White.copy(alpha = 0.9f)
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

    val captchaState by registerViewModel.captchaState.collectAsState()
    val registerState by registerViewModel.registerState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
                navController.navigate(ShellRoute.HOME) {
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

    val glassShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(GradientTop, GradientBottom))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { navController.popBackStack() },
                    tint = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = stringResource(R.string.register),
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // 品牌标识 — 92dp 单层圆形
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(GlassBgSubtle)
                    .border(1.dp, GlassBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                color = Color.White.copy(alpha = 0.6f)
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
                    onValueChange = {
                        if (it.length <= 6) {
                            verificationCode = it
                            isVerificationCodeError = false
                        }
                        // 输入完成自动跳转到密码框
                        if (it.length == 6) {
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
                    if (it.length <= 16) password = it
                    isPasswordError = if (it.isEmpty()) false
                    else it.length < 6 || it.length > 16
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 注册按钮 — 白色背景 + 品牌深绿文字 + 阴影
                Button(
                    onClick = {
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
                        .height(50.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
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
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 登录入口
            TextButton(
                onClick = {
                    navController.popBackStack()
                }
            ) {
                Text(
                    stringResource(R.string.has_account),
                    fontSize = 13.sp,
                    color = AuthBottomText
                )
                Text(
                    stringResource(R.string.login_now),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AuthBottomTextActive
                )
            }

            Spacer(modifier = Modifier.weight(1f))
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
    onSendCaptcha: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = verificationCode,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = isVerificationCodeError,
            supportingText = if (isVerificationCodeError) {
                { Text(stringResource(R.string.invalid_verification_code), color = ErrorLight) }
            } else null,
            leadingIcon = {
                Icon(
                    Icons.Rounded.Key,
                    contentDescription = null,
                    tint = if (isVerificationCodeError) ErrorLight
                    else GlassTextHint
                )
            },
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.verification_code), color = GlassTextSecondary)
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = Color.White,
                errorCursorColor = ErrorLight,
                errorLeadingIconColor = ErrorLight,
                errorSupportingTextColor = ErrorLight,
            )
        )

        TextButton(
            onClick = onSendCaptcha,
            enabled = captchaState !is RequestState.Loading,
            modifier = Modifier
                .border(1.dp, GlassInput, RoundedCornerShape(12.dp))
                .height(40.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = if (captchaState is RequestState.Loading) stringResource(R.string.countdown_format, countDown)
                else stringResource(R.string.get_verification_code),
                style = MaterialTheme.typography.labelMedium,
                color = if (captchaState is RequestState.Loading)
                    GlassTextSecondary
                else GlassTextActive
            )
        }
    }
}
