package com.smartwash.feature.lab.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.smartwash.feature.lab.LabRoute
import com.smartwash.feature.lab.ui.breath.BreathPage
import com.smartwash.feature.lab.ui.bubble.BubbleWrapPage
import com.smartwash.feature.lab.ui.drum.DrumPage
import com.smartwash.feature.lab.ui.fish.FishPage
import com.smartwash.feature.lab.ui.fluid.FluidPage
import com.smartwash.feature.lab.ui.home.LabHomePage
import com.smartwash.feature.lab.ui.painting.PaintingPage
import com.smartwash.feature.lab.ui.sand.SandPage
import com.smartwash.feature.lab.ui.soundscape.SoundscapePage

/**
 * 解压实验舱路由聚合（模式同 gameGraph）：四个页面注册自 app 壳
 * MainActivity NavHost，宿主侧一行 [labGraph] 完成聚合。路由常量取模块内
 * [LabRoute]（单一事实来源）。无底栏入口：进入路径为深链
 * smartwash://lab（本模块 di/LabDeepLinkModule 贡献）与后端 workbench_entry
 * 配置的工作台卡片（target 指向同一深链）。
 */
fun NavGraphBuilder.labGraph(navController: NavHostController) {
    composable(LabRoute.Home.text) {
        LabHomePage(navController)
    }
    composable(LabRoute.BubbleWrap.text) {
        BubbleWrapPage(navController)
    }
    composable(LabRoute.Breath.text) {
        BreathPage(navController)
    }
    composable(LabRoute.Fish.text) {
        FishPage(navController)
    }
    composable(LabRoute.Sand.text) {
        SandPage(navController)
    }
    composable(LabRoute.Fluid.text) {
        FluidPage(navController)
    }
    composable(LabRoute.Drum.text) {
        DrumPage(navController)
    }
    composable(LabRoute.Painting.text) {
        PaintingPage(navController)
    }
    composable(LabRoute.Soundscape.text) {
        SoundscapePage(navController)
    }
}
