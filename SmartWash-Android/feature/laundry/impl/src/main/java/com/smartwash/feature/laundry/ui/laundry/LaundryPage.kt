package com.smartwash.feature.laundry.ui.laundry

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppInfoDialog
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor
import com.smartwash.feature.laundry.R
import com.smartwash.feature.payment.api.PaymentRoute

@Composable
fun LaundryPage(
    navController: NavHostController,
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

    // 原写法把 Toast / reset 放在 when 渲染分支里（组合期副作用），重组会重复弹；
    // 收进以状态为键的 LaunchedEffect，只在状态真正到达时执行一次
    LaunchedEffect(getLaundryItemState) {
        val state = getLaundryItemState
        if (state is RequestState.Error) {
            Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
            laundryViewModel.resetGetLaundryItemState()
        }
    }

    LaunchedEffect(reservationState) {
        when (reservationState) {
            is RequestState.Success -> {
                navController.navigate("${PaymentRoute.Payment.text}/${orderId}") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
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
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.pagePadding)
            ) {
                // 投递柜选择
                item {
                    Text(
                        text = stringResource(R.string.delivery_locker),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.colorScheme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 卡片规格对齐同页组「洗衣服务」画板实测：radiusLg 16 + 1px outline 描边 + 无阴影
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.radiusLg),
                        color = AppColors.colorScheme.surface,
                        shadowElevation = 0.dp,
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
                            // M3 TextButton 自带 state layer（涟漪色块不受令牌控制）→ 自绘 pressable 文字按钮
                            Text(
                                text = stringResource(R.string.change),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.colorScheme.primaryDark,
                                modifier = Modifier
                                    .pressable(onClick = { showChangeSchoolDialog = true })
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        price = stringResource(R.string.currency_format, item.basePrice.toString())
                    ) {
                        selectItemId = item.itemId
                        totalPrice = item.basePrice
                    }
                    // 规范 §3 关键常量：列表项间距 12
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 底部栏（半透明 + 顶部渐变分割线）；固定 CTA 栏需避开手势条（规范 §4 安全区）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = AppColors.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 0.dp
            ) {
                Column {
                    // 顶部渐变分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        AppColors.colorScheme.outline.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.cardPadding)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.estimated_cost),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary
                            )
                            // 预估费用随套餐 0ms 跳变 → 交叉淡变（纯淡入淡出，无位移/缩放）；
                            // reduced motion 降级为 180ms 纯淡入淡出（规范 §8）。
                            // 注意：transitionSpec 是非 composable 上下文，reduced motion 判定必须在组合期取
                            val reduceMotion = LocalReduceMotion.current
                            val priceEnterSpec = if (reduceMotion) {
                                tween<Float>(CROSSFADE_ALT_MS)
                            } else {
                                enterTween<Float>(DURATION_MICRO_MS)
                            }
                            val priceExitSpec = if (reduceMotion) {
                                tween<Float>(CROSSFADE_ALT_MS)
                            } else {
                                // 退场取进入的 60%（动效语言 v0.4）；按 README「禁止页面魔法值」走 vocabulary 常量
                                exitTween<Float>(DURATION_MICRO_MS * 6 / 10)
                            }
                            AnimatedContent(
                                targetState = totalPrice,
                                transitionSpec = {
                                    fadeIn(priceEnterSpec) togetherWith fadeOut(priceExitSpec)
                                },
                                label = "totalPrice"
                            ) { price ->
                                Text(
                                    text = stringResource(R.string.currency_format, price.toString()),
                                    style = AppTextStyles.DataLarge,
                                    // 金额主色对齐支付页惯例：primaryDark（green.700/#1E8C5C·浅 / #5BC894·深）
                                    color = AppColors.colorScheme.primaryDark
                                )
                            }
                        }
                        AppButton(
                            text = stringResource(R.string.confirm_booking),
                            onClick = {
                                if (selectItemId == -1L) showDialog = true
                                else laundryViewModel.reservationLaundry(selectItemId, totalPrice)
                            },
                            modifier = Modifier.width(160.dp),
                            loading = reservationState is RequestState.Loading
                        )
                    }
                }
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
    // 描边宽度 0.5dp ↔ 1.5dp、阴影 level1 ↔ level2 硬切 → 弹簧过渡（reduced motion 自动降级 180ms）
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 1.5.dp else 0.5.dp,
        animationSpec = motionSpec(defaultSpring()),
        label = "borderWidth"
    )
    val shadowElevation by animateFloatAsState(
        targetValue = if (isSelected) AppElevation.level2.value else AppElevation.level1.value,
        animationSpec = motionSpec(defaultSpring()),
        label = "cardElevation"
    )
    // 指示器容器底色：原 if/else 瞬跳 → 统一走 selectedColor（200ms 档弹簧，reduced motion 降级 180ms）
    val indicatorColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primary.copy(alpha = 0.15f)
        else AppColors.colorScheme.divider
    )

    val interactionSource = remember { MutableInteractionSource() }
    // 卡片规格对齐同页组「洗衣服务」画板实测：radiusLg 16 + 1px outline 描边 + 无阴影
    // （规范 §3：卡片 elevation 0 用描边代替；选中强调由 primaryLight 底 + primary 描边承担）
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 原 clickable(indication = LocalIndication.current) 会浮出 Material 涟漪 → pressable（自带防抖与降级）
            .pressable(onClick = {
                view.performHaptic(HapticEffect.SELECTION)
                cardClick()
            }),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = bgColor,
        shadowElevation = 0.dp,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
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
                modifier = Modifier.weight(8f)
            ) {
                // 选中指示器 — 36dp 盒 + radiusSmall，选中底 primaryLight（规范选中底语义色）
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusSmall))
                        .background(
                            if (isSelected) AppColors.colorScheme.primaryLight
                            else AppColors.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // 勾选图标 ↔ 灰圆点瞬换 → 交叉淡变（180ms 降级档，纯淡入淡出）
                    Crossfade(
                        targetState = isSelected,
                        animationSpec = tween(CROSSFADE_ALT_MS),
                        label = "checkIndicator"
                    ) { selected ->
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AppColors.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.colorScheme.textTertiary)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    // 字重 Bold ↔ Normal 切换会改变文本宽度引发布局抖动 → 两态统一走 titleLarge 令牌字重
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.End) {
                Text(
                    text = price,
                    // 价格色对齐服务页画板实测：green.700（浅 #1E8C5C / 深 #5BC894）
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.primaryDark
                )
            }
        }
    }
}
