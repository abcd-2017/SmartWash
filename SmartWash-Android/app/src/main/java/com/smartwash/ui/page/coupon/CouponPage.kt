package com.smartwash.ui.page.coupon

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.R
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.ui.common.AppTabBar
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.coupon.tab.AvailableCouponsTab
import com.smartwash.ui.page.coupon.tab.ClaimedCouponsTab
import com.smartwash.ui.page.coupon.tab.HistoricalCouponsTab
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.RequestState
import com.smartwash.utils.UserCouponStatus

@Composable
fun CouponPage(
    navController: NavHostController,
    couponViewModel: CouponViewModel = hiltViewModel(),
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.available_coupons), stringResource(R.string.claimed_coupons), stringResource(R.string.historical_coupons))
    val getCouponListState by couponViewModel.getCouponListState.collectAsState()
    val couponList by couponViewModel.couponList.collectAsState()
    val context = LocalContext.current
    val receiveCouponState by couponViewModel.receiveCouponState.collectAsState()
    val userCouponList = couponViewModel.userCouponPagingFlow.collectAsLazyPagingItems()
    val couponState by couponViewModel.couponState.collectAsState()

    LaunchedEffect(Unit) {
        couponViewModel.getCouponList()
        couponViewModel.updateCouponState("0")
    }

    when (receiveCouponState) {
        is RequestState.Success -> {
            LaunchedEffect(receiveCouponState) {
                couponViewModel.getCouponList()
                Toast.makeText(context, context.getString(R.string.claim_success), Toast.LENGTH_SHORT).show()
                couponViewModel.resetReceiveState()
            }
        }
        is RequestState.Error -> {
            Toast.makeText(context, (receiveCouponState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
            couponViewModel.resetReceiveState()
        }
        else -> {}
    }

    when (getCouponListState) {
        is RequestState.Error -> {
            Toast.makeText(context, (getCouponListState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.coupons), onBack = { navController.navigateUp() })

            AppTabBar(
                tabs = tabs,
                selectedIndex = selectedTabIndex,
                onTabSelected = { index ->
                    selectedTabIndex = index
                    if (index > 0) couponViewModel.updateCouponState("${index - 1}")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> AvailableCouponsTab(couponList) {
                    userCouponList.refresh()
                    couponViewModel.receiveCoupon(it)
                }
                1 -> ClaimedCouponsTab(userCouponList, couponState)
                2 -> HistoricalCouponsTab(userCouponList, couponState)
            }
        }
    }
}

@Composable
fun UserCouponCard(
    coupon: UserCouponVo,
    couponState: String,
    modifier: Modifier = Modifier,
) {
    val isExpiredOrUsed = coupon.isUsed || couponState == UserCouponStatus.OVERDUE.status

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isExpiredOrUsed) 0.6f else 1f),
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
            // 左侧金额区
            Column(
                modifier = Modifier.width(80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.currency_format, String.format("%.0f", coupon.couponVo.discount)),
                    style = MaterialTheme.typography.headlineSmall,
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

            // 右侧信息区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = coupon.couponVo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.valid_until, "${coupon.expiredAt}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (coupon.couponVo.threshold == 0f) stringResource(R.string.no_threshold) else stringResource(R.string.coupon_min_amount_format, "${coupon.couponVo.threshold}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }

            // 右侧状态
            val statusText = when {
                coupon.isUsed -> stringResource(R.string.used)
                couponState == UserCouponStatus.OVERDUE.status -> stringResource(R.string.expired)
                else -> stringResource(R.string.available)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isExpiredOrUsed) AppColors.colorScheme.textSecondary else AppColors.colorScheme.primary
            )
        }
    }
}
