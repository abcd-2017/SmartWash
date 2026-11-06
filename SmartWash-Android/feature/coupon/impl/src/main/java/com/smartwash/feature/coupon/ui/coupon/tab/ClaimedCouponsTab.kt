package com.smartwash.feature.coupon.ui.coupon.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smartwash.feature.coupon.R
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.feature.coupon.repository.CouponSource
import com.smartwash.feature.coupon.ui.coupon.CouponOfflineHint
import com.smartwash.feature.coupon.ui.coupon.CouponSectionError
import com.smartwash.feature.coupon.ui.coupon.UserCouponCard
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.theme.AppDimens

/**
 * 已领取 tab（回归 2026-11-06 A5）：数据来源标记传入——
 * FAILED → 错误/重试小态（不再伪装「暂无」空态）；CACHE → 列表顶部轻量离线提示；NETWORK → 现状。
 */
@Composable
fun ClaimedCouponsTab(
    claimedCoupons: List<UserCouponVo>,
    source: CouponSource = CouponSource.NETWORK,
    onRetry: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
    ) {
        when {
            // 失败且无缓存可降级：错误小态 + 重试（与「暂无优惠券」空态严格区分）
            source == CouponSource.FAILED && claimedCoupons.isEmpty() -> {
                item(key = "claimed_section_error") {
                    CouponSectionError(onRetry = onRetry)
                }
            }
            claimedCoupons.isEmpty() -> {
                item {
                    EmptyState(
                        icon = Icons.Default.Info,
                        message = stringResource(R.string.no_claimed_coupons)
                    )
                }
            }
            else -> {
                if (source == CouponSource.CACHE) {
                    item(key = "claimed_offline_hint") {
                        CouponOfflineHint()
                    }
                }
                items(claimedCoupons, key = { it.userCouponId }) { coupon ->
                    UserCouponCard(couponVo = coupon, isHistorical = false)
                }
            }
        }
    }
}
