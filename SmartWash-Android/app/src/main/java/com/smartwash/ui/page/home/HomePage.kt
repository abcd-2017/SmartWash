package com.smartwash.ui.page.home

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        bottomBar = {
            BottomBar(homePageNavController)
        }
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
                IndexPage(
                    homePageNavController,
                    navController
                )
            }
            composable(HomePageConstant.Service.text) { ServicePage() }
            composable(HomePageConstant.UserInfo.text) {
                UserInfoPage(
                    navController,
                    homePageNavController
                )
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

    NavigationBar(modifier = Modifier.height(90.dp)) {
        bottomNavItems.forEach { item ->
            val isSelect = currentRoute == item.text
            NavigationBarItem(
                icon = {
                    Box(
                        modifier = Modifier.animateContentSize(
                            animationSpec = tween(300)
                        )
                    ) {
                        Icon(
                            if (isSelect) item.selectIcon else item.icon,
                            contentDescription = null, tint = animateColorAsState(
                                targetValue = if (isSelect)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                animationSpec = tween(300)
                            ).value
                        )
                    }
                },
                label = {
                    Text(
                        text = item.description,
                        fontWeight = if (isSelect) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = isSelect,
                onClick = {
                    if (!isSelect) {
                        navController.navigate(item.text) {
                            // 避免重复堆栈
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

@Composable
fun ServiceCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}