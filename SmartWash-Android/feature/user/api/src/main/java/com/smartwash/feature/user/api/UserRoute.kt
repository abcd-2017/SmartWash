package com.smartwash.feature.user.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户域类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class UserRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"Login"/"Register"/...），保证宿主 NavHost 注册与模块内跳转行为等价。
 * feature 不得反向依赖 app 壳层的 PageConstant。
 *
 * 归属说明：用户域 = 登录/注册 + 用户中心（含设置页——登出入口在 SettingPage，
 * 经 SessionManager.clearToken 登出）；充值/订单/支付等路由归各自 feature 的 api 模块。
 */
sealed interface UserRoute : SmartWashRoute {

    /** 登录页 */
    @Serializable
    @SerialName("Login")
    data object Login : UserRoute

    /** 注册页 */
    @Serializable
    @SerialName("Register")
    data object Register : UserRoute

    /** 用户中心主页 tab（HomePageConstant.UserInfo） */
    @Serializable
    @SerialName("UserInfo")
    data object UserInfo : UserRoute

    /** 用户学校信息修改页 */
    @Serializable
    @SerialName("UpdateUserInfoPage")
    data object UpdateUserInfo : UserRoute

    /** 设置页（含登出入口） */
    @Serializable
    @SerialName("Setting")
    data object Setting : UserRoute

    /** 权限管理页（设置页跳入：查看/申请应用权限、跳系统设置对应页） */
    @Serializable
    @SerialName("PermissionManagement")
    data object PermissionManagement : UserRoute
}
