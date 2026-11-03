package com.smartwash.feature.coupon.ui.coupon.tab

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.softCardShadow
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
import com.smartwash.feature.coupon.ui.coupon.TicketShape

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
        modifier = Modifier.fillMaxSize(),
        // 2026-11-01 阴影裁剪修复：容器级 horizontal padding 会把卡片钉在裁剪边界上 → contentPadding
        contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
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
    val outlineColor = AppColors.colorScheme.outline
    // 2026-11-01 用户拍板：可领取卡（设计稿无画板）统一成屏 12 票券形态——
    // 打孔 + 绿面额区 + 垂直虚线 + 右区，与已领取/过期 tab 同一卡片语言；领取按钮占状态字位
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // 中间虚线 — 同票券：1dp 竖线、段 6 / 隔 4，上下 margin 12（x=88 face 右缘）
                val dashWidth = 6.dp.toPx()
                val dashGap = 4.dp.toPx()
                val x = 88.dp.toPx()
                val strokeWidth = 1.dp.toPx()
                var y = 12.dp.toPx()
                val maxY = size.height - 12.dp.toPx()
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
            }.softCardShadow(),
        shape = TicketShape,
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // 左侧金额区 — 票券 face：brand-soft 纯色底 + 面额 + 单位字（纵向贯通两行）
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.discount_amount_format, "${coupon.discount}"),
                        style = AppTextStyles.AmountMedium,
                        color = AppColors.colorScheme.primaryDark
                    )
                    Text(
                        text = stringResource(R.string.coupon),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 右侧两行结构（2026-11-01 实机修正：88dp 单行塞不下「内容+按钮」，按钮独立成行）：
            // 上 = 标题/日期/门槛三行；下 = 领取按钮右对齐
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = coupon.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${coupon.startTime.split(" ")[0]} - ${coupon.endTime.split(" ")[0]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (coupon.threshold == 0f) stringResource(R.string.no_threshold) else stringResource(R.string.coupon_min_amount_format, "${coupon.threshold}"),
                    // 门槛是金额，开 tnum（规范 §2.2）
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
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

    // 触控热区 48dp 外层承载（视觉胶囊 40 高，与画板「热区-复制」60×48 外扩同模式），
    // 按压/防抖挂在外层，视觉层只负责底色与内容
    Box(
        modifier = Modifier
            .height(48.dp)
            .then(
                if (clickable) {
                    Modifier.pressable(
                        onClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) { onClaimClick() }
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // §3.3 小按钮档：36dp 高 / 12dp 圆角 / 横向 16（文字 13sp/600）
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(containerColor)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alpha(if (isClaiming) 0f else 1f)
                )
            }
            if (isClaiming) {
                ClaimingRing(color = Color.White)
            }
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
