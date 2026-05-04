package com.smartwash.ui.page.home

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.smartwash.ui.theme.GlassBorder
import com.smartwash.ui.theme.GlassTextDisabled
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.smartwash.ui.theme.Background
import com.smartwash.ui.theme.Divider
import com.smartwash.ui.theme.Primary
import com.smartwash.ui.theme.PrimaryLight
import com.smartwash.ui.theme.TextSecondary
import com.smartwash.ui.theme.TextTertiary
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
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
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

    Column {
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.colorScheme.surface)
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
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.textTertiary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.textSecondary
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

@Composable
fun ServiceCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Primary,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassBorder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = GlassTextDisabled
            )
        }
    }
}

@Composable
fun ServiceCardOutlined(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.colorScheme.primaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = AppColors.colorScheme.primary)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
        }
    }
}

@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    content: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.smartwash.ui.theme.IconBox(icon = icon, size = 36.dp, iconSize = 18.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
