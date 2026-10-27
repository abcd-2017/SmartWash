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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

            // 胶囊筛选（设计稿：视觉胶囊 40 高 r999 + 48 热区，文字 captionStrong 12/16/500）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppDimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTabIndex == index
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .pressable(onClick = { selectedTabIndex = index }, debounce = false),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.height(40.dp),
                            color = if (selected) AppColors.colorScheme.primary else AppColors.colorScheme.surface,
                            border = if (selected) null else BorderStroke(1.dp, AppColors.colorScheme.outline),
                            shape = RoundedCornerShape(AppDimens.radiusFull)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else AppColors.colorScheme.textSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
 * 票券卡 — 左金额区 + 垂直虚线分隔 + 右内容区 + 状态角标
 * 设计稿「优惠券」画板：卡 312×88、r16、描边 1px outline、无阴影（规范 §3 卡片 elevation 0）；
 * 可用态金额区白底（画板无填充）、历史态金额区为页面底色块；弱化态靠换灰，不做整体透明
 */
@Composable
fun UserCouponCard(
    couponVo: UserCouponVo,
    isHistorical: Boolean,
    modifier: Modifier = Modifier,
) {
    val amountColor = if (isHistorical) AppColors.colorScheme.textSecondary else AppColors.colorScheme.primaryDark
    val titleColor = if (isHistorical) AppColors.colorScheme.textSecondary else AppColors.colorScheme.textPrimary
    val statusText = if (couponVo.isUsed) stringResource(R.string.used) else stringResource(R.string.expired)

    val outlineColor = AppColors.colorScheme.outline
    val surfaceColor = AppColors.colorScheme.surface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // 中间虚线 — 设计稿：1dp 竖线、段 6 / 隔 4，上下端避开打孔（φ12 @ x=88）
                val dashWidth = 6.dp.toPx()
                val dashGap = 4.dp.toPx()
                val x = 88.dp.toPx()
                val strokeWidth = 1.dp.toPx()
                var y = 14.dp.toPx()
                val maxY = size.height - 8.dp.toPx()
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
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧金额区 — 金额 titleLarge(22/28/600)+tnum，单位 caption 12/400
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(88.dp)
                    .clip(TicketShape)
                    .then(
                        if (isHistorical) Modifier.background(AppColors.colorScheme.background)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.currency_format, String.format("%.0f", couponVo.couponVo.discount)),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = amountColor
                    )
                    Text(
                        text = stringResource(com.smartwash.feature.coupon.R.string.coupon),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // 右侧内容区 — 标题行（标题 + 状态角标）与标题垂直居中，行间距 4
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 24.dp, top = 20.dp, end = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = couponVo.couponVo.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = titleColor,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 状态角标 — 可用态 primaryContainer/onPrimaryContainer，弱化态 surfaceVariant/textSecondary
                    Surface(
                        shape = RoundedCornerShape(AppDimens.radiusFull),
                        color = if (isHistorical) AppColors.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isHistorical) statusText else stringResource(R.string.available),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isHistorical) AppColors.colorScheme.textSecondary
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (couponVo.couponVo.threshold == 0f)
                        stringResource(R.string.no_threshold)
                    else
                        stringResource(R.string.coupon_min_amount_format, "${couponVo.couponVo.threshold}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.valid_until, "${couponVo.expiredAt}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
        }
    }
}

/**
 * 票券形状 — r16 圆角矩形 + 金额区右缘（88dp）与卡顶/底边相交处的 φ12 打孔凹口
 * 设计稿「优惠券」画板：上打孔/下打孔 ellipse 12×12，圆心落在卡边缘线上
 */
private val TicketShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cardRadius = with(density) { 16.dp.toPx() }
        val notchRadius = with(density) { 6.dp.toPx() }
        val notchX = with(density) { 88.dp.toPx() }
        val w = size.width
        val h = size.height

        val path = Path().apply {
            // 顶边：左上圆角起点 → 上打孔凹口（凹进卡内）→ 右上圆角
            moveTo(cardRadius, 0f)
            lineTo(notchX - notchRadius, 0f)
            arcTo(
                rect = Rect(notchX - notchRadius, -notchRadius, notchX + notchRadius, notchRadius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(w - cardRadius, 0f)
            arcTo(Rect(w - 2 * cardRadius, 0f, w, 2 * cardRadius), 270f, 90f, false)
            // 右边 → 底边：右下圆角 → 下打孔凹口 → 左下圆角
            lineTo(w, h - cardRadius)
            arcTo(Rect(w - 2 * cardRadius, h - 2 * cardRadius, w, h), 0f, 90f, false)
            lineTo(notchX + notchRadius, h)
            arcTo(
                rect = Rect(notchX - notchRadius, h - notchRadius, notchX + notchRadius, h + notchRadius),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(cardRadius, h)
            arcTo(Rect(0f, h - 2 * cardRadius, 2 * cardRadius, h), 90f, 90f, false)
            // 左边 → 闭合左上圆角
            lineTo(0f, cardRadius)
            arcTo(Rect(0f, 0f, 2 * cardRadius, 2 * cardRadius), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}
