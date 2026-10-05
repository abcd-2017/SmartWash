package com.smartwash.feature.user.impl.ui.login

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
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
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.GlassBg
import com.smartwash.common.ui.theme.GlassBgSubtle
import com.smartwash.common.ui.theme.GlassBorder
import com.smartwash.common.ui.theme.GlassBorderSubtle
import com.smartwash.common.ui.theme.GlassTextDisabled
import com.smartwash.common.ui.theme.GlassTextHint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.PasswordInput
import com.smartwash.common.ui.components.PhoneNumberInput
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.isValidPhone
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressScale

private val GradientTop = AuthGradientTop
private val GradientBottom = AuthGradientBottom

// 认证页白色 CTA 按钮色
private val AuthCtaText = Color(0xFF1E8C5C)
// 认证页底部文字色
private val AuthBottomText = Color.White.copy(alpha = 0.55f)
private val AuthBottomTextActive = Color.White.copy(alpha = 0.9f)

@Composable
fun LoginPage(
    navController: NavController,
    loginViewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = currentView()

    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPhoneError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }

    val loginState by loginViewModel.loginState.collectAsState()
    val loginButtonInteractionSource = remember { MutableInteractionSource() }
    val registerEntryInteractionSource = remember { MutableInteractionSource() }

    // 已有 token 直接进首页（suspend 读取，不阻塞主线程；T5.3 起经 ViewModel 自取，
    // 不再由宿主传 SessionManager——app 壳不触碰用户域实现类型）。
    // 重启时学校信息检查也在此处一并完成（startDestination 恒为登录页，本分支即
    // 「已登录用户重启 app」的唯一入口），避免壳层并行检查造成双跳转竞态。
    LaunchedEffect(Unit) {
        if (loginViewModel.hasSavedToken()) {
            // 确认未填写才强制补填；网络失败无法确认（null）时放行进首页，不误拦截
            if (loginViewModel.schoolInfoMissing() == true) {
                navController.navigate(UserRoute.UpdateUserInfo.text) {
                    popUpTo(UserRoute.Login.text) { inclusive = true }
                }
            } else {
                navController.navigate(ShellRoute.HOME) {
                    popUpTo(UserRoute.Login.text) { inclusive = true }
                }
            }
        }
    }

    // 状态驱动的副作用统一放 LaunchedEffect，禁止在组合期直接弹 Toast/回写状态
    LaunchedEffect(loginState) {
        when (loginState) {
            is RequestState.Success -> {
                showPassword = false
                Toast.makeText(context, context.getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                // 顺序不能颠倒：先在本协程完成去向检查（此刻 key 仍为 Success，协程存活，
                // 网络挂起安全），完成导航后才 resetLoginState。若先 reset，key 变化会取消
                // 本协程，挂起中的 getUserInfo 被 Canceled，导航永不执行（表现为点击登录无反应）。
                val schoolMissing = loginViewModel.schoolInfoMissing() == true
                if (schoolMissing) {
                    navController.navigate(UserRoute.UpdateUserInfo.text) {
                        popUpTo(UserRoute.Login.text) { inclusive = true }
                    }
                } else {
                    navController.navigate(ShellRoute.HOME) {
                        popUpTo(UserRoute.Login.text) { inclusive = true }
                    }
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

            // 品牌标识 — 96dp 单层圆形 + 自绘洗衣机 SVG
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(GlassBgSubtle)
                    .border(1.dp, GlassBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(com.smartwash.common.ui.R.drawable.ic_washing_machine),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.brand_name),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.brand_subtitle),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(34.dp))

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
                    // 实时校验：只在已显示错误后实时更新，避免输入中就报错
                    isPhoneError = if (it.length == 11) !isValidPhone(it) else isPhoneError
                    // 输入完成自动跳转到密码框
                    if (it.length == 11 && isValidPhone(it)) {
                        passwordFocusRequester.requestFocus()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )

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

                // 登录按钮 — 白色背景 + 品牌深绿文字 + 阴影
                Button(
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
                    interactionSource = loginButtonInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(4.dp, RoundedCornerShape(14.dp))
                        .pressScale(loginButtonInteractionSource, 0.97f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = AuthCtaText,
                    )
                ) {
                    when (loginState) {
                        is RequestState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = AuthCtaText,
                                strokeWidth = 2.dp
                            )
                        }

                        else -> {
                            Text(
                                stringResource(R.string.login_button),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 注册入口 — 只有点击"立即注册"才跳转
            Row(
                modifier = Modifier.padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.no_account),
                    fontSize = 13.sp,
                    color = AuthBottomText
                )
                Text(
                    text = stringResource(R.string.register_now),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AuthBottomTextActive,
                    modifier = Modifier
                        .clickable(
                            interactionSource = registerEntryInteractionSource,
                            indication = null
                        ) {
                            navController.navigate(UserRoute.Register.text)
                        }
                        .pressScale(registerEntryInteractionSource, 0.97f)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
