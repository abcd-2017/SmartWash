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
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.AppInputDialog
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.model.RequestState
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
                        .padding(horizontal = AppDimens.pagePadding, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.my_profile),
                        style = MaterialTheme.typography.displayLarge,
                        color = AppColors.colorScheme.onBackground
                    )
                    IconButton(onClick = { navController.navigate(UserRoute.Setting.text) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = AppColors.colorScheme.textSecondary
                        )
                    }
                }

                // 身份横排（头像 48dp + 手机号 + 校区）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
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
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.colorScheme.primaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AppColors.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else if (userInfo?.avatar.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.colorScheme.primaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.avatar),
                                    modifier = Modifier.size(24.dp),
                                    tint = AppColors.colorScheme.primary
                                )
                            }
                        } else {
                            AsyncImage(
                                model = userInfo?.avatar,
                                contentDescription = stringResource(R.string.avatar),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // 相机角标
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(AppColors.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.change_avatar),
                                modifier = Modifier.size(10.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = userInfo?.phoneNumber ?: stringResource(R.string.username),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = userInfo?.schoolVo?.schoolName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                }

                // 账户分组
                GroupCard(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                ) {
                    SettingRow(
                        icon = Icons.Default.Wallet,
                        label = stringResource(R.string.account_balance),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.currency_format, String.format("%.2f", userInfo?.balance ?: 0f)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppColors.colorScheme.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.recharge),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = AppColors.colorScheme.textSecondary
                                )
                            }
                        },
                        onClick = { navController.navigate(PaymentRoute.Recharge.text) }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.divider, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.Default.LocalOffer,
                        label = stringResource(R.string.coupon),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.claim),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = AppColors.colorScheme.textSecondary
                                )
                            }
                        },
                        onClick = { navController.navigate(CouponRoute.Coupon.text) }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.divider, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.Default.CreditCard,
                        label = stringResource(R.string.campus_card),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (userInfo?.campusCard != null) stringResource(R.string.manage) else stringResource(R.string.bind),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = AppColors.colorScheme.textSecondary
                                )
                            }
                        },
                        onClick = {
                            if (userInfo?.campusCard != null) showUnbindDialog = true
                            else showBindDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 订单分组
                GroupCard(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                ) {
                    SettingRow(
                        icon = Icons.Default.Schedule,
                        label = stringResource(R.string.order_status_pending_payment),
                        trailing = {
                            val count = orderItemCount?.pendingPaymentCount ?: 0
                            if (count > 0) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.colorScheme.textSecondary
                            )
                        },
                        onClick = { navController.navigate("${OrderRoute.Order.text}/1") }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.divider, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.Default.LocalLaundryService,
                        label = stringResource(R.string.order_status_washing),
                        trailing = {
                            val count = orderItemCount?.processingCount ?: 0
                            if (count > 0) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.colorScheme.textSecondary
                            )
                        },
                        onClick = { navController.navigate("${OrderRoute.Order.text}/3") }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.divider, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.Default.Inventory,
                        label = stringResource(R.string.order_status_ready_for_pickup),
                        trailing = {
                            val count = orderItemCount?.pendingPickupCount ?: 0
                            if (count > 0) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.colorScheme.textSecondary
                            )
                        },
                        onClick = { navController.navigate("${OrderRoute.Order.text}/4") }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.divider, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.Default.History,
                        label = stringResource(R.string.all),
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.colorScheme.textSecondary
                            )
                        },
                        onClick = { navController.navigate("${OrderRoute.Order.text}/0") }
                    )
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 其他分组
                GroupCard(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                ) {
                    SettingRow(
                        icon = Icons.Default.Headset,
                        label = stringResource(R.string.contact_service),
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.colorScheme.textSecondary
                            )
                        },
                        onClick = { showServiceDialog = true }
                    )
                    HorizontalDivider(color = AppColors.colorScheme.divider, thickness = 0.5.dp)
                    SettingRow(
                        icon = Icons.AutoMirrored.Filled.Help,
                        label = stringResource(R.string.faq),
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.colorScheme.textSecondary
                            )
                        },
                        onClick = { Toast.makeText(context, context.getString(R.string.feature_in_development), Toast.LENGTH_SHORT).show() }
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
    label: String,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = AppColors.colorScheme.textSecondary
        )
    },
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, scaleFactor = 0.98f)
            .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
