package com.smartwash.ui.page.coupon.tab

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartwash.R
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.CouponStatus

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
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧金额
            Column(
                modifier = Modifier.width(80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.discount_amount_format, "${coupon.discount}"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.textSecondary)
                        ) { Text(stringResource(R.string.claimed)) }
                    } else {
                        Button(
                            onClick = onClaimClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.claim_now))
                        }
                    }
                }
            }
        }
    }
}
