package com.smartwash.ui.page.login

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // 应用图标
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalLaundryService,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "智能洗衣",
                style = MaterialTheme.typography.displaySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "让生活更轻松",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(visible = true) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        PhoneNumberInput(phone, isPhoneError) {
                            if (it.length <= 11) phone = it
                            isPhoneError = if (it.isEmpty()) false
                            else !isValidPhone(phone)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

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
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                when (loginState) {
                    is RequestState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    }

                    else -> {
                        Text(
                            "登录",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                onClick = {
                    navController.navigate(PageConstant.Register.text) { navController.navigateUp() }
                }
            ) {
                Text(
                    "没有账号？立即注册",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
