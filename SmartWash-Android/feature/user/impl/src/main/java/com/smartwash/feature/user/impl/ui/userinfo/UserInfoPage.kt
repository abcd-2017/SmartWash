package com.smartwash.feature.user.impl.ui.userinfo

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.AppInputDialog
import com.smartwash.common.ui.components.ButtonSpinner
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.feature.coupon.api.CouponRoute
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.R

@SuppressLint("DefaultLocale")
@Composable
fun UserInfoPage(
    navController: NavHostController,
    isActive: Boolean,
    userInfoViewModel: UserInfoViewModel = hiltViewModel(),
) {
    val userInfoStatus by userInfoViewModel.userInfoStatus.collectAsState()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    val bindCampusState by userInfoViewModel.bindCampusState.collectAsState()
    val unBindCampusState by userInfoViewModel.unBindCampusState.collectAsState()
    val avatarUploadState by userInfoViewModel.avatarUploadState.collectAsState()

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { userInfoViewModel.uploadAvatar(it) }
    }

    var showBindDialog by remember { mutableStateOf(false) }
    var showUnbindDialog by remember { mutableStateOf(false) }
    var cardNumber by remember { mutableStateOf("") }
    var cardNumberError by remember { mutableStateOf(false) }

    // 每次 settled 落到本 tab 时刷新（原为监听 tab 栈 currentBackStackEntry，等价迁移）
    LaunchedEffect(isActive) {
        if (isActive) userInfoViewModel.getUserInfo()
    }
    // 首载一旦落定（成功或失败）就不再回骨架：失败后 resetState 回到 Idle，
    // 若只看 Idle/Loading 会让骨架永久停留（本页没有重试入口）
    var firstLoadSettled by remember { mutableStateOf(false) }
    // 状态驱动的副作用统一放 LaunchedEffect，禁止在组合期直接弹 Toast/回写状态
    LaunchedEffect(userInfoStatus) {
        when (userInfoStatus) {
            is RequestState.Error -> {
                firstLoadSettled = true
                Toast.makeText(
                    context,
                    (userInfoStatus as RequestState.Error).getMessage(context),
                    Toast.LENGTH_SHORT
                ).show()
                userInfoViewModel.resetState()
            }
            is RequestState.Success -> {
                firstLoadSettled = true
                userInfoViewModel.resetState()
            }
            else -> {}
        }
    }
    LaunchedEffect(bindCampusState) {
        when (bindCampusState) {
            is RequestState.Success -> {
                showBindDialog = false
                cardNumber = ""
                cardNumberError = false
                Toast.makeText(context, context.getString(R.string.bind_success), Toast.LENGTH_SHORT).show()
                userInfoViewModel.resetBindCampusState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (bindCampusState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                userInfoViewModel.resetBindCampusState()
            }
            else -> {}
        }
    }
    LaunchedEffect(unBindCampusState) {
        when (unBindCampusState) {
            is RequestState.Success -> {
                showUnbindDialog = false
                Toast.makeText(context, context.getString(R.string.unbind_success), Toast.LENGTH_SHORT).show()
                userInfoViewModel.resetUnBindCampusState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (unBindCampusState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                userInfoViewModel.resetUnBindCampusState()
            }
            else -> {}
        }
    }
    LaunchedEffect(avatarUploadState) {
        when (avatarUploadState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.avatar_upload_success), Toast.LENGTH_SHORT).show()
                userInfoViewModel.resetAvatarUploadState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (avatarUploadState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                userInfoViewModel.resetAvatarUploadState()
            }
            else -> {}
        }
    }

    // 仅首载（还没有数据）显示骨架：回 tab / 绑定 / 换头像后的刷新保留现有内容，避免整页闪变
    val showSkeleton = userInfo == null && !firstLoadSettled &&
        (userInfoStatus is RequestState.Loading || userInfoStatus is RequestState.Idle)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Crossfade(
            targetState = showSkeleton,
            animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
            label = "userInfoSkeleton",
        ) { skeleton ->
            if (skeleton) {
                LoadingState(modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 标题行（设计：状态栏下 16，行底距头像行 8）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AppDimens.pagePadding,
                                end = AppDimens.pagePadding,
                                top = 16.dp,
                                bottom = 8.dp
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.my_profile),
                            style = AppTextStyles.RootTitle,
                            color = AppColors.colorScheme.textPrimary
                        )
                        // 设计：48dp 圆形底（radius 999）+ 20dp 图标，兼作 48dp 热区（防抖已注入，关闭叠加）
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(AppDimens.radiusFull))
                                .background(AppColors.colorScheme.surfaceVariant)
                                .pressable(
                                    onClick = rememberDebouncedClick { navController.navigate(UserRoute.Setting.text) },
                                    debounce = false,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.colorScheme.textSecondary
                            )
                        }
                    }

                    // 头像区域（设计：标题行下 8，行底距数据卡 16）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AppDimens.pagePadding,
                                end = AppDimens.pagePadding,
                                top = 8.dp,
                                bottom = 16.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .pressable(onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }),
                            contentAlignment = Alignment.Center
                        ) {
                            // 三态（上传中 / 占位首字 / 网络图）交叉淡入，替代 0ms 硬切；
                            // 180ms 淡入本身即 reduced motion 的降级形态（规范 §8）
                            val avatarState = when {
                                avatarUploadState is RequestState.Loading -> AvatarVisual.Uploading
                                userInfo?.avatar.isNullOrBlank() -> AvatarVisual.Placeholder
                                else -> AvatarVisual.Remote
                            }
                            Crossfade(
                                targetState = avatarState,
                                animationSpec = tween(CROSSFADE_ALT_MS),
                                label = "avatarVisual",
                            ) { visual ->
                                when (visual) {
                                    AvatarVisual.Uploading -> Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(AppColors.colorScheme.primaryLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // 公共 ButtonSpinner（§7.8：reduced motion 退化为静态 270° 圆环）
                                        ButtonSpinner(
                                            size = 24.dp,
                                            strokeWidth = 2.dp,
                                            color = AppColors.colorScheme.primary,
                                        )
                                    }

                                    AvatarVisual.Placeholder -> Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(
                                                // 设计 gradient.hero：对角线渐变（浅 #2D9B6A→#1E8C5C / 深 #2D9B6A→#5BC894）
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        AppColors.colorScheme.primary,
                                                        AppColors.colorScheme.primaryDark
                                                    ),
                                                    start = Offset.Zero,
                                                    end = Offset.Infinite
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = userInfo?.phoneNumber?.firstOrNull()?.toString() ?: "U",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }

                                    AvatarVisual.Remote -> AsyncImage(
                                        // 图片加载完成后的淡入；reduced motion 下取 0（直接显示）
                                        model = ImageRequest.Builder(context)
                                            .data(userInfo?.avatar)
                                            .crossfade(if (LocalReduceMotion.current) 0 else CROSSFADE_ALT_MS)
                                            .build(),
                                        contentDescription = stringResource(R.string.avatar),
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userInfo?.phoneNumber
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { maskPhone(it) }
                                    ?: stringResource(R.string.username),
                                style = MaterialTheme.typography.titleMedium,
                                color = AppColors.colorScheme.textPrimary
                            )
                            Text(
                                text = userInfo?.schoolVo?.schoolName ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = AppColors.colorScheme.textTertiary
                        )
                    }

                    // 数据卡片：三列（余额 + 优惠券 + 累计订单）
                    GroupCard(
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 余额（点击去充值；防抖已注入，关闭叠加）
                            Column(
                                modifier = Modifier
                                    .weight(1.35f)
                                    .pressable(
                                        onClick = rememberDebouncedClick { navController.navigate(PaymentRoute.Recharge.text) },
                                        debounce = false,
                                    ),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = stringResource(R.string.wallet_balance),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                                Text(
                                    text = stringResource(R.string.currency_format, String.format("%.2f", userInfo?.balance ?: 0f)),
                                    style = AppTextStyles.DataLarge,
                                    color = AppColors.colorScheme.primaryDark,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            // 分隔线（设计：1px hairline，高 40）
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(AppColors.colorScheme.hairline)
                            )
                            // 优惠券（点击进优惠券页；防抖已注入，关闭叠加）
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .pressable(
                                        onClick = rememberDebouncedClick { navController.navigate(CouponRoute.Coupon.text) },
                                        debounce = false,
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${userInfo?.couponCount ?: 0}",
                                    style = AppTextStyles.AmountMedium,
                                    color = AppColors.colorScheme.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.coupon),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            // 分隔线（设计：1px hairline，高 40）
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(AppColors.colorScheme.hairline)
                            )
                            // 累计订单
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${userInfo?.orderCount ?: 0}",
                                    style = AppTextStyles.AmountMedium,
                                    color = AppColors.colorScheme.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.total_orders),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                    // 账户卡片
                    GroupCard(
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    ) {
                        SettingRow(
                            icon = Icons.Default.CreditCard,
                            containerColor = AppColors.colorScheme.iconContainerGreen,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundGreen,
                            label = stringResource(R.string.campus_card),
                            trailing = {
                                Text(
                                    text = if (userInfo?.campusCard != null) stringResource(R.string.bound) else stringResource(R.string.not_bound),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (userInfo?.campusCard != null) AppColors.colorScheme.primary else AppColors.colorScheme.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AppColors.colorScheme.textTertiary
                                )
                            },
                            onClick = {
                                if (userInfo?.campusCard != null) showUnbindDialog = true
                                else showBindDialog = true
                            }
                        )
                        HorizontalDivider(color = AppColors.colorScheme.hairline, thickness = 0.5.dp)
                        SettingRow(
                            icon = Icons.Default.LocationOn,
                            containerColor = AppColors.colorScheme.iconContainerOrange,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundOrange,
                            label = stringResource(R.string.shipping_address),
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AppColors.colorScheme.textTertiary
                                )
                            },
                            onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 功能卡片
                    GroupCard(
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    ) {
                        SettingRow(
                            icon = Icons.Default.Inventory,
                            containerColor = AppColors.colorScheme.iconContainerBlue,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundBlue,
                            label = stringResource(R.string.my_orders),
                            trailing = {
                                Text(
                                    text = stringResource(R.string.my_orders_count_format, userInfo?.orderCount ?: 0),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.colorScheme.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AppColors.colorScheme.textTertiary
                                )
                            },
                            onClick = { navController.navigate("${OrderRoute.Order.text}/0") }
                        )
                        HorizontalDivider(color = AppColors.colorScheme.hairline, thickness = 0.5.dp)
                        SettingRow(
                            icon = Icons.Default.Schedule,
                            containerColor = AppColors.colorScheme.iconContainerPurple,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundPurple,
                            label = stringResource(R.string.divination_calendar),
                            onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                        )
                        HorizontalDivider(color = AppColors.colorScheme.hairline, thickness = 0.5.dp)
                        SettingRow(
                            icon = Icons.Default.Settings,
                            containerColor = AppColors.colorScheme.iconContainerGreen,
                            iconForegroundColor = AppColors.colorScheme.iconForegroundGreen,
                            label = stringResource(R.string.settings),
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AppColors.colorScheme.textTertiary
                                )
                            },
                            onClick = { navController.navigate(UserRoute.Setting.text) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // 绑定校园卡弹窗
    if (showBindDialog) {
        AppInputDialog(
            title = stringResource(R.string.bind_campus_card),
            inputLabel = stringResource(R.string.input_campus_card),
            inputValue = cardNumber,
            onValueChange = { cardNumberError = false; cardNumber = it },
            isError = cardNumberError,
            errorMessage = stringResource(R.string.campus_card_empty),
            keyboardType = KeyboardType.Number,
            onConfirm = {
                if (cardNumber.isEmpty()) cardNumberError = true
                else userInfoViewModel.bindCampus(cardNumber)
            },
            onDismiss = { showBindDialog = false }
        )
    }

    // 解绑校园卡弹窗
    if (showUnbindDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.unbind_campus_card),
            message = stringResource(R.string.confirm_unbind_question),
            confirmText = stringResource(R.string.confirm_unbind),
            onConfirm = { userInfoViewModel.unBindCampus() },
            onDismiss = { showUnbindDialog = false }
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    containerColor: Color,
    iconForegroundColor: Color,
    label: String,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.colorScheme.textTertiary
        )
    },
    onClick: () -> Unit,
) {
    // 水平内边距由 GroupCard cardPadding 承载；设计行距 64 = 16 + 32 图标盒 + 16
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ib32 图标容器（32dp，12px圆角，彩色浅底 + 语义色图标 20）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconForegroundColor
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

private fun maskPhone(phone: String): String =
    if (phone.length == 11) phone.take(3) + "****" + phone.takeLast(4) else phone

/** 头像三态 — 作为 [Crossfade] 的 targetState，避免上传中/占位/网络图之间硬切 */
private enum class AvatarVisual { Uploading, Placeholder, Remote }
