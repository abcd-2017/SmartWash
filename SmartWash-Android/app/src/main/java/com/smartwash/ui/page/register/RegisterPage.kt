package com.smartwash.ui.page.register

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

    var countDown by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val captchaState by registerViewModel.captchaState.collectAsState()
    val captchaValue by registerViewModel.captchaValue.collectAsState()
    val registerState by registerViewModel.registerState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    when (captchaState) {
        is RequestState.Success -> {
            verificationCode = captchaValue
        }

        is RequestState.Error -> {
            Toast.makeText(
                LocalContext.current,
                (captchaState as RequestState.Error).message,
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
                Toast.makeText(context, "注册成功", Toast.LENGTH_SHORT).show()
                registerViewModel.setRegisterIdle()
                navController.popBackStack()
                navController.navigate(PageConstant.Home.text)
            }
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (registerState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            registerViewModel.setRegisterIdle()
        }

        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 纯白头部区域（无渐变）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 52.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF0FDF6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✨", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "创建账号",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "开启智能洗衣之旅",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 三步进度条
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF2EB886))
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE0E5E2))
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE0E5E2))
                    )
                }
            }

            // 白色表单卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.06f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                PhoneNumberInput(phone, isPhoneError) {
                    if (it.length <= 11) phone = it
                    isPhoneError = if (it.isEmpty()) false
                    else !isValidPhone(phone)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 验证码行
                VerificationCodeRow(
                    verificationCode = verificationCode,
                    isVerificationCodeError = isVerificationCodeError,
                    countDown = countDown,
                    captchaState = captchaState,
                    onValueChange = {
                        if (it.length <= 6) {
                            verificationCode = it
                            isVerificationCodeError = false
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

                Spacer(modifier = Modifier.height(20.dp))

                PasswordInput(
                    password,
                    isPasswordError,
                    showPassword,
                    showVisibility = { showPassword = !showPassword }
                ) {
                    if (it.length <= 16) password = it
                    isPasswordError = if (it.isEmpty()) false
                    else it.length < 6 || it.length > 16
                }
            }

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
                    .height(52.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = Color(0xFF2EB886).copy(alpha = 0.3f),
                        spotColor = Color(0xFF2EB886).copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2EB886),
                    contentColor = Color.White
                )
            ) {
                when (registerState) {
                    is RequestState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                    else -> {
                        Text("注 册", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                onClick = {
                    navController.popBackStack()
                    navController.navigate(PageConstant.Login.text)
                }
            ) {
                Text(
                    "已有账号？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "立即登录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2EB886)
                )
            }
        }
    }
}

@Composable
private fun VerificationCodeRow(
    verificationCode: String,
    isVerificationCodeError: Boolean,
    countDown: Int,
    captchaState: RequestState,
    onValueChange: (String) -> Unit,
    onSendCaptcha: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "验证码",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = verificationCode,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = isVerificationCodeError,
                supportingText = if (isVerificationCodeError) {
                    { Text("请输入正确的验证码") }
                } else null,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = null,
                        tint = if (isVerificationCodeError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                placeholder = { Text("输入验证码") }
            )

            Button(
                onClick = onSendCaptcha,
                enabled = captchaState !is RequestState.Loading,
                modifier = Modifier
                    .width(120.dp)
                    .height(56.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF0FDF6),
                    contentColor = Color(0xFF2EB886),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = if (captchaState is RequestState.Loading) "${countDown}s"
                    else "获取验证码",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
