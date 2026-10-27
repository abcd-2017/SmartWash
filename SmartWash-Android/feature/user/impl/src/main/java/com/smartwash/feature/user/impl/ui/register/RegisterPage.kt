package com.smartwash.feature.user.impl.ui.register

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientMid
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.ErrorLight
import com.smartwash.common.ui.theme.GlassBgCard
import com.smartwash.common.ui.theme.GlassBgSubtle
import com.smartwash.common.ui.theme.GlassBorderSubtle
import com.smartwash.common.ui.theme.GlassInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.smartwash.feature.user.api.UserRoute
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

private val GradientTop = AuthGradientTop
private val GradientBottom = AuthGradientBottom

// 进度条非激活态（注册画板「进度2/3」= white 28%）
private val ProgressInactive = Color.White.copy(alpha = 0.28f)
// 退出时长 ≈ 进入（DURATION_MICRO_MS）的 70%（动效语言 v0.4）
private const val EXIT_MICRO_MS = 140

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
    // 倒计时任务句柄：发送失败时取消，使按钮立刻恢复可点（对齐鸿蒙 Register.ets:211-215）
    var countDownJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 连点防抖：本页返回箭头/去登录均为 popBackStack 出栈，转场残影期被重复触发会连 Login 一起弹掉，
    // 弹空导航栈导致黑屏（已确认 bug 本体），故用比转场更长的 800ms 窗口
    val debouncedBackClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
        navController.popBackStack()
    }
    val debouncedLoginClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
        navController.popBackStack()
    }

    val captchaState by registerViewModel.captchaState.collectAsState()
    val registerState by registerViewModel.registerState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = currentView()

    // D-R5 品牌渐变页深浅色同款：状态栏图标强制白色；离页恢复主题默认（与 LoginPage 对齐）
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
                Toast.makeText(context, context.getString(R.string.captcha_sent), Toast.LENGTH_SHORT).show()
                registerViewModel.setCaptchaIdle()
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
                registerViewModel.setCaptchaIdle()
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
                // 注册成功后跳转填写学校学号信息
                navController.navigate(UserRoute.UpdateUserInfo.text) {
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

    val glassShape = RoundedCornerShape(AppDimens.radiusLg)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(0f to GradientTop, 0.5f to AuthGradientMid, 1f to GradientBottom)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部导航栏 — 添加 statusBarsPadding 防止与状态栏重叠
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // D-R9 返回箭头包进 48dp 热区，右侧占位同宽保持标题视觉居中（防抖已注入，关闭叠加）。
                // 注册画板热区贴左缘（画板内 x4-52、图标 16-40），offset 左移 20 对齐且不影响标题居中占位
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
                    text = stringResource(R.string.register),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            // D-R2 布局顶部流式：brand 距 nav 行 16px
            Spacer(modifier = Modifier.height(16.dp))

            // 品牌标识 — 92dp 单层圆形
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(GlassBgSubtle)
                    .border(1.dp, GlassBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // D-R1 品牌图标：设计稿屏 2「水滴+加号」，替代 emoji
                Icon(
                    painter = painterResource(com.smartwash.common.ui.R.drawable.ic_water_add),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = Color.White
                )
            }

            // D-R2 brand margin-bottom 12px
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.create_account),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.start_laundry_journey),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )

            // 进度条
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 激活态 — 纯白
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(Color.White)
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(ProgressInactive)
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusFull))
                        .background(ProgressInactive)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 毛玻璃输入卡片 — 内边距上 16 / 下 24 / 水平 16（对齐登录画板 376→584：392 顶距 16、560→584 底距 24；同模板 A）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(glassShape)
                    .background(GlassBgCard)
                    .border(1.dp, GlassBorderSubtle, glassShape)
                    .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                PhoneNumberInput(
                    phone = phone,
                    isPhoneError = isPhoneError,
                    contentColor = Color.White,
                    label = stringResource(com.smartwash.common.ui.R.string.phone_number)
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

                Spacer(modifier = Modifier.height(8.dp))

                GlassDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // 验证码行
                VerificationCodeRow(
                    label = stringResource(R.string.verification_code),
                    verificationCode = verificationCode,
                    isVerificationCodeError = isVerificationCodeError,
                    countDown = countDown,
                    captchaState = captchaState,
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
                            registerViewModel.getCaptcha(phone)
                            // 同步置倒计时（立即禁用按钮防连点），倒计时任务句柄留存以便失败时取消
                            countDownJob?.cancel()
                            countDown = UserImplConstant.SEND_CAPTCHA
                            countDownJob = coroutineScope.launch {
                                while (countDown > 0) {
                                    delay(1000)
                                    countDown--
                                }
                                // 倒计时结束归零，允许重新发送
                                countDown = 0
                                registerViewModel.setCaptchaIdle()
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
                    password = password,
                    isPasswordError = isPasswordError,
                    showPassword = showPassword,
                    showVisibility = { showPassword = !showPassword },
                    contentColor = Color.White,
                    label = stringResource(com.smartwash.common.ui.R.string.password),
                    modifier = Modifier.focusRequester(passwordFocusRequester)
                ) {
                    // 只限制长度，不实时显示错误（错误只在点击提交按钮时检测）
                    if (it.length <= 16) password = it
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 注册按钮 — 玻璃卡白色 CTA（自绘无 state layer，防抖经 800ms 动作档注入）
                // 顶距 20：注册画板密码行底 532 → CTA 552
                AuthCtaButton(
                    text = stringResource(R.string.register),
                    loading = registerState is RequestState.Loading,
                    onClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) {
                        isPhoneError = !isValidPhone(phone)
                        isVerificationCodeError = verificationCode.length != 6
                        isPasswordError = password.length < 6 || password.length > 16

                        if (!isPhoneError && !isVerificationCodeError && !isPasswordError) {
                            view.performHaptic(HapticEffect.MEDIUM)
                            keyboardController?.hide()
                            registerViewModel.userRegister(phone, password, verificationCode)
                        } else {
                            view.performHaptic(HapticEffect.ERROR)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 登录入口 — 只有点击"去登录"才跳转
            // 顶距 8：注册画板卡底 624 → 文案 632（深浅两板一致，纯白 12/400·12/500）
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.has_account),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.login_now),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        // 热区先行：padding 必须在 pressable 之前，否则可点区域只有文字本体
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .pressable(onClick = debouncedLoginClick, debounce = false)
                )
            }
        }
    }
}

@Composable
private fun GlassDivider() {
    // 注册画板「分隔线1/2」：卡内左右缩进 24、1dp 高、白 12%
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(1.dp)
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
    // 可见字段标签（对齐登录画板「t:手机号/密码标签」12/500 模式，§7 表单必须有可见标签）
    label: String? = null,
    onValueChange: (String) -> Unit,
    onSendCaptcha: () -> Unit,
    contentColor: Color = Color.White
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.92f)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        // 紧凑输入行：内容高 20dp（设计稿输入行=图标 20px，登录/注册画板 412→432 同款，D-R4）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isVerificationCodeError) ErrorLight
                else contentColor.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceXs))
            BasicTextField(
                value = verificationCode,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp, lineHeight = 20.sp, color = contentColor
                ),
                cursorBrush = SolidColor(if (isVerificationCodeError) ErrorLight else contentColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (verificationCode.isEmpty()) {
                            Text(
                                text = stringResource(R.string.verification_code_hint),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = contentColor.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // 尾缀发送按钮：1dp 玻璃描边（白 30%）+ 999 胶囊 + 48dp 高 + 横向 14dp（注册画板「发送按钮」88×48 r999）。
            // 高 48 大于行高 20，垂直方向溢出行边界不参与布局占位（同 PasswordInput 可见性钮模式）；
            // 画板按钮相对输入行中心上移 6px（顶 437 对齐标签顶、底 485 对齐分隔线2），offset 精确复刻。
            // 倒计时/加载禁用态描边与文字透明度降至 .55；倒计时本身已防重复发送，不再叠加防抖
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
                    .height(48.dp)
                    .offset(y = (-6).dp)
                    .then(
                        if (sendEnabled) Modifier.pressable(onClick = onSendCaptcha, debounce = false)
                        else Modifier
                    )
                    .border(
                        width = 1.dp,
                        color = sendBorderColor,
                        shape = RoundedCornerShape(AppDimens.radiusFull)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                // 「获取验证码 ↔ 59s」状态切换交叉淡入；倒计时每秒的数字跳动仍为直切（不加动画）
                AnimatedContent(
                    targetState = countDown > 0,
                    transitionSpec = {
                        fadeIn(tween(CROSSFADE_ALT_MS)) togetherWith fadeOut(tween(CROSSFADE_ALT_MS))
                    },
                    label = "sendCaptchaLabel",
                ) { counting ->
                    Text(
                        text = if (counting) stringResource(R.string.countdown_format, countDown)
                        else stringResource(R.string.get_verification_code),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = sendTextColor
                    )
                }
            }
        }
        // 错误文案：淡入 + 纵向展开，避免卡片高度 0ms 突跳
        AnimatedVisibility(
            visible = isVerificationCodeError,
            enter = fadeIn(motionSpec(enterTween(DURATION_MICRO_MS))) +
                expandVertically(motionSpec(enterTween(DURATION_MICRO_MS))),
            exit = fadeOut(motionSpec(exitTween(EXIT_MICRO_MS))) +
                shrinkVertically(motionSpec(exitTween(EXIT_MICRO_MS))),
        ) {
            // 错误文案沿用既有 supportingText 内容（原 TextField supportingText 平替）
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
