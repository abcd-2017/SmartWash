package com.smartwash.lite.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.common.ui.navigation.ShellRoute
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.lite.ui.home.LiteHomePage
import com.smartwash.lite.ui.login.LiteLoginPage
import com.smartwash.lite.ui.register.LiteRegisterPage
import com.smartwash.lite.ui.setting.LiteSettingPage

/**
 * lite 变体导航图（flavor 源集贡献者注册，见 di/LiteGraphModule）。
 *
 * 注册策略（每变体路由唯一确定，无同路由重复注册）：
 * 1. lite 专属四页（首页 / 登录 / 注册 / 设置）注册到共享路由常量
 *    （[ShellRoute.Home] / [UserRoute.Login] / [UserRoute.Register] / [UserRoute.Setting]）——
 *    MainActivity 的 startDestination 恒为 [UserRoute.Login]，故登录页必须落该路由。
 * 2. **game / dining 不在此重复注册**：两域 impl 为两变体共用依赖（app/build.gradle 的
 *    `implementation`），其 `*GraphModule` 已在 lite 汇入同一 [NavGraphContributor] 集合，
 *    再委托 [com.smartwash.feature.game.ui.gameGraph] /
 *    [com.smartwash.feature.dining.ui.diningGraph] 会造成同路由重复注册。
 * 3. **userGraph 已不注册 Login/Register/Setting**：这三条路由的注册随页面实现走
 *    （full → :feature:user:impl 的 src/full 源集 UserFlavorGraphModule，lite → 本图），
 *    故 lite classpath 下 [UserRoute.Login] 只有本图一处注册，不存在「后注册覆盖」的
 *    胜出页不确定性。lite 不需要 user 域的其余页面（用户中心/钱包/密码等），本图不委托 userGraph。
 */
fun NavGraphBuilder.liteGraph(navController: NavHostController) {
    composable<ShellRoute.Home> {
        LiteHomePage(navController)
    }
    composable<UserRoute.Login> {
        LiteLoginPage(navController)
    }
    composable<UserRoute.Register> {
        LiteRegisterPage(navController)
    }
    composable<UserRoute.Setting> {
        LiteSettingPage(navController)
    }
}
