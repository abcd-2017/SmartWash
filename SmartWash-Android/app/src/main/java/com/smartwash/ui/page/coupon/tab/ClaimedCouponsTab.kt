package com.smartwash.ui.page.coupon.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.smartwash.R
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.ui.page.coupon.UserCouponCard
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens

@Composable
fun ClaimedCouponsTab(userCouponList: LazyPagingItems<UserCouponVo>, couponState: String) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        if (userCouponList.itemCount == 0) {
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
            items(userCouponList.itemCount) { i ->
                userCouponList[i]?.let { UserCouponCard(it, couponState) }
            }
        }
    }
}
