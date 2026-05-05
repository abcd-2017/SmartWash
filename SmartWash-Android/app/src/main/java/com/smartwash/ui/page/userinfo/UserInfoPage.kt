package com.smartwash.ui.page.userinfo

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.smartwash.R
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.IconBox
import com.smartwash.utils.RequestState

@SuppressLint("DefaultLocale")
@Composable
fun UserInfoPage(
    navController: NavHostController,
    homePageNavController: NavHostController,
    userInfoViewModel: UserInfoViewModel = hiltViewModel(),
) {
    val userInfoStatus by userInfoViewModel.userInfoStatus.collectAsState()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    val orderItemCountVo by userInfoViewModel.orderItemCount.collectAsState()
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
        else -> { userInfoViewModel.resetState() }
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
                IconButton(onClick = { navController.navigate(PageConstant.Setting.text) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = AppColors.colorScheme.textSecondary
                    )
                }
            }

            // 用户信息区（居中大头像）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
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
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(AppColors.colorScheme.primaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = AppColors.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else if (userInfo?.avatar.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(AppColors.colorScheme.primaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.avatar),
                                modifier = Modifier.size(32.dp),
                                tint = AppColors.colorScheme.primary
                            )
                        }
                    } else {
                        AsyncImage(
                            model = userInfo?.avatar,
                            contentDescription = stringResource(R.string.avatar),
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // 相机角标
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(AppColors.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.change_avatar),
                            modifier = Modifier.size(13.dp),
                            tint = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = userInfo?.phoneNumber ?: stringResource(R.string.username),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.student_id_format, userInfo?.studentId ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }

            // 余额信息卡（渐变绿底）
            BalanceCard(
                balance = userInfo?.balance ?: 0f,
                campusCard = userInfo?.campusCard,
                onRechargeClick = { navController.navigate(PageConstant.Recharge.text) },
                onCampusCardClick = {
                    if (userInfo?.campusCard != null) showUnbindDialog = true
                    else showBindDialog = true
                }
            )

            // 订单快捷入口
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.orders),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OrderQuickGrid(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                pendingPaymentCount = orderItemCountVo?.pendingPaymentCount ?: 0,
                processingCount = orderItemCountVo?.processingCount ?: 0,
                pendingPickupCount = orderItemCountVo?.pendingPickupCount ?: 0,
                onPendingPaymentClick = { navController.navigate("${PageConstant.Order.text}/1") },
                onProcessingClick = { navController.navigate("${PageConstant.Order.text}/3") },
                onPendingPickupClick = { navController.navigate("${PageConstant.Order.text}/4") },
                onAllClick = { navController.navigate("${PageConstant.Order.text}/0") }
            )

            // 功能列表
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Column(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
            ) {
                FunctionItem(
                    icon = Icons.Default.LocalOffer,
                    title = stringResource(R.string.coupon),
                    subtitle = stringResource(R.string.claimable_coupons),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.claim), style = MaterialTheme.typography.bodySmall, color = AppColors.colorScheme.primary)
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.textSecondary)
                        }
                    },
                    onClick = { navController.navigate(PageConstant.Coupon.text) }
                )
                FunctionItem(
                    icon = Icons.Default.Headset,
                    title = stringResource(R.string.contact_service),
                    subtitle = stringResource(R.string.online_consulting),
                    onClick = {}
                )
                FunctionItem(
                    icon = Icons.AutoMirrored.Filled.Help,
                    title = stringResource(R.string.faq),
                    subtitle = stringResource(R.string.user_guide),
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 绑定校园卡弹窗
    if (showBindDialog) {
        AlertDialog(
            onDismissRequest = { showBindDialog = false },
            title = { Text(stringResource(R.string.bind_campus_card), style = MaterialTheme.typography.headlineSmall) },
            text = {
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumberError = false; cardNumber = it },
                    label = { Text(stringResource(R.string.input_campus_card)) },
                    supportingText = { if (cardNumberError) Text(stringResource(R.string.campus_card_empty)) },
                    singleLine = true,
                    isError = cardNumberError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (cardNumber.isEmpty()) cardNumberError = true
                    else userInfoViewModel.bindCampus(cardNumber)
                }) { Text(stringResource(R.string.confirm_bind)) }
            },
            dismissButton = {
                TextButton(onClick = { showBindDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // 解绑校园卡弹窗
    if (showUnbindDialog) {
        AlertDialog(
            onDismissRequest = { showUnbindDialog = false },
            title = { Text(stringResource(R.string.unbind_campus_card), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(stringResource(R.string.confirm_unbind_question)) },
            confirmButton = {
                TextButton(onClick = { userInfoViewModel.unBindCampus() }) { Text(stringResource(R.string.confirm_unbind)) }
            },
            dismissButton = {
                TextButton(onClick = { showUnbindDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun BalanceCard(
    balance: Float,
    campusCard: String?,
    onRechargeClick: () -> Unit,
    onCampusCardClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark)),
                    shape = RoundedCornerShape(AppDimens.cardRadius)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.account_balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.currency_format, String.format("%.2f", balance)),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.campus_card) + "：" +
                            if (campusCard != null) stringResource(R.string.bind) else stringResource(R.string.not_bound),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.clickable(onClick = onCampusCardClick)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.clickable(onClick = onRechargeClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.go_recharge),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderQuickGrid(
    modifier: Modifier = Modifier,
    pendingPaymentCount: Int,
    processingCount: Int,
    pendingPickupCount: Int,
    onPendingPaymentClick: () -> Unit,
    onProcessingClick: () -> Unit,
    onPendingPickupClick: () -> Unit,
    onAllClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OrderQuickEntry(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Schedule,
                label = stringResource(R.string.order_status_pending_payment),
                count = pendingPaymentCount,
                onClick = onPendingPaymentClick
            )
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(72.dp)
                    .align(Alignment.CenterVertically)
                    .background(AppColors.colorScheme.divider)
            )
            OrderQuickEntry(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocalLaundryService,
                label = stringResource(R.string.order_status_washing),
                count = processingCount,
                onClick = onProcessingClick
            )
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(72.dp)
                    .align(Alignment.CenterVertically)
                    .background(AppColors.colorScheme.divider)
            )
            OrderQuickEntry(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Inventory,
                label = stringResource(R.string.order_status_ready_for_pickup),
                count = pendingPickupCount,
                onClick = onPendingPickupClick
            )
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(72.dp)
                    .align(Alignment.CenterVertically)
                    .background(AppColors.colorScheme.divider)
            )
            OrderQuickEntry(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.History,
                label = stringResource(R.string.all),
                count = null,
                onClick = onAllClick
            )
        }
    }
}

@Composable
private fun OrderQuickEntry(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    count: Int?,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                .background(AppColors.colorScheme.primaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.colorScheme.primary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.colorScheme.textPrimary
        )
        if (count != null && count > 0) {
            Text(
                text = stringResource(R.string.order_count_format, count),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FunctionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.textSecondary)
    },
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.cardPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                    .background(AppColors.colorScheme.primaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AppColors.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
            trailing()
        }
    }
}
