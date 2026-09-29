package com.smartwash.feature.coupon.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.coupon.api.CouponRoute
import com.smartwash.feature.coupon.ui.coupon.CouponPage

/**
 * 优惠券域路由聚合（T8.1）：优惠券页 composable 注册自 app 壳 MainActivity
 * NavHost 迁入，宿主侧一行 [couponGraph] 完成聚合。路由常量取 coupon-api 的
 * [CouponRoute]（单一事实来源）。
 */
fun NavGraphBuilder.couponGraph(navController: NavHostController) {
    composable(CouponRoute.Coupon.text) { CouponPage(navController) }
}
