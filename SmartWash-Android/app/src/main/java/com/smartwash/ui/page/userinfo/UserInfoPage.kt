package com.smartwash.ui.page.userinfo

import android.annotation.SuppressLint
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AccountBalance
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.network.vo.user.UserInfoVo
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.SettingRow
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.Background
import com.smartwash.ui.theme.Divider
import com.smartwash.ui.theme.IconBox
import com.smartwash.ui.theme.Primary
import com.smartwash.ui.theme.PrimaryLight
import com.smartwash.ui.theme.TextSecondary
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

    var showBindDialog by remember { mutableStateOf(false) }
    var showUnbindDialog by remember { mutableStateOf(false) }
    var cardNumber by remember { mutableStateOf("") }
    var cardNumberError by remember { mutableStateOf(false) }
    var context = LocalContext.current

    LaunchedEffect(homePageNavController.currentBackStackEntry) {
        userInfoViewModel.getUserInfo()
    }
    when (userInfoStatus) {
        is RequestState.Error -> {
            Toast.makeText(
                context,
                context.getString((userInfoStatus as RequestState.Error).messageResId),
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
            Toast.makeText(context, context.getString((bindCampusState as RequestState.Error).messageResId), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString((unBindCampusState as RequestState.Error).messageResId), Toast.LENGTH_SHORT).show()
            userInfoViewModel.resetUnBindCampusState()
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
            // 顶部标题 + 设置入口
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
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { navController.navigate(PageConstant.Setting.text) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = AppColors.colorScheme.textSecondary
                    )
                }
            }

            // 用户信息卡
            AppCard(
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AppColors.colorScheme.primaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.avatar),
                            modifier = Modifier.size(28.dp),
                            tint = AppColors.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
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
                }
            }

            // 账户信息区
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.account),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.AccountBalance,
                    title = stringResource(R.string.account_balance),
                    subtitle = stringResource(R.string.currency_format, String.format("%s", userInfo?.balance ?: 0)),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.recharge), style = MaterialTheme.typography.bodySmall, color = AppColors.colorScheme.primary)
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.textSecondary)
                        }
                    },
                    onClick = { navController.navigate(PageConstant.Recharge.text) }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider, modifier = Modifier.padding(horizontal = AppDimens.cardPadding))
                SettingRow(
                    icon = Icons.Default.CreditCard,
                    title = stringResource(R.string.campus_card),
                    subtitle = if (userInfo?.campusCard != null) userInfo?.campusCard ?: "" else stringResource(R.string.not_bound),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (userInfo?.campusCard != null) stringResource(R.string.unbind) else stringResource(R.string.bind),
                                style = MaterialTheme.typography.bodySmall,
                                color = Primary
                            )
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.textSecondary)
                        }
                    },
                    onClick = {
                        if (userInfo?.campusCard != null) showUnbindDialog = true
                        else showBindDialog = true
                    }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider, modifier = Modifier.padding(horizontal = AppDimens.cardPadding))
                SettingRow(
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
            }

            // 订单管理区
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.orders),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OrderQuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    title = stringResource(R.string.order_status_pending_payment),
                    count = orderItemCountVo?.pendingPaymentCount ?: 0,
                    onClick = { navController.navigate("${PageConstant.Order.text}/1") }
                )
                OrderQuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalLaundryService,
                    title = stringResource(R.string.order_status_washing),
                    count = orderItemCountVo?.processingCount ?: 0,
                    onClick = { navController.navigate("${PageConstant.Order.text}/3") }
                )
                OrderQuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Inventory,
                    title = stringResource(R.string.order_status_ready_for_pickup),
                    count = orderItemCountVo?.pendingPickupCount ?: 0,
                    onClick = { navController.navigate("${PageConstant.Order.text}/4") }
                )
                OrderQuickCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.History,
                    title = stringResource(R.string.all),
                    count = null,
                    onClick = { navController.navigate("${PageConstant.Order.text}/0") }
                )
            }

            // 其他功能
            Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            Text(
                text = stringResource(R.string.other),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                SettingRow(
                    icon = Icons.Default.Headset,
                    title = stringResource(R.string.contact_service),
                    subtitle = stringResource(R.string.online_consulting),
                    trailing = {
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.textSecondary)
                    }
                )
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider, modifier = Modifier.padding(horizontal = AppDimens.cardPadding))
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.Help,
                    title = stringResource(R.string.faq),
                    subtitle = stringResource(R.string.user_guide),
                    trailing = {
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.colorScheme.textSecondary)
                    }
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
private fun OrderQuickCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    count: Int?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (count != null && count > 0) {
                Text(
                    text = stringResource(R.string.order_count_format, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.primary
                )
            }
        }
    }
}
