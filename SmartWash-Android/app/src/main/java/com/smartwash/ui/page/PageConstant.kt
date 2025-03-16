package com.smartwash.ui.page

sealed class PageConstant(val text: String, val description: String) {
    data object Register : PageConstant("Register", "注册页面")
    data object Login : PageConstant("Login", "登录页面")
    data object Home : PageConstant("Home", "主页")
    data object UserInfo : PageConstant("UserInfo", "用户主页")
}