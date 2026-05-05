package com.smartwash.ui.page.register

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.smartwash.ui.theme.AuthGradientBottom
import com.smartwash.ui.theme.AuthGradientTop
import com.smartwash.ui.theme.ErrorLight
import com.smartwash.ui.theme.GlassBg
import com.smartwash.ui.theme.GlassBgSubtle
import com.smartwash.ui.theme.GlassBorder
import com.smartwash.ui.theme.GlassBorderSubtle
import com.smartwash.ui.theme.GlassInput
import com.smartwash.ui.theme.GlassTextActive
import com.smartwash.ui.theme.GlassTextDisabled
import com.smartwash.ui.theme.GlassTextHint
import com.smartwash.ui.theme.GlassTextSecondary
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartwash.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.ui.common.PasswordInput
import com.smartwash.ui.common.PhoneNumberInput
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.AppConstant
import com.smartwash.utils.RequestState
import com.smartwash.utils.isValidPhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GradientTop = AuthGradientTop
private val GradientBottom = AuthGradientBottom

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

    when (captchaState) {
        is RequestState.Success -> {
            Toast.makeText(context, stringResource(R.string.captcha_sent), Toast.LENGTH_SHORT).show()
            registerViewModel.setCaptchaIdle()
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (captchaState as RequestState.Error).getMessage(context),
                Toast.LENGTH_SHORT
            ).show()
            registerViewModel.setCaptchaIdle()
            countDown = AppConstant.SEND_CAPTCHA
        }

        else -> {}
    }

    when (registerState) {
        is RequestState.Success -> {
            LaunchedEffect(Unit) {
                showPassword = false
                Toast.makeText(context, context.getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                registerViewModel.setRegisterIdle()
                navController.popBackStack()
                navController.navigate(PageConstant.Home.text)
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
            Spacer(modifier = Modifier.weight(1f))

            // 品牌标识
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.create_account),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.start_laundry_journey),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTextDisabled
            )

            // 进度条
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GlassInput)
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GlassInput)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 毛玻璃输入卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(glassShape)
                    .background(GlassBgSubtle)
                    .border(1.dp, GlassBorderSubtle, glassShape)
                    .padding(vertical = 8.dp)
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
                        registerViewModel.getCaptcha(phone)
                        if (isValidPhone(phone)) {
                            countDown = AppConstant.SEND_CAPTCHA
                            coroutineScope.launch {
                                while (countDown > 0) {
                                    delay(1000)
                                    countDown--
                                }
                                countDown = AppConstant.SEND_CAPTCHA
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

                Spacer(modifier = Modifier.height(20.dp))

                // 注册按钮
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
                        .padding(horizontal = 16.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassBorder,
                        contentColor = Color.White,
                    )
                ) {
                    when (registerState) {
                        is RequestState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )

                        else -> {
                            Text(stringResource(R.string.register), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // 登录入口
            TextButton(
                onClick = {
                    navController.popBackStack()
                    navController.navigate(PageConstant.Login.text)
                }
            ) {
                Text(
                    stringResource(R.string.has_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTextHint
                )
                Text(
                    stringResource(R.string.login_now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(GlassBg)
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
