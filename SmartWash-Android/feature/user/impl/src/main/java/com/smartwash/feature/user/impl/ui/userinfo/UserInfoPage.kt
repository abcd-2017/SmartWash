package com.smartwash.feature.user.impl.ui.userinfo

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.AppInputDialog
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import com.smartwash.feature.coupon.api.CouponRoute
import com.smartwash.feature.order.api.OrderRoute
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.R

@SuppressLint("DefaultLocale")
@Composable
fun UserInfoPage(
    navController: NavHostController,
    homePageNavController: NavHostController,
    userInfoViewModel: UserInfoViewModel = hiltViewModel(),
) {
    val userInfoStatus by userInfoViewModel.userInfoStatus.collectAsState()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    val orderItemCount by userInfoViewModel.orderItemCount.collectAsState()
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
    var showServiceDialog by remember { mutableStateOf(false) }
    var cardNumber by remember { mutableStateOf("") }
    var cardNumberError by remember { mutableStateOf(false) }

    LaunchedEffect(homePageNavController.currentBackStackEntry) {
        userInfoViewModel.getUserInfo()
    }
    when (userInfoStatus) {
        is RequestState.Error -> {
            Toast.makeText(
                context,
                (userInfoStatus as RequestState.Error).getMessage(context),
                Toast.LENGTH_SHORT
            ).show()
            userInfoViewModel.resetState()
        }
        is RequestState.Success -> { userInfoViewModel.resetState() }
        else -> {}
    }
    when (bindCampusState) {
        is RequestState.Success -> {
            showBindDialog = false
            cardNumber = ""
            cardNumberError = false
            LaunchedEffect(bindCampusState) {
                Toast.makeText(context, context.getString(R.string.bind_success), Toast.LENGTH_SHORT).show()
            }
            userInfoViewModel.resetBindCampusState()
        }
        is RequestState.Error -> {
            Toast.makeText(context, (bindCampusState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
            userInfoViewModel.resetBindCampusState()
        }
        else -> {}
    }
    when (unBindCampusState) {
        is RequestState.Success -> {
            showUnbindDialog = false
            LaunchedEffect(unBindCampusState) {
                Toast.makeText(context, context.getString(R.string.unbind_success), Toast.LENGTH_SHORT).show()
            }
            userInfoViewModel.resetUnBindCampusState()
        }
        is RequestState.Error -> {
            Toast.makeText(context, (unBindCampusState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
            userInfoViewModel.resetUnBindCampusState()
        }
        else -> {}
    }
    when (avatarUploadState) {
        is RequestState.Success -> {
            LaunchedEffect(avatarUploadState) {
                Toast.makeText(context, context.getString(R.string.avatar_upload_success), Toast.LENGTH_SHORT).show()
            }
            userInfoViewModel.resetAvatarUploadState()
        }
        is RequestState.Error -> {
            Toast.makeText(context, context.getString(R.string.avatar_upload_failed), Toast.LENGTH_SHORT).show()
            userInfoViewModel.resetAvatarUploadState()
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (userInfoStatus is RequestState.Loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.my_profile),
                        style = AppTextStyles.RootTitle,
                        color = AppColors.colorScheme.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.colorScheme.iconContainerTeal)
                            .clickable { navController.navigate(UserRoute.Setting.text) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier.size(18.dp),
                            tint = AppColors.colorScheme.primary
                        )
                    }
                }

                // 头像区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clickable {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUploadState is RequestState.Loading) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.colorScheme.primaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = AppColors.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else if (userInfo?.avatar.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                AppColors.colorScheme.primary,
                                                AppColors.colorScheme.primaryDark
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userInfo?.phoneNumber?.firstOrNull()?.toString() ?: "U",
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            AsyncImage(
                                model = userInfo?.avatar,
                                contentDescription = stringResource(R.string.avatar),
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // 相机角标
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(AppColors.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.change_avatar),
                                modifier = Modifier.size(11.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userInfo?.phoneNumber ?: stringResource(R.string.username),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.colorScheme.textPrimary
                        )
                        Text(
                            text = userInfo?.schoolVo?.schoolName ?: "",
                            fontSize = 11.sp,
                            color = AppColors.colorScheme.textTertiary,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
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
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 余额
                        Column(
                            modifier = Modifier.weight(1.35f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.wallet_balance),
                                fontSize = 11.sp,
                                color = AppColors.colorScheme.textTertiary
                            )
                            Text(
                                text = stringResource(R.string.currency_format, String.format("%.2f", userInfo?.balance ?: 0f)),
                                style = AppTextStyles.DataLarge,
                                color = AppColors.colorScheme.primaryDark,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(42.dp)
                                .background(AppColors.colorScheme.hairline)
                        )
                        // 优惠券
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${userInfo?.couponCount ?: 0}",
                                style = AppTextStyles.AmountMedium,
                                color = AppColors.colorScheme.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.coupon),
                                fontSize = 11.sp,
                                color = AppColors.colorScheme.textTertiary,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(42.dp)
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
                                fontSize = 11.sp,
                                color = AppColors.colorScheme.textTertiary,
                                modifier = Modifier.padding(top = 5.dp)
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
                        iconTint = AppColors.colorScheme.iconContainerBlue,
                        label = stringResource(R.string.campus_card),
                        trailing = {
                            Text(
                                text = if (userInfo?.campusCard != null) stringResource(R.string.bound) else stringResource(R.string.not_bound),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (userInfo?.campusCard != null) AppColors.colorScheme.primary else AppColors.colorScheme.textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
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
                        icon = Icons.Default.Person,
                        iconTint = AppColors.colorScheme.iconContainerOrange,
                        label = stringResource(R.string.school_dorm_info),
                        trailing = {
                            Text(
                                text = userInfo?.schoolVo?.schoolName ?: "",
                                fontSize = 12.sp,
                                color = AppColors.colorScheme.textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = AppColors.colorScheme.textTertiary
                            )
                        },
                        onClick = { navController.navigate(UserRoute.UpdateUserInfo.text) }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.hairline, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.Default.Inventory,
                        iconTint = AppColors.colorScheme.iconContainerPurple,
                        label = stringResource(R.string.shipping_address),
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = AppColors.colorScheme.textTertiary
                            )
                        },
                        onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 功能卡片
                GroupCard(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                ) {
                    SettingRow(
                        icon = Icons.Default.Schedule,
                        iconTint = AppColors.colorScheme.iconContainerTeal,
                        label = stringResource(R.string.divination_calendar),
                        trailing = {
                            Text(
                                text = "12 条",
                                fontSize = 12.sp,
                                color = AppColors.colorScheme.textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = AppColors.colorScheme.textTertiary
                            )
                        },
                        onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.hairline, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.Default.Settings,
                        iconTint = AppColors.colorScheme.iconContainerGreen,
                        label = stringResource(R.string.settings),
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
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

    // 联系客服弹窗
    if (showServiceDialog) {
        AppInfoDialog(
            title = stringResource(R.string.contact_service),
            message = stringResource(R.string.service_phone) + "\n" + stringResource(R.string.service_hours),
            onDismiss = { showServiceDialog = false }
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = AppColors.colorScheme.textTertiary
        )
    },
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, scaleFactor = 0.98f)
            .padding(vertical = 15.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ib32 图标容器（32dp，10px圆角，彩色浅底）
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = AppColors.colorScheme.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
