package com.smartwash.feature.coupon.ui.coupon.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.feature.coupon.ui.coupon.UserCouponCard
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens

@Composable
fun ClaimedCouponsTab(claimedCoupons: List<UserCouponVo>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        if (claimedCoupons.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_claimed_coupons),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
        } else {
            items(claimedCoupons) { coupon ->
                UserCouponCard(coupon = coupon, isHistorical = false)
            }
        }
    }
}
