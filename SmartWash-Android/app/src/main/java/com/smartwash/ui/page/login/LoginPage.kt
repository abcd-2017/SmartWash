package com.smartwash.ui.page.login

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.ui.common.PasswordInput
import com.smartwash.ui.common.PhoneNumberInput
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.AppConstant
import com.smartwash.utils.RequestState
import com.smartwash.utils.SharePreferenceUtils
import com.smartwash.utils.isValidPhone

@Composable
fun LoginPage(
    navController: NavController,
    loginViewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPhoneError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val loginState by loginViewModel.loginState.collectAsState()

    LaunchedEffect(Unit) {
        val token = SharePreferenceUtils.getDataBlocking(AppConstant.TOKEN, "")
        if (token.isNotBlank()) {
            navController.navigate(PageConstant.Home.text) { navController.navigateUp() }
        }
    }

    when (loginState) {
        is RequestState.Success -> {
            LaunchedEffect(Unit) {
                showPassword = false
                Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                loginViewModel.resetLoginState()
                navController.navigate(PageConstant.Home.text) {
                    navController.navigateUp()
                }
            }
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (loginState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            loginViewModel.resetLoginState()
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
            // 品牌渐变头部
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2EB886),
                                Color(0xFF1A9E6E),
                                Color(0xFF158A5C)
                            )
                        )
                    )
                    .padding(top = 44.dp, bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalLaundryService,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "智能洗衣",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "智慧校园 · 轻松洗衣",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // 白色卡片（上移重叠渐变区）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-24).dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.06f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                PhoneNumberInput(phone, isPhoneError) {
                    if (it.length <= 11) phone = it
                    isPhoneError = if (it.isEmpty()) false
                    else !isValidPhone(phone)
                }

                Spacer(modifier = Modifier.height(24.dp))

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

            // 登录按钮
            Button(
                onClick = {
                    isPhoneError = !isValidPhone(phone)
                    isPasswordError = password.isEmpty() || password.length < 6 || password.length > 16

                    if (!isPhoneError && !isPasswordError) {
                        keyboardController?.hide()
                        loginViewModel.loginUser(phone, password)
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
                when (loginState) {
                    is RequestState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }

                    else -> {
                        Text(
                            "登 录",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    navController.navigate(PageConstant.Register.text) { navController.navigateUp() }
                }
            ) {
                Text(
                    "没有账号？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "立即注册",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2EB886)
                )
            }
        }
    }
}
