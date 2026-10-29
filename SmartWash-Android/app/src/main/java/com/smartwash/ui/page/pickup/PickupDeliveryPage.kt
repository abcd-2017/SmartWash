package com.smartwash.ui.page.pickup

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppConfirmDialog
import androidx.compose.foundation.BorderStroke
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.InfoRow
import com.smartwash.common.ui.components.InfoSection
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.pressable
import com.smartwash.feature.order.api.PickupDeliveryType
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.generateQrCodeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 详情三态：骨架（加载中）/ 空态 + 重试（加载失败）/ 内容 */
private enum class DetailLoadState { LOADING, ERROR, CONTENT }

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
    val view = currentView()

    if (orderId != -1L) {
        LaunchedEffect(Unit) { pickupDeliveryViewModel.getOrderDetail(orderId) }
    }

    // 详情加载失败：Toast + 复位收进 effect。原实现在组合期直接 Toast/reset，
    // 每次重组都会重复弹 Toast，且复位后的 Toast 与 UI 状态脱节。
    LaunchedEffect(getOrderDetailState) {
        val detailState = getOrderDetailState
        if (detailState is RequestState.Error) {
            Toast.makeText(context, detailState.getMessage(context), Toast.LENGTH_SHORT).show()
            pickupDeliveryViewModel.resetState()
        }
    }
    val pickupCode = orderInfo?.pickupCode?.let { it.split(":").getOrNull(2) } ?: ""
    val lockerNumber = orderInfo?.lockersVo?.lockerNumber ?: 0

    // 二维码异步生成（评审 #17）：原实现用 remember(pickupCode) 在组合期同步执行 512×512
    // 逐像素双循环，阻塞主线程。改为 produceState 以 pickupCode 为 key：生成期间保持 null
    // 展示占位（复用下方"加载中"盒子），完成后更新；重组不会重复生成，pickupCode 变化时
    // 旧生成任务随协程取消。generateQrCodeBitmap 本身保持纯函数，调用侧切到 Dispatchers.Default
    val qrBitmap by produceState<Bitmap?>(initialValue = null, pickupCode) {
        if (pickupCode.isBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) { generateQrCodeBitmap(pickupCode) }
    }
    val imageBitmap = qrBitmap?.asImageBitmap()

    // 确认操作结果统一收进同一 effect：原实现把 navigateUp 写在 Success 分支内的
    // LaunchedEffect(Unit) 里、又在分支里 reset，reset 把状态打回 Idle 后该 effect 被取消，
    // navigateUp 可能根本不执行（页面卡在原地）。Success → 触觉 → navigateUp → reset 顺序执行。
    LaunchedEffect(setOrderNextState) {
        val nextState = setOrderNextState
        when (nextState) {
            is RequestState.Success -> {
                showDialog = false
                view.performHaptic(HapticEffect.SUCCESS)
                navController.navigateUp()
                pickupDeliveryViewModel.resetNextStatusState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, nextState.getMessage(context), Toast.LENGTH_SHORT).show()
                pickupDeliveryViewModel.resetNextStatusState()
            }
            else -> {}
        }
    }

    // 三分态：Loading → 骨架；Error → 空态 + 重试；orderInfo != null → 内容。
    // 原实现只判 Loading，Error 被 reset 成 Idle 后落入内容分支，QR 占位框永远"加载中"；
    // orderId == -1L 同样会伪加载。重试只挂 Error 态（详情可能已被壳层缓存，其余分支不给入口）。
    val detailContentState = when {
        getOrderDetailState is RequestState.Loading -> DetailLoadState.LOADING
        getOrderDetailState is RequestState.Error -> DetailLoadState.ERROR
        orderInfo != null -> DetailLoadState.CONTENT
        else -> if (orderId == -1L) DetailLoadState.ERROR else DetailLoadState.LOADING
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
            // 寄件板实测 页头文字底 → 首卡顶 22：PageHeader 自带 12 内边距，补 8 收敛 4 网格
            Spacer(modifier = Modifier.height(8.dp))

            // 三分态统一进同一容器：进入 200ms 减速淡入、退出 140ms 加速淡出。
            // transitionSpec 不是 @Composable 上下文（不能在其中调 motionSpec()），
            // 故按规范 §8 用 LocalReduceMotion 显式分支：降级统一 180ms 纯淡入。
            val reduceMotion = LocalReduceMotion.current
            val detailTransition: ContentTransform =
                if (reduceMotion) {
                    fadeIn(tween(CROSSFADE_ALT_MS)) togetherWith fadeOut(tween(CROSSFADE_ALT_MS))
                } else {
                    fadeIn(enterTween(DURATION_MICRO_MS)) togetherWith fadeOut(exitTween(140))
                }
            AnimatedContent(
                targetState = detailContentState,
                transitionSpec = { detailTransition },
                label = "pickupDeliveryDetail",
                modifier = Modifier.weight(1f)
            ) { state ->
                when (state) {
                    DetailLoadState.LOADING -> LoadingState(modifier = Modifier.fillMaxSize())

                    DetailLoadState.ERROR -> EmptyState(
                        icon = Icons.Rounded.Warning,
                        message = stringResource(R.string.error_network_fail),
                        modifier = Modifier.fillMaxSize(),
                        action = if (orderId != -1L) {
                            {
                                AppButton(
                                    text = stringResource(R.string.retry),
                                    onClick = { pickupDeliveryViewModel.getOrderDetail(orderId) }
                                )
                            }
                        } else null
                    )

                    DetailLoadState.CONTENT -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AppDimens.pagePadding)
                    ) {
                        // 寄件注意事项
                        if (type == PickupDeliveryType.DELIVERY.type) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(AppDimens.radiusLg),
                                color = AppColors.colorScheme.warningContainer,
                                // 规范 §2.4 卡片唯一画法：1px 描边 + level1 阴影，两者必须成对，
                                // 只有描边无投影会被读成"被禁的裸白卡"（§3.1 禁止项）
                                shadowElevation = AppElevation.level1,
                                border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(AppDimens.cardPadding),
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
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
                            shadowElevation = AppElevation.level1,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(AppDimens.cardPadding),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 二维码成图 0ms 硬切（占位 Box ↔ Image）→ 交叉淡变；
                                // reduced motion 经 motionSpec 自动降级为 180ms 纯淡变（规范 §8）
                                Crossfade(
                                    targetState = imageBitmap != null,
                                    animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
                                    label = "pickupQrCode"
                                ) { hasQrBitmap ->
                                    val qrImage = imageBitmap
                                    if (hasQrBitmap && qrImage != null) {
                                        Image(
                                            bitmap = qrImage,
                                            contentDescription = stringResource(R.string.qr_code),
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(AppDimens.radiusLg))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(AppDimens.radiusLg))
                                                .background(AppColors.colorScheme.surface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = stringResource(R.string.loading),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = AppColors.colorScheme.textSecondary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.pickup_code) else stringResource(R.string.delivery_code),
                                    style = AppTextStyles.SectionTitle
                                )
                                Text(
                                    text = pickupCode,
                                    style = AppTextStyles.DataLarge,
                                    color = AppColors.colorScheme.primaryDark
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                // 确认按钮未接 loading：请求期间按钮不禁用不禁用无反馈，
                                // 用户会重复点确认。AppButton 内置禁用 + spinner（降级由桶 A 处理）
                                AppButton(
                                    text = if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.confirm_picked_up) else stringResource(R.string.confirm_delivered),
                                    onClick = { showDialog = true },
                                    loading = setOrderNextState is RequestState.Loading
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.pickup_code) else stringResource(R.string.delivery_code),
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary
                        )
                        Text(
                            text = pickupCode,
                            style = AppTextStyles.DataLarge,
                            color = AppColors.colorScheme.primaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.spaceSm))

                // 寄存柜信息（板实测仅 3 行：学校名称/地址/寄存柜柜号；行距节奏 34+14=48，
                // 与订单详情页同款；寄件码已在上方大字展示，不再重复成行）
                InfoSection(title = stringResource(R.string.locker_info_section), icon = Icons.Rounded.School) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        InfoRow(stringResource(R.string.school_name_label), orderInfo?.schoolsVo?.schoolName ?: "")
                        InfoRow(stringResource(R.string.address), orderInfo?.schoolsVo?.location ?: "")
                        InfoRow(stringResource(R.string.locker_number_label), "$lockerNumber")
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.spaceSm))

                // 操作指引（板实测：卡内无图标、标题 14/500 textPrimary；步骤号盒 20 r8
                // iconContainerGreen、序号 12/400 浅 onPrimaryContainer/深 primary、
                // 文本 12/400 textPrimary、盒-文 8、行距 24 = 盒 20 + 行间 4）
                InfoSection(title = stringResource(R.string.operation_guide), icon = null) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        steps.forEachIndexed { index, text ->
                            StepRow("${index + 1}", text)
                        }
                    }
                }

                // 板实测 末卡底 → 底部操作条顶 12
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            }

            // 底部操作栏（模板 E 变体）— 板实测：无 surface 底/顶线，双 48dp 胶囊按钮落在页面底上，
            // 间距 12、底部留白 16；次按钮透明底 + 1dp outline 描边 + primaryDark 字，
            // 主按钮纯色 primary + onPrimary 双主题字，文字 font.title 18/600
            PickupDeliveryActionBar(
                primaryText = if (type == PickupDeliveryType.PICKUP.type) stringResource(R.string.confirm_picked_up) else stringResource(R.string.confirm_delivered),
                onPrimaryClick = { showDialog = true },
                onContactServiceClick = {
                    // 客服功能未立项，先以 Toast 兜底（与 PickupPage/OrderDetailPage D-OD12 约定一致）
                    Toast.makeText(context, context.getString(R.string.contact_service_unavailable), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (showDialog) {
        AppConfirmDialog(
            message = if (type == PickupDeliveryType.DELIVERY.type) stringResource(R.string.confirm_put_clothes) else stringResource(R.string.confirm_taken_clothes),
            onConfirm = {
                showDialog = false
                pickupDeliveryViewModel.setOrderNextState(type, orderId, orderInfo?.pickupCode ?: "")
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 底部操作栏（模板 E 变体）— 寄件板实测：无 surface 底/顶线，按钮直接落在页面底上；
 * 高 48 / radiusFull 胶囊 / 按钮间距 12 / 底部留白 16 / 文字 font.title 18/600（板实测字距 0）；
 * 主按钮纯色 primary + onPrimary 双主题字；次按钮透明底 + 1dp outline 描边 + primaryDark 字
 */
@Composable
private fun PickupDeliveryActionBar(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    onContactServiceClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)
    ) {
        // 次按钮 — 联系客服：透明底 + 1dp outline 描边（板实测无填充）
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pressable(onClick = onContactServiceClick, debounce = false)
                .border(1.dp, AppColors.colorScheme.outline, RoundedCornerShape(AppDimens.radiusFull)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.contact_service),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.primaryDark
            )
        }
        // 主按钮 — 纯色 primary 胶囊，onPrimary 双主题成对
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pressable(onClick = onPrimaryClick, debounce = false)
                .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusFull)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    // 板实测：号盒 20dp 圆形 iconContainerGreen（浅 #F0FAF5→规范收敛 #F0FBF4 / 深 #1D3229）；
    // 序号色不对称 浅 #17322B（onPrimaryContainer）/ 深 #5BC894（primary）
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(AppColors.colorScheme.iconContainerGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) AppColors.colorScheme.primary
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(AppDimens.spaceXs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textPrimary
        )
    }
}
