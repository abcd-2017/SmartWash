package com.smartwash.ui.page.home

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.motionSpec
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartwash.feature.divination.ui.page.home.DivHomePage
import com.smartwash.ui.page.HomePageConstant
import com.smartwash.ui.page.index.IndexPage
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.laundry.ui.service.ServicePage
import com.smartwash.feature.user.impl.ui.userinfo.UserInfoPage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable

@Composable
fun HomePage(
    navController: NavHostController,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val hasUserSchool by homeViewModel.hasUserSchool.collectAsState()
    val getSchoolState by homeViewModel.getSchoolState.collectAsState()
    val homePageNavController = rememberNavController()

    LaunchedEffect(Unit) {
        homeViewModel.getUserSchool()
    }
    LaunchedEffect(getSchoolState) {
        when (getSchoolState) {
            is RequestState.Success -> {
                if (hasUserSchool == -1L) {
                    navController.popBackStack()
                    navController.navigate(UserRoute.UpdateUserInfo.text)
                }
            }
            else -> {}
        }
    }
    Scaffold(
        containerColor = AppColors.colorScheme.background,
        bottomBar = { BottomBar(homePageNavController) }
    ) { paddingValues ->
        NavHost(
            navController = homePageNavController,
            startDestination = HomePageConstant.Index.text,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                fadeIn(animationSpec = defaultSpring())
            },
            exitTransition = {
                fadeOut(animationSpec = defaultSpring())
            }
        ) {
            composable(HomePageConstant.Index.text) {
                IndexPage(homePageNavController, navController)
            }
            composable(HomePageConstant.Service.text) {
                ServicePage(navController = navController)
            }
            composable(HomePageConstant.Divination.text) {
                DivHomePage(navController)
            }
            composable(HomePageConstant.UserInfo.text) {
                UserInfoPage(navController, homePageNavController)
            }
        }
    }
}

@Composable
fun BottomBar(navController: NavHostController) {
    val bottomNavItems = listOf(
        HomePageConstant.Index,
        HomePageConstant.Service,
        HomePageConstant.Divination,
        HomePageConstant.UserInfo
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val view = currentView()

    // 底部栏 — 规范 §4.5：68dp 高、0.5dp 顶部分隔线、纯白底（D-I10）
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.colorScheme.surface)
    ) {
        // 顶部分隔线 — 设计 nav3 border-top .5px line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(AppDimens.bottomBarHeight),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.text
                BottomNavItem(
                    iconRes = item.iconRes,
                    label = item.description,
                    isSelected = isSelected,
                    onClick = {
                        view.performHaptic(HapticEffect.SELECTION)
                        if (!isSelected) {
                            navController.navigate(item.text) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current

    // 规范 §4.5：选中 scale 1.06，未选中 1.0；reduced motion 时取消缩放
    val scale = if (reduceMotion) 1f else if (isSelected) 1.06f else 1f

    Column(
        modifier = Modifier
            .pressable(onClick = onClick, debounce = false)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 规范 §4.5：26dp 图标，Canvas 绘制带动画
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            colorFilter = ColorFilter.tint(
                if (isSelected) AppColors.colorScheme.primaryDark
                else AppColors.colorScheme.textSecondary
            ),
            modifier = Modifier
                .size(26.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AppColors.colorScheme.primaryDark
                    else AppColors.colorScheme.textSecondary
        )
    }
}


