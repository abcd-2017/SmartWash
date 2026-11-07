package com.smartwash.feature.user.impl.ui.resetpassword

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientMid
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.ErrorLight
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.GlassBgCard
import com.smartwash.common.ui.theme.GlassBorderSubtle
import com.smartwash.common.ui.theme.GlassInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.smartwash.feature.user.impl.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.common.ui.components.AuthCtaButton
import com.smartwash.common.ui.components.PasswordInput
import com.smartwash.common.ui.components.PhoneNumberInput
import com.smartwash.feature.user.impl.UserImplConstant
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.isValidPhone
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.selectedColor
import com.smartwash.common.utils.HapticEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 退出时长 ≈ 进入（DURATION_MICRO_MS）的 70%（动效语言 v0.4，对齐注册页） */
private const val EXIT_MICRO_MS = 140

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
    var phone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isPhoneError by remember { mutableStateOf(false) }
    var isVerificationCodeError by remember { mutableStateOf(false) }
    var isNewPasswordError by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
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
                    // 页头 22/600 = PageHeader 档（与注册页同模板）
                    style = AppTextStyles.PageHeader,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                PhoneNumberInput(
                    phone = phone,
                    isPhoneError = isPhoneError,
                    contentColor = Color.White,
                    // 字段标签行复用 common:ui 既有 string 资源（nonTransitiveRClass 全限定引用）
                    label = stringResource(com.smartwash.common.ui.R.string.phone_hint),
                ) {
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
                }

                Spacer(modifier = Modifier.height(8.dp))

                GlassDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // 验证码行（60s 客户端倒计时，样式与注册页同模板）
                VerificationCodeRow(
                    verificationCode = verificationCode,
                    isVerificationCodeError = isVerificationCodeError,
                    countDown = countDown,
                    captchaState = captchaState,
                    label = stringResource(R.string.verification_code_hint),
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
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                GlassDivider()

                Spacer(modifier = Modifier.height(8.dp))

                PasswordInput(
                    password = newPassword,
                    isPasswordError = isNewPasswordError,
                    showPassword = showPassword,
                    showVisibility = { showPassword = !showPassword },
                    contentColor = Color.White,
                    label = stringResource(R.string.reset_new_password),
                    modifier = Modifier.focusRequester(passwordFocusRequester)
                ) {
                    // 只限制长度，不实时显示错误（错误只在点击提交按钮时检测）
                    if (it.length <= 16) newPassword = it
                    // 对齐登录页实时清除机制：补足有效长度即清除行内错误
                    isNewPasswordError = if (it.length >= 6) false else isNewPasswordError
                }

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

/** 验证码输入行（与注册页 VerificationCodeRow 同模板：label + 48dp 输入行 + 描边发送按钮） */
@Composable
private fun VerificationCodeRow(
    verificationCode: String,
    isVerificationCodeError: Boolean,
    countDown: Int,
    captchaState: RequestState,
    label: String? = null,
    focusRequester: FocusRequester = FocusRequester(),
    onValueChange: (String) -> Unit,
    onSendCaptcha: () -> Unit,
    contentColor: Color = Color.White
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 字段标签行（白 12sp 小字位于输入行上方，间距 4dp）
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = contentColor,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        // 输入行：高 48dp（§3.7 行高 48–50dp），文字与图标垂直居中
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 前缀图标 16dp + opacity .5（§3.7）
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isVerificationCodeError) ErrorLight
                else contentColor.copy(alpha = 0.5f)
            )
            // 前缀与文字间距 spaceSm(12dp)（§3.7）
            Spacer(modifier = Modifier.width(AppDimens.spaceSm))
            BasicTextField(
                value = verificationCode,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                // §3.7：输入正文 16sp / Normal
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp, lineHeight = 24.sp, color = contentColor
                ),
                cursorBrush = SolidColor(if (isVerificationCodeError) ErrorLight else contentColor),
                decorationBox = { innerTextField ->
                    Box {
                        // 有 label 时隐藏占位符（占位=字段名，与 label 文案重复）
                        if (verificationCode.isEmpty() && label == null) {
                            Text(
                                text = stringResource(R.string.verification_code_hint),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // 尾缀发送按钮：1dp 玻璃描边 + 12dp 圆角 + 高 40dp + 横向 14dp（对齐注册页）；
            // 倒计时/加载禁用态描边与文字透明度降至 .55；倒计时本身已防重复发送
            val sendEnabled = countDown == 0 && captchaState !is RequestState.Loading
            // 描边与文字色随可发送态过渡（reduced motion 下经 motionSpec 降级为 180ms 纯颜色变化）
            val sendBorderColor = selectedColor(
                if (sendEnabled) GlassInput else GlassInput.copy(alpha = GlassInput.alpha * 0.55f)
            )
            val sendTextColor = selectedColor(
                contentColor.copy(alpha = if (sendEnabled) 0.92f else 0.92f * 0.55f)
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .then(
                        if (sendEnabled) Modifier.pressable(onClick = onSendCaptcha, debounce = false)
                        else Modifier
                    )
                    .border(
                        width = 1.dp,
                        color = sendBorderColor,
                        shape = RoundedCornerShape(AppDimens.radiusMd)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                // 「获取验证码 ↔ 59s」状态切换交叉淡入；倒计时数字跳动直切（不加动画）
                AnimatedContent(
                    targetState = countDown > 0,
                    transitionSpec = {
                        fadeIn(tween(CROSSFADE_ALT_MS)) togetherWith fadeOut(tween(CROSSFADE_ALT_MS))
                    },
                    label = "resetSendCaptchaLabel",
                ) { counting ->
                    Text(
                        text = if (counting) stringResource(R.string.countdown_format, countDown)
                        else stringResource(R.string.get_verification_code),
                        // 12/16/500 = captionStrong 档；倒计时秒数开 tnum（规范 §2.2）
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = sendTextColor
                    )
                }
            }
        }
        // 错误文案：淡入 + 纵向展开，避免卡片高度 0ms 突跳（对齐注册页）
        AnimatedVisibility(
            visible = isVerificationCodeError,
            enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
            exit = fadeOut(motionSpec(exitTween(EXIT_MICRO_MS))) +
                shrinkVertically(motionSpec(exitTween(EXIT_MICRO_MS))),
        ) {
            Text(
                text = stringResource(R.string.invalid_verification_code),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = ErrorLight,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
