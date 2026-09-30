package com.smartwash.feature.user.impl.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.ui.login.LoginPage
import com.smartwash.feature.user.impl.ui.register.RegisterPage
import com.smartwash.feature.user.impl.ui.setting.SettingPage
import com.smartwash.feature.user.impl.ui.update_userinfo.UpdateUserInfoPage

/**
 * 用户域路由聚合（T8.1）：本域 4 页面的 composable 注册自 app 壳 MainActivity
 * NavHost 迁入，宿主侧一行 [userGraph] 完成聚合。路由常量取 user-api 的
 * [UserRoute]（单一事实来源）。
 *
 * @param checkUpdateContent 设置页「检查更新」行插槽（feature:update 归壳层聚合，
 *   user-impl 不依赖它；壳层经参数注入，T5.2 插槽模式保持不变）
 */
fun NavGraphBuilder.userGraph(
    navController: NavHostController,
    checkUpdateContent: (@Composable () -> Unit)? = null,
) {
    composable(UserRoute.Login.text) {
        LoginPage(navController)
    }
    composable(UserRoute.Register.text) {
        RegisterPage(navController)
    }
    composable(UserRoute.UpdateUserInfo.text) {
        UpdateUserInfoPage(navController)
    }
    composable(UserRoute.Setting.text) {
        SettingPage(navController, checkUpdateContent)
    }
}
