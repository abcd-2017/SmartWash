package com.smartwash.feature.user.api

/**
 * 用户域路由常量（模块内单一事实来源，模式仿 :feature:divination 的 DivRoute）。
 *
 * 路由值对齐 app 壳层 PageConstant / HomePageConstant 的现有常量，保证宿主 NavHost
 * 注册与模块内跳转永不漂移；feature 不得反向依赖 app 壳层的 PageConstant。
 * T5.3 消费方切换后，app 的 PageConstant.Login 等用户域常量委托本常量的值
 * （同 PageConstant.Div* 委托 DivRoute 的模式）；T8.1 统一拆路由后 app 侧委托常量移除。
 *
 * 归属说明：用户域 = 登录/注册 + 用户中心（含设置页——登出入口在 SettingPage，
 * 经 SessionManager.clearToken 登出）；充值/订单/支付等路由归各自 feature 的 api 模块。
 */
sealed class UserRoute(val text: String) {
    /** 登录页（PageConstant.Login） */
    data object Login : UserRoute("Login")

    /** 注册页（PageConstant.Register） */
    data object Register : UserRoute("Register")

    /** 用户中心主页 tab（HomePageConstant.UserInfo） */
    data object UserInfo : UserRoute("UserInfo")

    /** 用户学校信息修改页（PageConstant.UpdateUserInfoPage） */
    data object UpdateUserInfo : UserRoute("UpdateUserInfoPage")

    /** 设置页（PageConstant.Setting，含登出入口） */
    data object Setting : UserRoute("Setting")
}
