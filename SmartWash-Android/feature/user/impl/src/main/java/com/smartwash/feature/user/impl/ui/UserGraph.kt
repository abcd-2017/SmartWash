package com.smartwash.feature.user.impl.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.impl.ui.changepassword.ChangePasswordPage
import com.smartwash.feature.user.impl.ui.permission.PermissionManagementPage
import com.smartwash.feature.user.impl.ui.resetpassword.ResetPasswordPage
import com.smartwash.feature.user.impl.ui.setting.DietaryPreferencePage

/**
 * 用户域路由聚合（T8.1）：本域页面的 composable 注册自 app 壳 MainActivity
 * NavHost 迁入，宿主侧一行 [userGraph] 完成聚合。路由常量取 user-api 的
 * [UserRoute]（单一事实来源）。
 *
 * **登录 / 注册 / 设置三路由不在此注册**（变体路由归属，见函数内注释）：full 由
 * 本模块 src/full 的 UserFlavorGraphModule 注册，lite 由 app/src/lite 的 liteGraph 注册。
 *
 * reduceMotion 在转场 lambda 内读取 [NavGraphAmbient.reduceMotion] 快照（NavHost builder
 * 非组合上下文，无法读 [com.smartwash.common.utils.LocalReduceMotion]；快照由壳层组合期写入，
 * 与全局转场同源）。设置页「检查更新」插槽由 [SettingPage] 经 [LocalCheckUpdateContent]
 * 自行读取（壳层在 NavHost 外注入），图侧不再透传。
 */
fun NavGraphBuilder.userGraph(navController: NavHostController) {
    // 登录 / 注册 / 设置三路由**不在本图注册**：它们是两变体共用的路由常量，但页面实现
    // 分属 full（本模块 src/full）与 lite（app/src/lite）——同路由重复注册在 Navigation 2.8
    // 是「后注册覆盖」，胜出者取决于 Hilt @IntoSet 聚合顺序（未保证）。故这三条路由的注册
    // 随页面实现走：full 由本模块 src/full 的 UserFlavorGraphModule 注册（含认证转场），
    // lite 由 app/src/lite 的 liteGraph 注册。
    //
    // 本图只注册**两变体共用实现**的页面：权限管理 / 修改密码 / 重置密码（无 lite 副本，
    // 且不依赖 full 专属域）。资料编辑 / 解读模型 / 钱包流水 / 我的页虽在 user-impl 包内，
    // 但实现已落在本模块 src/full 源集（依赖 laundry-api / notice-api / payment-api 等
    // full 专属契约），其路由注册同在 src/full 的 UserFlavorGraphModule。
    composable<UserRoute.PermissionManagement> {
        PermissionManagementPage(navController)
    }
    // 饮食偏好页（两变体共用实现，契约 §三.2；入口在设置页）
    composable<UserRoute.DietaryPreference> {
        DietaryPreferencePage(navController)
    }
    // 以下两页走 NavHost 全局默认转场（与 Setting/PermissionManagement 同档）
    composable<UserRoute.ChangePassword> {
        ChangePasswordPage(navController)
    }
    composable<UserRoute.ResetPassword> {
        ResetPasswordPage(navController)
    }
}
