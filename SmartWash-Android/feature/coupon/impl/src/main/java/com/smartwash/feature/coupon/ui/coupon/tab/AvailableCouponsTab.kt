package com.smartwash.feature.coupon.ui.coupon.tab

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.ClickDebouncer
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.feature.coupon.CouponStatus

@Composable
fun AvailableCouponsTab(
    couponList: List<CouponVo>,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_coupons_available),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
        } else {
            items(couponList) { coupon ->
                CouponCard(
                    coupon = coupon,
                    isAvailable = true,
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
                    if (coupon.status == CouponStatus.RECEIVE.status) {
                        // 已领取 — 纯展示态（无动作、无按压反馈）
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .background(AppColors.colorScheme.textSecondary, RoundedCornerShape(AppDimens.radiusMd))
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.claimed), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        // 立即领取 — 自绘（领券点击防抖：receiveCoupon 无 Loading 守卫，响应期间按钮始终可点）
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .pressable(
                                    onClick = rememberDebouncedClick(ClickDebouncer.ACTION_CLICK_INTERVAL_MS) { onClaimClick() }
                                )
                                .background(AppColors.colorScheme.primary, RoundedCornerShape(AppDimens.radiusMd))
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.claim_now), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
