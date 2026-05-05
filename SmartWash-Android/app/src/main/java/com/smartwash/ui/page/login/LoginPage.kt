package com.smartwash.ui.page.login

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.smartwash.ui.theme.AuthGradientBottom
import com.smartwash.ui.theme.AuthGradientTop
import com.smartwash.ui.theme.GlassBg
import com.smartwash.ui.theme.GlassBgSubtle
import com.smartwash.ui.theme.GlassBorder
import com.smartwash.ui.theme.GlassBorderSubtle
import com.smartwash.ui.theme.GlassTextDisabled
import com.smartwash.ui.theme.GlassTextHint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartwash.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.ui.common.PasswordInput
import com.smartwash.ui.common.PhoneNumberInput
import com.smartwash.ui.page.PageConstant
import com.smartwash.utils.AppConstant
import com.smartwash.utils.RequestState
import com.smartwash.utils.SharePreferenceUtils
import com.smartwash.utils.isValidPhone

private val GradientTop = AuthGradientTop
private val GradientBottom = AuthGradientBottom

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
    val passwordFocusRequester = remember { FocusRequester() }

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
                Toast.makeText(context, context.getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                loginViewModel.resetLoginState()
                navController.navigate(PageConstant.Home.text) {
                    navController.navigateUp()
                }
            }
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
                Icon(
                    imageVector = Icons.Rounded.LocalLaundryService,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.brand_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTextDisabled
            )

            Spacer(modifier = Modifier.height(40.dp))

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
                    // 输入完成自动跳转到密码框
                    if (it.length == 11 && isValidPhone(it)) {
                        passwordFocusRequester.requestFocus()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(0.5.dp)
                        .background(GlassBg)
                )

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
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassBorder,
                        contentColor = Color.White,
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
                                stringResource(R.string.login_button),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // 注册入口
            TextButton(
                onClick = {
                    navController.navigate(PageConstant.Register.text) { navController.navigateUp() }
                }
            ) {
                Text(
                    stringResource(R.string.no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTextHint
                )
                Text(
                    stringResource(R.string.register_now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
