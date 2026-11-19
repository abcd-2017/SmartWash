package com.smartwash.feature.user.impl.ui.resetpassword

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.smartwash.common.ui.theme.ErrorLight
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientMid
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.GlassBgCard
import com.smartwash.common.ui.theme.GlassBgSubtle
import com.smartwash.common.ui.theme.GlassBorderSubtle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.AppFieldVariant
import com.smartwash.common.ui.components.AppPasswordTextField
import com.smartwash.common.ui.components.AppTextField
import com.smartwash.common.ui.components.AppVerificationCodeField
import com.smartwash.common.ui.components.AuthCtaButton
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.isValidPhone
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.HapticEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 重置密码页（忘记密码）：手机号 → 验证码（60s 客户端倒计时）→ 新密码。
 * 视觉与登录/注册同模板（品牌渐变 + 毛玻璃卡）。重置成功后 ViewModel 已清会话并
 * 广播登录失效，导航由壳层统一 pop 回登录页，本页不自理跳转。
 */
@Composable
fun ResetPasswordPage(
    navController: NavController,
    resetPasswordViewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    // 手机号/验证码 saveable：返回再进不必重输（新密码按隐私红线不落 saved state）
    var phone by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isPhoneError by remember { mutableStateOf(false) }
    var isVerificationCodeError by remember { mutableStateOf(false) }
    var isNewPasswordError by remember { mutableStateOf(false) }
    val verificationCodeFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    var countDown by remember { mutableStateOf(0) }
    // 倒计时任务句柄：发送失败时取消，使按钮立刻恢复可点（对齐注册页）
    var countDownJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 连点防抖：返回为 popBackStack 出栈，防转场残影期重复触发弹空导航栈（对齐注册页）
    val debouncedBackClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
        navController.popBackStack()
    }

    val captchaState by resetPasswordViewModel.captchaState.collectAsState()
    val captchaMessage by resetPasswordViewModel.captchaMessage.collectAsState()
    val resetState by resetPasswordViewModel.resetState.collectAsState()
    val context = LocalContext.current
    val view = currentView()

    // 品牌渐变页深浅色同款：状态栏图标强制白色；离页恢复主题默认（对齐登录/注册页）
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
                // 优先展示接口 data 原文（本地 stub 模式含「本地演示验证码：{code}」）
                Toast.makeText(
                    context,
                    captchaMessage ?: context.getString(R.string.captcha_sent),
                    Toast.LENGTH_SHORT
                ).show()
                resetPasswordViewModel.setCaptchaIdle()
            }

            is RequestState.Error -> {
                Toast.makeText(
                    context,
                    (captchaState as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                // 发送失败立即可重发：取消倒计时而不是重置为 60s 继续锁按钮
                countDownJob?.cancel()
                countDownJob = null
                countDown = 0
                resetPasswordViewModel.setCaptchaIdle()
            }

            else -> {}
        }
    }

    LaunchedEffect(resetState) {
        when (resetState) {
            is RequestState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.reset_password_success),
                    Toast.LENGTH_SHORT
                ).show()
                resetPasswordViewModel.setResetIdle()
                // 会话已清 + 登录失效已广播（ViewModel），壳层统一 pop 回登录页，此处不再导航
            }

            is RequestState.Error -> {
                Toast.makeText(
                    context,
                    (resetState as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                resetPasswordViewModel.setResetIdle()
            }

            else -> {}
        }
    }

    val glassShape = RoundedCornerShape(AppDimens.radiusLg)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(0f to AuthGradientTop, 0.5f to AuthGradientMid, 1f to AuthGradientBottom)
            )
            // IME 遮挡 CTA 修复：键盘弹出时内容区整体上收 + 可滚动（对齐注册页）
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部导航栏 — statusBarsPadding 防止与状态栏重叠（对齐注册页）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回箭头包进 48dp 热区，右侧占位同宽保持标题视觉居中（防抖已注入）
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = (-20).dp)
                        .pressable(onClick = debouncedBackClick, debounce = false),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = stringResource(R.string.reset_password),
                    modifier = Modifier.weight(1f),
                    // 页头 18/24/600 = PageHeader 档（与注册页同模板）
                    style = AppTextStyles.PageHeader,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 品牌标识 — 92dp 单层圆形，与登录（96dp）/注册（92dp）同模板。
            // 鸿蒙端本页为 sys.symbol.lock 42/92；安卓此前缺失，现补自绘锁形品牌位对齐。
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(GlassBgSubtle)
                    .border(1.dp, GlassBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(com.smartwash.common.ui.R.drawable.ic_lock_brand),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 毛玻璃输入卡片 — 内边距垂直 20 / 水平 16（DESIGN-SPEC v0.4 §5 玻璃卡统一值，
            // 与登录/注册同模板；双参重载避免方向反转）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(glassShape)
                    .background(GlassBgCard)
                    .border(1.dp, GlassBorderSubtle, glassShape)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                // 字段标签行复用 common:ui 既有 string 资源（nonTransitiveRClass 全限定引用）
                Text(
                    text = stringResource(com.smartwash.common.ui.R.string.phone_hint),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                AppTextField(
                    value = phone,
                    onValueChange = {
                        if (it.length <= 11) phone = it
                        isPhoneError = when {
                            it.isEmpty() -> false
                            it.length == 11 -> !isValidPhone(it)
                            else -> false
                        }
                        // 输入完成自动跳转到验证码框
                        if (it.length == 11 && isValidPhone(it)) {
                            verificationCodeFocusRequester.requestFocus()
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    variant = AppFieldVariant.GLASS,
                    isError = isPhoneError,
                    leadingIcon = Icons.Rounded.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = {
                        AnimatedVisibility(
                            visible = isPhoneError,
                            enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                                expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
                            exit = fadeOut(motionSpec(exitTween(140))) +
                                shrinkVertically(motionSpec(exitTween(140))),
                        ) {
                            Text(
                                text = stringResource(com.smartwash.common.ui.R.string.invalid_phone),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = ErrorLight
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                GlassDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // 验证码行（60s 客户端倒计时，样式与注册页同模板）
                Text(
                    text = stringResource(R.string.verification_code_hint),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                AppVerificationCodeField(
                    value = verificationCode,
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
                    sendText = stringResource(R.string.get_verification_code),
                    countdownText = stringResource(R.string.countdown_format, countDown),
                    onSendCaptcha = {
                        // 先校验手机号格式，通过后再请求验证码，避免无效手机号也发起请求
                        if (isValidPhone(phone)) {
                            resetPasswordViewModel.getCaptcha(phone)
                            // 同步置倒计时（立即禁用按钮防连点），失败时经句柄取消
                            countDownJob?.cancel()
                            countDown = UserImplConstant.SEND_CAPTCHA
                            countDownJob = coroutineScope.launch {
                                while (countDown > 0) {
                                    delay(1000)
                                    countDown--
                                }
                                // 倒计时结束归零，允许重新发送
                                countDown = 0
                                resetPasswordViewModel.setCaptchaIdle()
                            }
                        } else {
                            isPhoneError = true
                        }
                    },
                    // focusRequester 经 modifier 下传（保留原 VerificationCodeRow 的聚焦自动跳转）
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .focusRequester(verificationCodeFocusRequester),
                    variant = AppFieldVariant.GLASS,
                    countDown = countDown,
                    sendEnabled = countDown == 0 && captchaState !is RequestState.Loading,
                    isError = isVerificationCodeError,
                    supportingText = {
                        AnimatedVisibility(
                            visible = isVerificationCodeError,
                            enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                                expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
                            exit = fadeOut(motionSpec(exitTween(140))) +
                                shrinkVertically(motionSpec(exitTween(140))),
                        ) {
                            Text(
                                text = stringResource(R.string.invalid_verification_code),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = ErrorLight
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                GlassDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.reset_new_password),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                AppPasswordTextField(
                    value = newPassword,
                    onValueChange = {
                        // 只限制长度，不实时显示错误（错误只在点击提交按钮时检测；超长由组件 maxLength 截断）
                        newPassword = it
                        // 对齐登录页实时清除机制：补足有效长度即清除行内错误
                        isNewPasswordError = if (it.length >= 6) false else isNewPasswordError
                    },
                    // 最大位数由组件 maxLength 固定 16，无需页面截断
                    modifier = Modifier.focusRequester(passwordFocusRequester),
                    variant = AppFieldVariant.GLASS,
                    isError = isNewPasswordError,
                    supportingText = {
                        AnimatedVisibility(
                            visible = isNewPasswordError,
                            enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                                expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
                            exit = fadeOut(motionSpec(exitTween(140))) +
                                shrinkVertically(motionSpec(exitTween(140))),
                        ) {
                            Text(
                                text = stringResource(com.smartwash.common.ui.R.string.invalid_password),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = ErrorLight
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 重置密码按钮 — 玻璃卡主 CTA（与登录/注册同模板，防抖经 800ms 动作档注入）
                AuthCtaButton(
                    text = stringResource(R.string.reset_password),
                    loading = resetState is RequestState.Loading,
                    onClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                        isPhoneError = !isValidPhone(phone)
                        isVerificationCodeError = verificationCode.length != 6
                        isNewPasswordError = newPassword.length < 6 || newPassword.length > 16

                        if (!isPhoneError && !isVerificationCodeError && !isNewPasswordError) {
                            view.performHaptic(HapticEffect.MEDIUM)
                            resetPasswordViewModel.resetPassword(phone, verificationCode, newPassword)
                        } else {
                            view.performHaptic(HapticEffect.ERROR)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GlassDivider() {
    // 玻璃卡内分隔线：1dp 高、白 12%（对齐注册页）
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}
