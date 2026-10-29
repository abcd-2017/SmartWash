package com.smartwash.feature.coupon.ui.coupon

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.AppPillTabRow
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.feature.coupon.ui.coupon.tab.AvailableCouponsTab
import com.smartwash.feature.coupon.ui.coupon.tab.ClaimedCouponsTab
import com.smartwash.feature.coupon.ui.coupon.tab.HistoricalCouponsTab
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.DURATION_CARD_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.motionSpec
import kotlinx.coroutines.launch

/** 优惠券页 tab 数：可领取 / 已领取 / 已使用 */
private const val COUPON_TAB_COUNT = 3

/** 优惠券页内容区三态：首载骨架 / 首载失败 / 内容（三 tab Pager） */
private enum class CouponContentState { Skeleton, Error, Content }

@Composable
fun CouponPage(
    navController: NavHostController,
    couponViewModel: CouponViewModel = hiltViewModel(),
) {
    val reduceMotion = LocalReduceMotion.current
    val coroutineScope = rememberCoroutineScope()
    // 三 tab 改由 Pager 承载：页间切换不再重建列表，LazyLayout 按页保存滚动位置
    val pagerState = rememberPagerState(pageCount = { COUPON_TAB_COUNT })
    val loadState by couponViewModel.loadState.collectAsState()
    val availableCoupons by couponViewModel.availableCoupons.collectAsState()
    val claimedCoupons by couponViewModel.claimedCoupons.collectAsState()
    val historicalCoupons by couponViewModel.historicalCoupons.collectAsState()
    // 领券进行中（页面局部 UI 状态）：按钮原位换圆环并禁点，阻断重复提交
    var claimingId by remember { mutableStateOf<Long?>(null) }
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
        when (val state = receiveCouponState) {
            is RequestState.Success -> {
                // 领券收尾：成功也清 pending（loadAllCoupons 期间列表保持原位，不再整屏骨架）
                claimingId = null
                Toast.makeText(context, context.getString(R.string.claim_success), Toast.LENGTH_SHORT).show()
                couponViewModel.resetReceiveState()
            }
            is RequestState.Error -> {
                claimingId = null
                Toast.makeText(context, state.getMessage(context), Toast.LENGTH_SHORT).show()
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

    // 首载骨架：三列表都空且状态为 Idle/Loading（Idle 首帧同按加载处理，避免空态闪现）。
    // 领券后 loadAllCoupons 会把 loadState 置回 Loading，但此时列表已有内容 → 保留内容，不整屏替换。
    val listsEmpty = availableCoupons.isEmpty() && claimedCoupons.isEmpty() && historicalCoupons.isEmpty()
    val contentState = when {
        listsEmpty && (loadState is RequestState.Idle || loadState is RequestState.Loading) ->
            CouponContentState.Skeleton
        listsEmpty && loadState is RequestState.Error -> CouponContentState.Error
        else -> CouponContentState.Content
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.coupons), onBack = { navController.navigateUp() })

            // 胶囊筛选（seg3 样式）— 公共 AppPillTabRow：选中色过渡 + 选中触觉 + 禁 ripple
            AppPillTabRow(
                tabs = tabs,
                selectedIndex = pagerState.targetPage,
                onSelect = { index ->
                    coroutineScope.launch {
                        // reduced motion：取消位移动画，直接落到目标页（规范 §8）
                        if (reduceMotion) pagerState.scrollToPage(index)
                        else pagerState.animateScrollToPage(index)
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // 骨架 ↔ 内容交叉淡变；reduced motion 经 motionSpec 降级为 180ms 纯淡变
                Crossfade(
                    targetState = contentState,
                    animationSpec = motionSpec(enterTween(DURATION_CARD_MS)),
                    label = "couponContent",
                ) { state ->
                    when (state) {
                        CouponContentState.Skeleton -> LoadingState(modifier = Modifier.fillMaxSize())

                        // 首载失败（三列表均空）：给出说明与重试，不再只靠 Toast
                        CouponContentState.Error -> EmptyState(
                            // EmptyState 只渲染滚筒圆母题（§3.2 禁裸图标着色）：icon 不渲染，仅占位（core 图标，不引 icons-extended）
                            icon = Icons.Default.Refresh,
                            message = stringResource(R.string.coupon_load_failed),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = AppDimens.pagePadding),
                            action = {
                                AppButton(
                                    text = stringResource(R.string.coupon_action_retry),
                                    onClick = { couponViewModel.loadAllCoupons() },
                                    variant = AppButtonVariant.SECONDARY,
                                )
                            },
                        )

                        CouponContentState.Content -> HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            when (page) {
                                0 -> AvailableCouponsTab(
                                    couponList = availableCoupons,
                                    claimingId = claimingId,
                                    itemClick = { couponId ->
                                        claimingId = couponId
                                        couponViewModel.receiveCoupon(couponId)
                                    },
                                )
                                1 -> ClaimedCouponsTab(claimedCoupons)
                                else -> HistoricalCouponsTab(historicalCoupons)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 票券卡 — 左金额区 + 垂直虚线分隔 + 右内容区 + 状态角标
 * 设计稿「优惠券」画板：卡 312×88、r16、描边 1px outline；
 * 阴影按规范 §2.4 卡片档 = AppElevation.level1（CSS 0 2px 12px rgba(20,40,30,.05) 的最近似档位）；
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
        shadowElevation = AppElevation.level1,
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
