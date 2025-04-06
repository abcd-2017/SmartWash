package com.smartwash.ui.page.coupon

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.utils.CouponStatus
import com.smartwash.utils.RequestState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponPage(
    navController: NavHostController,
    couponViewModel: CouponViewModel = hiltViewModel(),
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("可领取", "已领取", "历史记录")
    val getCouponListState by couponViewModel.getCouponListState.collectAsState()
    val couponList by couponViewModel.couponList.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(selectedTabIndex) {
        couponViewModel.getCouponList()
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
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        // 标签页内容
        when (selectedTabIndex) {
            0 -> AvailableCouponsTab(couponList)
            1 -> ClaimedCouponsTab()
            2 -> HistoricalCouponsTab()
        }
    }
}

@Composable
fun AvailableCouponsTab(couponList: List<CouponVo>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (couponList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无优惠券",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(couponList) { coupon ->
                CouponCard(
                    coupon = coupon,
                    isAvailable = true,
                    onClaimClick = { /* 处理领取逻辑 */ }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ClaimedCouponsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
//        // 示例已领取优惠券数据
//        val claimedCoupons = listOf(
//            CouponData(
//                title = "新人专享券",
//                amount = "5元",
//                condition = "满20元可用",
//                validPeriod = "2024.03.01-2024.03.31",
//                isNew = false
//            )
//        )
//
//        claimedCoupons.forEach { coupon ->
//            CouponCard(
//                coupon = coupon,
//                isAvailable = false,
//                onClaimClick = null
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//        }
    }
}

@Composable
fun HistoricalCouponsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 示例历史优惠券数据
//        val historicalCoupons = listOf(
//            CouponData(
//                title = "新人专享券",
//                amount = "5元",
//                condition = "满20元可用",
//                validPeriod = "2024.02.01-2024.02.29",
//                isNew = false
//            )
//        )
//
//        historicalCoupons.forEach { coupon ->
//            CouponCard(
//                coupon = coupon,
//                isAvailable = false,
//                onClaimClick = null
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//        }
    }
}

@Composable
fun CouponCard(
    coupon: CouponVo,
    isAvailable: Boolean,
    onClaimClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 优惠券金额
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${coupon.discount}元",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAvailable)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
//                    if (coupon.isNew) {
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Text(
//                            text = "新",
//                            fontSize = 12.sp,
//                            color = MaterialTheme.colorScheme.primary,
//                            modifier = Modifier
//                                .clip(RoundedCornerShape(4.dp))
//                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
//                                .padding(horizontal = 6.dp, vertical = 2.dp)
//                        )
//                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (coupon.threshold == 0f) "无门槛" else "满${coupon.threshold}元可用",
                    fontSize = 14.sp,
                    color = if (isAvailable)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右侧信息
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = coupon.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAvailable)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${coupon.startTime.split(" ")[0]}-${coupon.endTime.split(" ")[0]}",
                    fontSize = 12.sp,
                    color = if (isAvailable)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (isAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (coupon.status == CouponStatus.ACTIVE.status) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) { Text("已领取") }
                    } else {
                        Button(
                            onClick = { onClaimClick?.invoke() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("立即领取")
                        }
                    }
                }
            }
        }
    }
}