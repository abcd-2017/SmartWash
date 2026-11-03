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
import com.smartwash.feature.lab.ui.pet.PetPage
import com.smartwash.feature.lab.ui.sand.SandPage
import com.smartwash.feature.lab.ui.soundscape.SoundscapePage
import com.smartwash.feature.lab.ui.starmap.StarMapPage

/**
 * 解压实验舱路由聚合（模式同 gameGraph）：玩具页面注册自 app 壳
 * MainActivity NavHost，宿主侧一行 [labGraph] 完成聚合。类型安全路由取模块内
 * [LabRoute]（单一事实来源）。无底栏入口：进入路径为深链
 * smartwash://lab（本模块 di/LabDeepLinkModule 贡献）与后端 workbench_entry
 * 配置的工作台卡片（target 指向同一深链）。
 */
fun NavGraphBuilder.labGraph(navController: NavHostController) {
    composable<LabRoute.Home> {
        LabHomePage(navController)
    }
    composable<LabRoute.BubbleWrap> {
        BubbleWrapPage(navController)
    }
    composable<LabRoute.Breath> {
        BreathPage(navController)
    }
    composable<LabRoute.Fish> {
        FishPage(navController)
    }
    composable<LabRoute.Sand> {
        SandPage(navController)
    }
    composable<LabRoute.Fluid> {
        FluidPage(navController)
    }
    composable<LabRoute.Drum> {
        DrumPage(navController)
    }
    composable<LabRoute.Painting> {
        PaintingPage(navController)
    }
    composable<LabRoute.Soundscape> {
        SoundscapePage(navController)
    }
    composable<LabRoute.Pet> {
        PetPage(navController)
    }
    composable<LabRoute.StarMap> {
        StarMapPage(navController)
    }
}
