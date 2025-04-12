package com.smartwash.ui.page.coupon

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.network.vo.coupon.UserCouponVo
import com.smartwash.ui.page.coupon.tab.AvailableCouponsTab
import com.smartwash.ui.page.coupon.tab.ClaimedCouponsTab
import com.smartwash.ui.page.coupon.tab.HistoricalCouponsTab
import com.smartwash.utils.RequestState
import com.smartwash.utils.UserCouponStatus


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponPage(
    navController: NavHostController,
    couponViewModel: CouponViewModel = hiltViewModel(),
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("可领取", "已领取", "历史记录")
    val getCouponListState by couponViewModel.getCouponListState.collectAsState()
    val couponList by couponViewModel.couponList.collectAsState()
    val context = LocalContext.current
    val receiveCouponState by couponViewModel.receiveCouponState.collectAsState()
    //已经领取的优惠券列表
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
                Toast.makeText(context, "领取成功", Toast.LENGTH_SHORT).show()
                couponViewModel.resetReceiveState()
            }
        }

        is RequestState.Error -> {
            Toast.makeText(
                context,
                (receiveCouponState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
            couponViewModel.resetReceiveState()
        }

        else -> {}
    }

    when (getCouponListState) {
        is RequestState.Error -> {
            Toast.makeText(
                context,
                (getCouponListState as RequestState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
        }

        else -> {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 顶部栏
        TopAppBar(
            title = { Text("优惠券", fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            }
        )

        // 标签栏
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        if (index > 0) {
                            couponViewModel.updateCouponState("${index - 1}")
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        // 标签页内容
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

@Composable
fun UserCouponCard(
    coupon: UserCouponVo,
    couponState: String,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when {
        coupon.isUsed -> MaterialTheme.colorScheme.surfaceVariant
        couponState == UserCouponStatus.OVERDUE.status -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val statusText = when {
        coupon.isUsed -> "已使用"
        couponState == UserCouponStatus.OVERDUE.status -> "已过期"
        else -> "可使用"
    }

    val statusColor = when {
        coupon.isUsed || couponState == UserCouponStatus.OVERDUE.status -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${coupon.couponVo.discount}元优惠券",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (coupon.couponVo.threshold == 0f) "满${coupon.couponVo.discount + 0.01}减${coupon.couponVo.discount}" else "满${coupon.couponVo.threshold}元可用",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "有效期至 ${coupon.expiredAt}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "￥${coupon.couponVo.discount}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = statusColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }
    }
}
