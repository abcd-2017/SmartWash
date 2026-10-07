package com.smartwash.ui.page.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.core.graphics.PathParser
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
                    solidPath = item.solidPath,
                    outlinePath = item.outlinePath,
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
    solidPath: String,
    outlinePath: String,
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
        AnimatedBottomNavIcon(
            solidPath = solidPath,
            outlinePath = outlinePath,
            isSelected = isSelected,
            reduceMotion = reduceMotion,
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



/**
 * 规范 §4.5 底栏图标选中动效（600ms）：0ms 外轮廓勾边 → 140ms 灰实心淡出 →
 * 170ms 品牌色淡入 → 400ms 描边退场 → 600ms 终态。灰实心与品牌填充共用同一条
 * 带镂空路径（evenOdd），交叉淡变期间镂空始终是底色；勾边只沿外轮廓。
 * 路径解析用官方 androidx.core.graphics.PathParser（完整 SVG 语法）。
 * reduced motion：不勾边不动画，直接终态。
 */
@Composable
private fun AnimatedBottomNavIcon(
    solidPath: String,
    outlinePath: String,
    isSelected: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val solid = remember(solidPath) {
        PathParser.createPathFromPathData(solidPath).apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
        }.asComposePath()
    }
    val outline = remember(outlinePath) {
        PathParser.createPathFromPathData(outlinePath).asComposePath()
    }
    val pathMeasure = remember(outline) { PathMeasure().apply { setPath(outline, false) } }
    val pathLength = remember(pathMeasure) { pathMeasure.length }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(isSelected, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(if (isSelected) 1f else 0f)
        } else {
            progress.animateTo(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing)
            )
        }
    }

    val brandColor = AppColors.colorScheme.primaryDark
    val grayColor = AppColors.colorScheme.textSecondary

    Canvas(modifier = modifier) {
        // 路径坐标是 24 网格（§4.5），按画布尺寸换算密度缩放，否则 density>1 时图标缩小且左上偏移
        val scaleFactor = size.minDimension / 24f
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
            val totalProgress = progress.value * 600f
            val drawOnProgress = (totalProgress / 400f).coerceIn(0f, 1f)
            val strokeAlpha = when {
                totalProgress < 400f -> 1f
                totalProgress < 600f -> 1f - (totalProgress - 400f) / 200f
                else -> 0f
            }
            val grayAlpha = when {
                totalProgress < 140f -> 1f
                totalProgress < 170f -> 1f - (totalProgress - 140f) / 30f
                else -> 0f
            }
            val brandAlpha = when {
                totalProgress < 170f -> 0f
                totalProgress < 200f -> (totalProgress - 170f) / 30f
                else -> 1f
            }
            if (grayAlpha > 0f) {
                drawPath(path = solid, color = grayColor.copy(alpha = grayAlpha), style = Fill)
            }
            if (brandAlpha > 0f) {
                drawPath(path = solid, color = brandColor.copy(alpha = brandAlpha), style = Fill)
            }
            if (drawOnProgress > 0f && strokeAlpha > 0f) {
                val segment = Path()
                pathMeasure.getSegment(0f, pathLength * drawOnProgress, segment, startWithMoveTo = true)
                drawPath(
                    path = segment,
                    color = brandColor.copy(alpha = strokeAlpha),
                    // 描边宽同为 24 网格单位，随 scale 变换缩放到物理像素
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
