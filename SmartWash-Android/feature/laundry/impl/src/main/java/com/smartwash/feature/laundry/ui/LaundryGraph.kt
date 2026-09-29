package com.smartwash.feature.laundry.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.laundry.api.LaundryRoute
import com.smartwash.feature.laundry.ui.laundry.LaundryPage
import com.smartwash.feature.laundry.ui.service.ServicePage

/**
 * 洗衣域路由聚合（T8.1）：服务 tab 页 / 洗衣预约页 composable 注册自 app 壳
 * MainActivity NavHost 迁入，宿主侧一行 [laundryGraph] 完成聚合。路由常量取
 * laundry-api 的 [LaundryRoute]（单一事实来源；Service 同为主页底部 tab 路由，
 * 壳层 HomePageConstant.Service 委托其值）。
 */
fun NavGraphBuilder.laundryGraph(navController: NavHostController) {
    composable(LaundryRoute.Service.text) { ServicePage() }
    composable(LaundryRoute.Laundry.text) { LaundryPage(navController) }
}
