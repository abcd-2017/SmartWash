package com.smartwash.feature.dining.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.dining.ui.create.DiningRecipeCreatePage
import com.smartwash.feature.dining.ui.detail.DiningRecipeDetailPage
import com.smartwash.feature.dining.ui.join.DiningOrderJoinPage
import com.smartwash.feature.dining.ui.library.DiningRecipeLibraryPage
import com.smartwash.feature.dining.ui.order.DiningOrderCreatePage
import com.smartwash.feature.dining.ui.order.DiningOrderDetailPage
import com.smartwash.feature.dining.ui.order.DiningOrderListPage
import com.smartwash.feature.dining.ui.order.DiningOrderSharePage
import com.smartwash.feature.dining.ui.order.DiningOrderShoppingListPage
import com.smartwash.feature.dining.ui.scan.DiningScanPage

/**
 * 家庭点单域路由聚合（模式同 noticeGraph/divinationGraph）：composable 注册自 app 壳
 * MainActivity NavHost 迁入，宿主侧一行 [diningGraph] 完成聚合。
 * 类型安全路由取 dining-api 的 [DiningRoute]（单一事实来源）。
 * 十页清单：订单六页（07 号票发起链路：订单列表[域首页，深链落点]/创建/分享；
 * 09 号票点单页 OrderDetail；11 号票购买清单结果页 ShoppingList；12 号票站内信深链
 * 加入入口 OrderJoin）+ 菜谱三页
 * （04 号票：菜谱库/详情/新增，入口在订单列表页右上角）。
 */
fun NavGraphBuilder.diningGraph(navController: NavHostController) {
    composable<DiningRoute.OrderList> { DiningOrderListPage(navController) }
    composable<DiningRoute.OrderCreate> { DiningOrderCreatePage(navController) }
    composable<DiningRoute.Scan> { DiningScanPage(navController) }
    composable<DiningRoute.OrderJoin> { DiningOrderJoinPage(navController) }
    composable<DiningRoute.OrderDetail> { DiningOrderDetailPage(navController) }
    composable<DiningRoute.OrderShare> { DiningOrderSharePage(navController) }
    composable<DiningRoute.ShoppingList> { DiningOrderShoppingListPage(navController) }
    composable<DiningRoute.RecipeLibrary> { DiningRecipeLibraryPage(navController) }
    composable<DiningRoute.RecipeDetail> { entry ->
        DiningRecipeDetailPage(navController, entry.toRoute<DiningRoute.RecipeDetail>().recipeId)
    }
    composable<DiningRoute.RecipeCreate> { DiningRecipeCreatePage(navController) }
}
