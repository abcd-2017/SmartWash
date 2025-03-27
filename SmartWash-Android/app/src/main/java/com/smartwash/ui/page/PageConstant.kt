package com.smartwash.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.ui.graphics.vector.ImageVector

sealed class PageConstant(val text: String, val description: String) {
    data object Register : PageConstant("Register", "注册页面")
    data object Login : PageConstant("Login", "登录页面")
    data object Home : PageConstant("Home", "主页")
    data object UpdateUserInfoPage : PageConstant("UpdateUserInfoPage", "用户学校信息修改")
    data object Setting : PageConstant("Setting", "设置页面")
    data object Recharge : PageConstant("Recharge", "充值页面")
    data object Order : PageConstant("Order", "订单页面")
    data object Service : PageConstant("Service", "服务页面")
    data object Payment : PageConstant("Payment", "支付页面")
}

sealed class HomePageConstant(
    val text: String,
    val description: String,
    val icon: ImageVector,
    val selectIcon: ImageVector
) {
    data object Index : HomePageConstant("Index", "首页", Icons.Outlined.Home, Icons.Filled.Home)
    data object Laundry :
        HomePageConstant(
            "Laundry",
            "洗衣",
            Icons.Outlined.LocalLaundryService,
            Icons.Filled.LocalLaundryService
        )

    data object Locker :
        HomePageConstant("Locker", "寄存柜", Icons.Outlined.Receipt, Icons.Filled.Receipt)

    data object UserInfo :
        HomePageConstant("UserInfo", "主页", Icons.Outlined.Person, Icons.Filled.Person)
}