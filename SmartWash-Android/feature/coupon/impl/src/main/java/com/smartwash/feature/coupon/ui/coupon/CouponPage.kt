package com.smartwash.feature.coupon.ui.coupon

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.feature.coupon.ui.coupon.tab.AvailableCouponsTab
import com.smartwash.feature.coupon.ui.coupon.tab.ClaimedCouponsTab
import com.smartwash.feature.coupon.ui.coupon.tab.HistoricalCouponsTab
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable

@Composable
fun CouponPage(
    navController: NavHostController,
    couponViewModel: CouponViewModel = hiltViewModel(),
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val loadState by couponViewModel.loadState.collectAsState()
    val availableCoupons by couponViewModel.availableCoupons.collectAsState()
    val claimedCoupons by couponViewModel.claimedCoupons.collectAsState()
    val historicalCoupons by couponViewModel.historicalCoupons.collectAsState()
    // D-C1：可用 tab 文案带真实计数
    val tabs = listOf(
        stringResource(R.string.available_coupons_with_count, availableCoupons.size),
        stringResource(R.string.claimed_coupons),
        stringResource(R.string.historical_coupons)
    )
    val context = LocalContext.current
    val receiveCouponState by couponViewModel.receiveCouponState.collectAsState()

    LaunchedEffect(Unit) {
        couponViewModel.loadAllCoupons()
    }

    LaunchedEffect(receiveCouponState) {
        when (receiveCouponState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.claim_success), Toast.LENGTH_SHORT).show()
                couponViewModel.resetReceiveState()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (receiveCouponState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                couponViewModel.resetReceiveState()
            }
            else -> {}
        }
    }

    LaunchedEffect(loadState) {
        if (loadState is RequestState.Error) {
            Toast.makeText(context, (loadState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.coupons), onBack = { navController.navigateUp() })

            // 胶囊筛选（seg3 样式）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.pagePadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTabIndex == index
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .pressable(onClick = { selectedTabIndex = index }, debounce = false),
                        color = if (selected) AppColors.colorScheme.primary else AppColors.colorScheme.surface,
                        border = if (selected) null else BorderStroke(1.dp, AppColors.colorScheme.outline),
                        shape = RoundedCornerShape(AppDimens.radiusFull)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) Color.White else AppColors.colorScheme.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (loadState is RequestState.Loading) {
                LoadingState(modifier = Modifier.fillMaxSize())
            } else {
                when (selectedTabIndex) {
                    0 -> AvailableCouponsTab(availableCoupons) {
                        couponViewModel.receiveCoupon(it)
                    }
                    1 -> ClaimedCouponsTab(claimedCoupons)
                    2 -> HistoricalCouponsTab(historicalCoupons)
                }
            }
        }
    }
}

/**
 * 票券卡片（ticket3 样式）— 左右半圆打孔 + 中间虚线
 * 设计稿 §12：左侧金额区 brand-soft 纯色底，右侧内容区白色
 */
@Composable
fun UserCouponCard(
    couponVo: UserCouponVo,
    isHistorical: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (isHistorical) 0.55f else 1f
    val amountColor = if (isHistorical) AppColors.colorScheme.textTertiary else AppColors.colorScheme.primaryDark
    val unitColor = if (isHistorical) AppColors.colorScheme.textTertiary else AppColors.colorScheme.primary
    val titleColor = if (isHistorical) AppColors.colorScheme.textSecondary else AppColors.colorScheme.textPrimary
    val statusColor = if (isHistorical) AppColors.colorScheme.textTertiary else AppColors.colorScheme.primary
    val statusText = if (couponVo.isUsed) stringResource(R.string.used) else stringResource(R.string.expired)

    val outlineColor = AppColors.colorScheme.outline
    val surfaceColor = AppColors.colorScheme.surface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .drawBehind {
                // 中间虚线 — 规范 §3.1 票券卡
                val dashWidth = 6.dp.toPx()
                val dashGap = 4.dp.toPx()
                val x = 88.dp.toPx()
                val strokeWidth = 1.dp.toPx()
                var y = 16.dp.toPx()
                val maxY = size.height - 16.dp.toPx()
                while (y < maxY) {
                    drawLine(
                        color = outlineColor,
                        start = Offset(x, y),
                        end = Offset(x, (y + dashWidth).coerceAtMost(maxY)),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    y += dashWidth + dashGap
                }
            },
        shape = TicketShape,
        color = surfaceColor,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧金额区 — brand-soft 纯色底（历史态 surface-2 纯色）
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(88.dp)
                    .background(
                        // brand-soft 等价令牌经 M3 原生 ColorScheme（AppColorScheme 无 primaryContainer 字段）
                        color = if (isHistorical) AppColors.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.currency_format, String.format("%.0f", couponVo.couponVo.discount)),
                        style = AppTextStyles.AmountMedium,
                        color = amountColor
                    )
                    Text(
                        text = stringResource(com.smartwash.feature.coupon.R.string.coupon),
                        style = MaterialTheme.typography.labelSmall,
                        color = unitColor,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }

            // 右侧内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, top = 20.dp, bottom = 20.dp)
            ) {
                Text(
                    text = couponVo.couponVo.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (couponVo.couponVo.threshold == 0f)
                        stringResource(R.string.no_threshold)
                    else
                        stringResource(R.string.coupon_min_amount_format, "${couponVo.couponVo.threshold}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.valid_until, "${couponVo.expiredAt}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }

            // 状态标签
            Text(
                text = if (isHistorical) statusText else stringResource(R.string.available),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

/**
 * 票券形状 — 左右半圆打孔
 * 设计稿 §12：ticket3::before 和 ticket3::after
 * 左侧凹口在票券左边缘，右侧凹口在金额区（88dp）右边缘
 */
private val TicketShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val notchRadius = 7f
            val rightNotchX = 88f
            val centerY = size.height / 2

            // 从左上角开始
            moveTo(0f, 0f)
            // 顶边到右侧凹口顶部
            lineTo(rightNotchX, 0f)
            // 到右侧凹口起点
            lineTo(rightNotchX, centerY - notchRadius)
            // 右侧凹口（向下凹的半圆）
            arcTo(
                rect = Rect(
                    rightNotchX - notchRadius,
                    centerY - notchRadius,
                    rightNotchX + notchRadius,
                    centerY + notchRadius
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            // 继续到右下角
            lineTo(size.width, size.height)
            // 底边到左侧
            lineTo(0f, size.height)
            // 到左侧凹口起点
            lineTo(0f, centerY + notchRadius)
            // 左侧凹口（向上凹的半圆）
            arcTo(
                rect = Rect(
                    -notchRadius,
                    centerY - notchRadius,
                    notchRadius,
                    centerY + notchRadius
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }
        return Outline.Generic(path)
    }
}
