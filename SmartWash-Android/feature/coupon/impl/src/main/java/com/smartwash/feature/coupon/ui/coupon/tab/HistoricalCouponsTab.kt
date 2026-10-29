package com.smartwash.feature.coupon.ui.coupon.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.feature.coupon.ui.coupon.UserCouponCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.theme.AppDimens

@Composable
fun HistoricalCouponsTab(historicalCoupons: List<UserCouponVo>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        if (historicalCoupons.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Info,
                    message = stringResource(R.string.no_historical_coupons)
                )
            }
        } else {
            items(historicalCoupons, key = { it.userCouponId }) { coupon ->
                UserCouponCard(couponVo = coupon, isHistorical = true)
            }
        }
    }
}
