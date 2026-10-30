package com.smartwash.feature.subscription.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartwash.feature.subscription.SubscriptionRoute
import com.smartwash.feature.subscription.ui.detail.SubscriptionDetailPage
import com.smartwash.feature.subscription.ui.detail.SubscriptionDetailViewModel
import com.smartwash.feature.subscription.ui.edit.SubscriptionEditPage
import com.smartwash.feature.subscription.ui.edit.SubscriptionEditViewModel
import com.smartwash.feature.subscription.ui.list.SubscriptionListPage
import com.smartwash.feature.subscription.ui.reminder.SubscriptionReminderPage

/**
 * 订阅域路由聚合（模式同 toolboxGraph/divinationGraph）：4 页面的 composable 可整段
 * 注册自 app 壳 MainActivity NavHost，宿主侧一行 [subscriptionGraph] 完成聚合。
 * 路由常量取模块内 [SubscriptionRoute]（单一事实来源）。当前无宿主入口，
 * 接入方式：MainActivity NavHost 内追加一行 subscriptionGraph(navController)，
 * 再由任意宿主页面 navigate(SubscriptionRoute.List.text) 进入。
 */
fun NavGraphBuilder.subscriptionGraph(navController: NavHostController) {
    composable(SubscriptionRoute.List.text) {
        SubscriptionListPage(navController)
    }
    // id 经 query 携带（缺省 -1 = 新建），与详情/表单的 SavedStateHandle 读取对齐
    composable(
        route = "${SubscriptionRoute.Edit.text}?${SubscriptionEditViewModel.ARG_ID}={${SubscriptionEditViewModel.ARG_ID}}",
        arguments = listOf(
            navArgument(SubscriptionEditViewModel.ARG_ID) {
                type = NavType.LongType
                defaultValue = -1L
            },
        )
    ) {
        SubscriptionEditPage(navController)
    }
    composable(
        route = "${SubscriptionRoute.Detail.text}?${SubscriptionDetailViewModel.ARG_ID}={${SubscriptionDetailViewModel.ARG_ID}}",
        arguments = listOf(
            navArgument(SubscriptionDetailViewModel.ARG_ID) {
                type = NavType.LongType
                defaultValue = -1L
            },
        )
    ) {
        SubscriptionDetailPage(navController)
    }
    composable(SubscriptionRoute.Reminders.text) {
        SubscriptionReminderPage(navController)
    }
}

/** 列表 → 详情（详情数据实时拉取，仅携带 id） */
fun NavHostController.navigateToSubscriptionDetail(subscriptionId: Long) {
    navigate("${SubscriptionRoute.Detail.text}?${SubscriptionDetailViewModel.ARG_ID}=$subscriptionId")
}

/** 列表/详情 → 表单（id ≤ 0 = 新建） */
fun NavHostController.navigateToSubscriptionEdit(subscriptionId: Long) {
    navigate("${SubscriptionRoute.Edit.text}?${SubscriptionEditViewModel.ARG_ID}=$subscriptionId")
}

/** 列表 → 提醒 */
fun NavHostController.navigateToSubscriptionReminders() {
    navigate(SubscriptionRoute.Reminders.text)
}

/**
 * 变更成功后置位刷新信号（存于发起页上一栈位的 savedStateHandle）：
 * 列表/详情/提醒页消费后复位并重载（红点/状态/分页流）。
 */
fun NavHostController.notifySubscriptionChanged() {
    previousBackStackEntry?.savedStateHandle?.set(SubscriptionRoute.CHANGED_KEY, true)
}
