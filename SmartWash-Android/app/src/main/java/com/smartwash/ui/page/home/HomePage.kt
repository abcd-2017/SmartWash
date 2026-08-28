package com.smartwash.ui.page.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.smartwash.utils.HapticEffect
import com.smartwash.utils.currentView
import com.smartwash.utils.defaultSpring
import com.smartwash.utils.performHaptic
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartwash.ui.page.HomePageConstant
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.index.IndexPage
import com.smartwash.ui.page.service.ServicePage
import com.smartwash.ui.page.userinfo.UserInfoPage
import com.smartwash.ui.theme.AppColors
import com.smartwash.utils.RequestState

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
                    navController.navigate(PageConstant.UpdateUserInfoPage.text)
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
            composable(HomePageConstant.Service.text) { ServicePage() }
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
        HomePageConstant.UserInfo
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val view = currentView()

    Column {
        // 渐变分隔替代硬线 — Apple 滚动边缘效果
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColors.colorScheme.divider.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
        // 半透明底部栏 — 内容在下方滚动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.colorScheme.surface.copy(alpha = 0.85f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.text
                BottomNavItem(
                    icon = if (isSelected) item.selectIcon else item.icon,
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
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // 选中态图标尺寸动画 — 选中时略微放大
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 26.dp else 24.dp,
        label = "iconSize"
    )
    // 选中态颜色动画 — 平滑过渡
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.textTertiary,
        label = "iconColor"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.textSecondary,
        label = "labelColor"
    )

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = iconColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AppColors.colorScheme.primary)
            )
        }
    }
}

