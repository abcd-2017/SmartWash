package com.smartwash.ui.page.pickup

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.ui.common.AppButton
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.InfoRow
import com.smartwash.ui.common.InfoSection
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.Background
import com.smartwash.ui.theme.Divider
import com.smartwash.ui.theme.IconBox
import com.smartwash.ui.theme.Primary
import com.smartwash.ui.theme.PrimaryLight
import com.smartwash.ui.theme.TextSecondary
import com.smartwash.ui.theme.Warning
import com.smartwash.ui.theme.WarningContainer
import com.smartwash.utils.PickupDeliveryType
import com.smartwash.utils.RequestState
import com.smartwash.utils.generateQrCodeBitmap

@SuppressLint("RememberReturnType")
@Composable
fun PickupDeliveryPage(
    type: Int,
    navController: NavHostController,
    orderId: Long,
    pickupDeliveryViewModel: PickupDeliveryViewModel = hiltViewModel(),
) {
    val getOrderDetailState by pickupDeliveryViewModel.getOrderInfoDetail.collectAsState()
    val setOrderNextState by pickupDeliveryViewModel.setOrderNextState.collectAsState()
    val orderInfo by pickupDeliveryViewModel.orderInfo.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (orderId != -1L) {
        LaunchedEffect(Unit) { pickupDeliveryViewModel.getOrderDetail(orderId) }
    }

    when (getOrderDetailState) {
        is RequestState.Error -> {
            Toast.makeText(context, context.getString((getOrderDetailState as RequestState.Error).messageResId), Toast.LENGTH_SHORT).show()
            pickupDeliveryViewModel.resetState()
        }
        else -> { pickupDeliveryViewModel.resetState() }
    }
    val pickupCode = orderInfo?.pickupCode?.let { it.split(":")[2] } ?: "error"
    val lockerNumber = orderInfo?.lockersVo?.lockerNumber ?: 0

    val qrBitmap = remember(pickupCode) { generateQrCodeBitmap(pickupCode) }
    val imageBitmap = qrBitmap.asImageBitmap()

    when (setOrderNextState) {
        is RequestState.Success -> {
            showDialog = false
            LaunchedEffect(Unit) { navController.navigateUp() }
            pickupDeliveryViewModel.resetNextStatusState()
        }
        is RequestState.Error -> {
            Toast.makeText(context, context.getString((setOrderNextState as RequestState.Error).messageResId), Toast.LENGTH_SHORT).show()
            pickupDeliveryViewModel.resetNextStatusState()
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.pickup) else stringResource(R.string.delivery_type),
                onBack = { navController.navigateUp() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.pagePadding)
            ) {
                // 寄件注意事项
                if (type == PickupDeliveryType.DELIVERY.type) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = AppColors.colorScheme.warningContainer,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = AppColors.colorScheme.warning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium, color = AppColors.colorScheme.onWarningContainer)
                                Text(
                                    stringResource(R.string.notes_content),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.onWarningContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                }

                // 二维码卡
                AppCard {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = stringResource(R.string.qr_code),
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.pickup_code) else stringResource(R.string.delivery_code),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = pickupCode,
                            style = MaterialTheme.typography.displaySmall,
                            color = AppColors.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        AppButton(
                            text = if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.confirm_picked_up) else stringResource(R.string.confirm_delivered),
                            onClick = { showDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 寄存柜信息
                InfoSection(title = stringResource(R.string.locker_info_section), icon = Icons.Rounded.School) {
                    InfoRow(stringResource(R.string.school_name_label), orderInfo?.schoolsVo?.schoolName ?: "")
                    InfoRow(stringResource(R.string.address), orderInfo?.schoolsVo?.location ?: "")
                    InfoRow(stringResource(R.string.locker_number_label), "$lockerNumber")
                    InfoRow(if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.pickup_code) else stringResource(R.string.delivery_code), pickupCode)
                }

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 操作指引
                InfoSection(title = stringResource(R.string.operation_guide), icon = Icons.Rounded.Info) {
                    val steps = if (type == PickupDeliveryType.PICKUP.type) {
                        listOf(
                            stringResource(R.string.find_locker_format, "$lockerNumber"),
                            stringResource(R.string.click_pickup_button),
                            stringResource(R.string.input_pickup_code_format, pickupCode),
                            stringResource(R.string.wait_and_take_clothes)
                        )
                    } else {
                        listOf(
                            stringResource(R.string.find_locker_format, "$lockerNumber"),
                            stringResource(R.string.click_delivery_button),
                            stringResource(R.string.input_delivery_code_format, pickupCode),
                            stringResource(R.string.wait_and_put_clothes)
                        )
                    }
                    steps.forEachIndexed { index, text ->
                        StepRow("${index + 1}", text)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            text = {
                Text(
                    text = if (type == PickupDeliveryType.DELIVERY.type) stringResource(R.string.confirm_put_clothes) else stringResource(R.string.confirm_taken_clothes)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pickupDeliveryViewModel.setOrderNextState(type, orderId, orderInfo?.pickupCode ?: "")
                }) { Text(stringResource(R.string.confirm), color = AppColors.colorScheme.primary) }
            }
        )
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.colorScheme.primaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
