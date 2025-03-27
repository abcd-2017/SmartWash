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
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var isLoading by remember { mutableStateOf(false) }
    var countDown by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val captchaState by registerViewModel.captchaState.collectAsState()
    val registerState by registerViewModel.registerState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    //验证码状态
    when (captchaState) {
        is CaptchaState.Success -> {
            verificationCode = (captchaState as CaptchaState.Success).message
        }

        is CaptchaState.Error -> {
            Toast.makeText(
                LocalContext.current,
                (captchaState as CaptchaState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            registerViewModel.setCaptchaIdle()
            countDown = AppConstant.SEND_CAPTCHA
        }

        else -> {}
    }

    //注册状态
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
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalLaundryService,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "创建账号",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "开启智能洗衣之旅",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(34.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 手机号输入框
                    PhoneNumberInput(phone, isPhoneError) {
                        if (it.length <= 11) phone = it
                        isPhoneError = if (it.isEmpty()) false
                        else !isValidPhone(phone)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 验证码输入框
                    CaptchaInput(
                        verificationCode,
                        isVerificationCodeError,
                        countDown,
                        captchaState,
                        onValueChange = {
                            if (it.length <= 6) {
                                verificationCode = it
                                isVerificationCodeError = false
                            }
                        }
                    ) {
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // 密码输入框
                    PasswordInput(
                        password,
                        isPasswordError,
                        showPassword,
                        showVisibility = {
                            showPassword = !showPassword
                        }) {
                        if (it.length <= 16) password = it
                        isPasswordError = if (it.isEmpty()) false
                        else !isValidPhone(phone)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            RegisterButton(registerState) {
                isPhoneError = !isValidPhone(phone)
                isVerificationCodeError = verificationCode.length != 6
                isPasswordError = password.length < 6 || password.length > 16

                if (!isPhoneError && !isVerificationCodeError &&
                    !isPasswordError
                ) {
                    isLoading = true
                    keyboardController?.hide()
                    registerViewModel.userRegister(phone, password, verificationCode)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = {
                    //跳转到登录页
                    navController.popBackStack()
                    navController.navigate(PageConstant.Login.text)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    "已有账号？立即登录",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }
     }
}

//注册按钮
@Composable
fun RegisterButton(
    registerState: RequestState,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        when (registerState) {
            is RequestState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )

            else -> {
                Text(
                    "注册",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

//验证码输入框
@Composable
fun CaptchaInput(
    verificationCode: String,
    isVerificationCodeError: Boolean,
    countDown: Int,
    captchaState: CaptchaState,
    onValueChange: (String) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = verificationCode,
            onValueChange = onValueChange,
            label = { Text("验证码") },
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
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = onClick,
            enabled = when (captchaState) {
                is CaptchaState.Idle -> true
                else -> false
            },
            modifier = Modifier
                .width(120.dp)
                .padding(top = 6.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                when (captchaState) {
                    is CaptchaState.Idle -> {
                        "获取验证码"
                    }

                    else -> {
                        "${countDown}s"
                    }

                },
                fontSize = 14.sp
            )
        }
    }
}

