package com.smartwash.feature.coupon.ui.coupon.tab

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.SPIN_PERIOD_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.selectedColor
import com.smartwash.common.utils.spinInfinite
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.feature.coupon.CouponStatus

/** 领券进行中圆环直径 */
private val ClaimingRingSize = 14.dp

/** 领券进行中圆环描边宽 */
private val ClaimingRingStroke = 2.dp

@Composable
fun AvailableCouponsTab(
    couponList: List<CouponVo>,
    claimingId: Long?,
    itemClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        if (couponList.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Info,
                    message = stringResource(R.string.no_coupons_available)
                )
            }
        } else {
            items(couponList, key = { it.couponId }) { coupon ->
                CouponCard(
                    coupon = coupon,
                    isAvailable = true,
                    isClaiming = coupon.couponId == claimingId,
                    onClaimClick = { itemClick(coupon.couponId) }
                )
            }
        }
    }
}

@Composable
fun CouponCard(
    coupon: CouponVo,
    isAvailable: Boolean,
    isClaiming: Boolean,
    onClaimClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧金额
            Column(
                modifier = Modifier.width(80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.discount_amount_format, "${coupon.discount}"),
                    style = AppTextStyles.AmountMedium,
                    color = AppColors.colorScheme.primary
                )
            }

            // 竖线分隔
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(48.dp)
                    .background(AppColors.colorScheme.divider)
            )

            // 右侧信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = coupon.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${coupon.startTime.split(" ")[0]} - ${coupon.endTime.split(" ")[0]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textTertiary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (coupon.threshold == 0f) stringResource(R.string.no_threshold) else stringResource(R.string.coupon_min_amount_format, "${coupon.threshold}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                if (isAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ClaimAction(
                        claimed = coupon.status == CouponStatus.RECEIVE.status,
                        isClaiming = isClaiming,
                        onClaimClick = onClaimClick,
                    )
                }
            }
        }
    }
}

/**
 * 领取按钮 — "立即领取 / 已领取 / 领取中"三态共用同一个容器，避免状态切换时布局跳变：
 * - 底色经 [selectedColor] 过渡（primary → textSecondary），不再随整屏刷新硬切；
 * - 领取中：文字保持占位（alpha 0）换 14dp 圆环并禁点，防重复提交；
 * - reduced motion：圆环静止（规范 §8 取消无限旋转），颜色过渡降级 180ms。
 */
@Composable
private fun ClaimAction(
    claimed: Boolean,
    isClaiming: Boolean,
    onClaimClick: () -> Unit,
) {
    val containerColor = selectedColor(
        if (claimed) AppColors.colorScheme.textSecondary else AppColors.colorScheme.primary
    )
    val clickable = !claimed && !isClaiming

    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(containerColor)
            .then(
                if (clickable) {
                    Modifier.pressable(
                        onClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) { onClaimClick() }
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // 文字 ↔ 圆环交叉淡变；文字用 alpha 隐藏而非移除，保证按钮宽度不抖动
        Crossfade(
            targetState = claimed,
            animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
            label = "couponClaimLabel",
        ) { isClaimed ->
            Text(
                text = stringResource(if (isClaimed) R.string.claimed else R.string.claim_now),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(if (isClaiming) 0f else 1f)
            )
        }
        if (isClaiming) {
            ClaimingRing(color = Color.White)
        }
    }
}

/** 领券进行中圆环 — reduced motion 时静止在当前角度（静态 270° 圆环），不旋转 */
@Composable
private fun ClaimingRing(
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (LocalReduceMotion.current) {
        Canvas(modifier = modifier.size(ClaimingRingSize)) {
            drawClaimingRing(color)
        }
    } else {
        val transition = rememberInfiniteTransition(label = "claimingRing")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = spinInfinite(SPIN_PERIOD_MS),
            label = "claimingRingAngle"
        )
        Canvas(modifier = modifier.size(ClaimingRingSize)) {
            rotate(angle) {
                drawClaimingRing(color)
            }
        }
    }
}

private fun DrawScope.drawClaimingRing(color: Color) {
    val stroke = ClaimingRingStroke.toPx()
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
}
