package com.smartwash.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class PageConstant(val text: String, val description: String) {
    data object Register : PageConstant("Register", "注册页面")
    data object Login : PageConstant("Login", "登录页面")
    data object Home : PageConstant("Home", "主页")
    data object UpdateUserInfoPage : PageConstant("UpdateUserInfoPage", "用户学校信息修改")
    data object Setting : PageConstant("Setting", "设置页面")
    data object Recharge : PageConstant("Recharge", "充值页面")
    data object Order : PageConstant("Order", "订单页面")
    data object OrderDetail : PageConstant("OrderDetail", "订单详情页面")
    data object Service : PageConstant("Service", "服务页面")
    data object Payment : PageConstant("Payment", "支付页面")
    data object Laundry : PageConstant("Laundry", "洗衣预约页面")
    data object PaySuccess : PageConstant("PaySuccess", "支付成功页面")
    data object PickupDelivery : PageConstant("PickupDelivery", "寄件取件页面")
    data object Coupon : PageConstant("Coupon", "优惠券页面")
    data object Pickup : PageConstant("Pickup", "取件页面")
}

sealed class HomePageConstant(
    val text: String,
    val description: String,
    val icon: ImageVector,
    val selectIcon: ImageVector,
) {
    data object Index : HomePageConstant("Index", "首页", Icons.Outlined.Home, Icons.Filled.Home)
    data object Service :
        HomePageConstant(
            "Service",
            "服务",
            Icons.AutoMirrored.Outlined.List,
            Icons.AutoMirrored.Filled.List
        )

//    data object Locker :
//        HomePageConstant("Locker", "寄存柜", Icons.Outlined.Receipt, Icons.Filled.Receipt)

    data object UserInfo :
        HomePageConstant("UserInfo", "主页", Icons.Outlined.Person, Icons.Filled.Person)
}