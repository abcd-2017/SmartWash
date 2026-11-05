package com.smartwash.feature.laundry.ui.laundry

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor
import com.smartwash.feature.laundry.R
import com.smartwash.feature.payment.api.PaymentRoute

@Composable
fun LaundryPage(
    navController: NavHostController,
    // 路由可选参数：服务页套餐行跳入时预选该套餐（-1 = 不指定，见 LaundryRoute.Laundry）
    defaultItemId: Long = -1L,
    laundryViewModel: LaundryViewModel = hiltViewModel(),
) {
    val getLaundryItemState by laundryViewModel.getLaundryItemState.collectAsState()
    val laundryItems by laundryViewModel.laundryItems.collectAsState()
    var selectItemId by remember { mutableLongStateOf(-1) }
    var totalPrice by remember { mutableFloatStateOf(0f) }
    var showDialog by remember { mutableStateOf(false) }
    var showChangeSchoolDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val reservationState by laundryViewModel.reservationState.collectAsState()
    val orderId by laundryViewModel.orderId.collectAsState()
    val userInfo by laundryViewModel.userInfo.collectAsState()

    LaunchedEffect(Unit) { laundryViewModel.getLaundryItem() }

    // 套餐列表就绪后落地路由预选（仅首次，用户手动改选后不再覆盖）
    LaunchedEffect(laundryItems, defaultItemId) {
        if (defaultItemId > 0 && selectItemId == -1L) {
            val item = laundryItems.firstOrNull { it.itemId == defaultItemId }
            if (item != null) {
                selectItemId = item.itemId
                totalPrice = item.basePrice
            }
        }
    }

    // 原写法把 Toast / reset 放在 when 渲染分支里（组合期副作用），重组会重复弹；
    // 收进以状态为键的 LaunchedEffect，只在状态真正到达时执行一次
    LaunchedEffect(getLaundryItemState) {
        val state = getLaundryItemState
        if (state is RequestState.Error) {
            Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
            laundryViewModel.resetGetLaundryItemState()
        }
    }

    // P-601：预约成功导航到支付页后必须立即 reset reservationState。此前 Success 残留在
    // ViewModel（其存活于本页返回栈条目），从支付页返回时本页重新组合，本 effect 以相同
    // key（Success）重启并重放导航体 → 瞬间又被弹回支付页，且支付页 LaunchedEffect(Unit)
    // 重新拉单（logcat 可见 GET /orders/{id} 重发），用户被卡在支付页。
    // reset 置回 Idle 后下一帧本 effect 以 Idle 键重启仅走 else 分支，返回时不再重放导航。
    // 顺序对齐 PickupDeliveryPage / RechargePage 既有模式：先导航后 reset（reset 放导航前
    // 会把状态打回 Idle 使 effect 被取消，导航可能不执行）。
    LaunchedEffect(reservationState) {
        when (reservationState) {
            is RequestState.Success -> {
                navController.navigate(PaymentRoute.Payment(orderId)) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                laundryViewModel.resetReservationState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (reservationState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                laundryViewModel.resetReservationState()
            }
            else -> {}
        }
    }

    if (showDialog) {
        AppInfoDialog(
            message = stringResource(R.string.please_select_package),
            onDismiss = { showDialog = false }
        )
    }

    if (showChangeSchoolDialog) {
        AppInfoDialog(
            message = stringResource(R.string.change_school_contact_service),
            onDismiss = { showChangeSchoolDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        // 骨架 ↔ 内容整屏硬切 → 交叉淡变（reduced motion 经 motionSpec 自动降级为 180ms）
        Crossfade(
            targetState = getLaundryItemState is RequestState.Loading,
            animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
            modifier = Modifier.fillMaxSize(),
            label = "laundryContent"
        ) { isLoading ->
        if (isLoading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.book_locker), onBack = { navController.navigateUp() })

            LazyColumn(
                modifier = Modifier.weight(1f),
                // 2026-11-01 阴影裁剪修复：见 PickupPage 同项
                contentPadding = PaddingValues(horizontal = AppDimens.pagePadding)
            ) {
                // 投递柜选择
                item {
                    Text(
                        text = stringResource(R.string.delivery_locker),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.colorScheme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 卡片规格对齐同页组「洗衣服务」画板实测：radiusLg 16 + 1px outline 描边
                    // + 阴影 AppElevation.level1（规范 §2.4 卡片档，替代原「无阴影」写法）
                    Surface(
                        modifier = Modifier.fillMaxWidth().softCardShadow(),
                        shape = RoundedCornerShape(AppDimens.radiusLg),
                        color = AppColors.colorScheme.surface,
                        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppDimens.cardPadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 绿系 iconTile：primaryLight 底 + primaryDark 前景（画板实测绿 tile 配对），
                                // 图标 24 = icon.md 档
                                IconBox(
                                    icon = Icons.Default.LocationOn,
                                    size = 44.dp,
                                    iconSize = 24.dp,
                                    containerColor = AppColors.colorScheme.primaryLight,
                                    iconTint = AppColors.colorScheme.primaryDark
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = userInfo?.school?.schoolName ?: "",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = userInfo?.school?.location ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(0.6f),
                                        overflow = TextOverflow.Ellipsis,
                                        color = AppColors.colorScheme.textSecondary
                                    )
                                }
                            }
                            // M3 TextButton 自带 state layer（涟漪色块不受令牌控制）→ 自绘 pressable 文字按钮；
                            // 垂直 12 使热区达 48dp（正文 24 行高 + 24，原 8 只有 40dp 触控不达标）
                            Text(
                                text = stringResource(R.string.change),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.colorScheme.primaryDark,
                                modifier = Modifier
                                    .pressable(onClick = { showChangeSchoolDialog = true })
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            )
                        }
                    }
                }

                // 套餐选择
                item {
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    Text(
                        text = stringResource(R.string.laundry_package),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.colorScheme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(laundryItems, key = { it.itemId }) { item ->
                    LaundryTypeItem(
                        title = item.itemName,
                        description = item.description,
                        isSelected = selectItemId == item.itemId,
                        price = stringResource(R.string.currency_format, String.format("%.2f", item.basePrice))
                    ) {
                        selectItemId = item.itemId
                        totalPrice = item.basePrice
                    }
                    // 卡间距 16dp（规范 §2.3；原 12dp 属列表项间距旧值）
                    Spacer(modifier = Modifier.height(AppDimens.cardSpacing))
                }
            }

            // 底部 CTA 栏 — 对齐支付页固定按钮形态：0.5dp 分隔线（§2.4 浮层边缘豁免）+ 全宽 AppButton，
            // 金额内嵌按钮文案（原左置 36sp DataLarge 与按钮构成双锚点且 160dp 定宽不符 §3.3/§1.2）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.colorScheme.surface)
            ) {
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.outline)
                AppButton(
                    text = if (selectItemId == -1L) stringResource(R.string.confirm_booking)
                    else stringResource(
                        R.string.confirm_booking_format,
                        // 金额统一走 currency_format（¥%s），原直拼 %.2f 导致按钮只显示「\prod预约 · 29.90」无 ¥
                        stringResource(R.string.currency_format, String.format("%.2f", totalPrice))
                    ),
                    onClick = {
                        if (selectItemId == -1L) showDialog = true
                        else laundryViewModel.reservationLaundry(selectItemId, totalPrice)
                    },
                    loading = reservationState is RequestState.Loading,
                    // 对齐支付页画板实测：左右 24、上 16、下 20，navigationBarsPadding 避让手势条（规范 §4）
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp)
                )
            }
        }
        }
        }
    }
}

@Composable
private fun LaundryTypeItem(
    title: String,
    description: String,
    isSelected: Boolean,
    price: String,
    cardClick: () -> Unit,
) {
    val view = currentView()
    // 选中态颜色过渡动画
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) AppColors.colorScheme.primaryLight else AppColors.colorScheme.surface,
        animationSpec = defaultSpring(),
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.outline,
        animationSpec = defaultSpring(),
        label = "borderColor"
    )
    // 指示器圆环色：选中 primary / 未选中 outline（空心环语义）
    val indicatorRingColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.outline
    )
    // 勾的缩放：选中 1 / 未选中 0（弹簧，reduced motion 经 motionSpec 降级）
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = motionSpec(defaultSpring()),
        label = "checkScale"
    )

    // 卡片规格：radiusLg 16 + 1px outline 描边 + 阴影 level1（规范 §2.4 卡片档）。
    // 选中强调由 primaryLight 底 + primary 描边承担，描边宽度不再随选中变化（原 0.5/1/1.5dp
    // 三档与弹簧过渡未接入绘制，属死代码，一并移除——视觉与时序均无变化）
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 原 clickable 直挂 Material 涟漪（§7 禁令）→ pressable（自带防抖与降级）
            .pressable(onClick = {
                view.performHaptic(HapticEffect.SELECTION)
                cardClick()
            }).softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 选中指示器 — 20dp 圆环 + 勾，对齐公共 SelectionDot 的语言
                // （未选 = outline 空心环 / 选中 = primary 环 + 勾）。
                // 2026-11-01：未选中态由「灰实心点」改为空心圆环（原实心点观感突兀）；
                // 2026-11-04：移除 36dp 灰盒底 —— 未选态灰盒(surfaceVariant #EFF1EE)与浅环
                // (outline #E3E7E2)对比度仅 Δ9/255，环几乎不可见、观感是一块「脏灰」；
                // 选中态盒底与卡面同色本就不可见，盒底纯属噪音。去掉后环直接落卡面（Δ25，
                // 与支付/充值页 SelectionDot 同口径），行高仍由右侧两行文字决定，布局无变化
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        // U-119：描边统一 1px（规范 §3），原 1.5dp 不在允许集
                        .border(1.dp, indicatorRingColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // 勾：选中时自圆心弹簧长出（尺寸恒定，仅内容显现/隐去）
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                            },
                        tint = AppColors.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    // 套餐卡标题（2026-11-01 用户反馈）：18sp 仍偏大、与下方描述差距过大 → 降 16sp/500；
                    // 标题与描述间补 4dp（原为 0，两行贴死更显标题沉重）
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
            // 金额（2026-11-01）：原 weight(2f) 只分到 54dp，¥29.90（约 60dp）必然折行 →
            // 改自然宽度右对齐 + 限单行；左侧信息列 weight(1f) 吸收余量
            Row(modifier = Modifier, horizontalArrangement = Arrangement.End) {
                Text(
                    text = price,
                    // 价格色对齐服务页画板实测：green.700（浅 #1E8C5C / 深 #5BC894）
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.primaryDark,
                    maxLines = 1
                )
            }
        }
    }
}
