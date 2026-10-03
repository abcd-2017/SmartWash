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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
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
import com.smartwash.common.model.RequestState

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
            composable(HomePageConstant.Service.text) { ServicePage() }
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

    // 底部栏 — 规范 §4.5：68dp 高、无顶部分隔线、纯白底
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
                iconPath = item.iconPath,
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

@Composable
private fun BottomNavItem(
    iconPath: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current

    // 规范 §4.5：选中 scale 1.06，未选中 1.0；reduced motion 时取消缩放
    val scale = if (reduceMotion) 1f else if (isSelected) 1.06f else 1f

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 规范 §4.5：26dp 图标，Canvas 绘制带动画
        AnimatedBottomNavIcon(
            iconPath = iconPath,
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

@Composable
private fun AnimatedBottomNavIcon(
    iconPath: String,
    isSelected: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val path = remember(iconPath) { parseSvgPath(iconPath) }
    val pathMeasure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    // Note: PathMeasure.setPath(path, forceClose) - path is the Path to measure
    val pathLength = remember(pathMeasure) { pathMeasure.length }

    // 动画进度：0 = 未选中，1 = 选中
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isSelected, reduceMotion) {
        if (reduceMotion) {
            // reduced motion：直接跳到终态，不做任何动画
            progress.snapTo(if (isSelected) 1f else 0f)
        } else {
            // 规范 §4.5 时序：
            // t = 0ms      外轮廓开始勾边
            // t = 140ms    灰色实心开始淡出
            // t = 170ms    品牌色开始淡入
            // t = 400ms    描边开始退场
            // t = 600ms    终态：纯实心 + 镂空
            val target = if (isSelected) 1f else 0f
            progress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = 600,
                    easing = LinearEasing
                )
            )
        }
    }

    val density = LocalDensity.current
    val strokeWidth = with(density) { 1.5.dp.toPx() }

    // 提前解析颜色（AppColors.colorScheme 是 @Composable，不能在 Canvas 中使用）
    val primaryDarkColor = AppColors.colorScheme.primaryDark
    val grayColor = Color(0xFF9A9DA3)

    Canvas(modifier = modifier) {
        // 计算各阶段进度
        val totalProgress = progress.value * 600f

        // 勾边进度：0-400ms
        val drawOnProgress = (totalProgress / 400f).coerceIn(0f, 1f)

        // 描边退场：400-600ms
        val strokeAlpha = when {
            totalProgress < 400f -> 1f
            totalProgress < 600f -> 1f - (totalProgress - 400f) / 200f
            else -> 0f
        }

        // 灰色淡出：140-170ms
        val grayAlpha = when {
            totalProgress < 140f -> 1f
            totalProgress < 170f -> 1f - (totalProgress - 140f) / 30f
            else -> 0f
        }

        // 品牌色淡入：170-200ms
        val brandAlpha = when {
            totalProgress < 170f -> 0f
            totalProgress < 200f -> (totalProgress - 170f) / 30f
            else -> 1f
        }

        // 1. 灰色实心（带镂空）- 淡出
        if (grayAlpha > 0f) {
            drawPath(
                path = path,
                color = grayColor.copy(alpha = 0.48f * grayAlpha),
                style = Fill
            )
        }

        // 2. 品牌色实心（带镂空）- 淡入
        if (brandAlpha > 0f) {
            drawPath(
                path = path,
                color = primaryDarkColor.copy(alpha = brandAlpha),
                style = Fill
            )
        }

        // 3. 外轮廓描边 - 勾边 + 退场
        if (drawOnProgress > 0f && strokeAlpha > 0f) {
            val segment = Path()
            pathMeasure.getSegment(
                startDistance = 0f,
                stopDistance = pathLength * drawOnProgress,
                destination = segment
            )
            drawPath(
                path = segment,
                color = primaryDarkColor.copy(alpha = strokeAlpha),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

// 简化的 SVG 路径解析（仅支持 M/L/H/V/C/A/Z 命令）
private fun parseSvgPath(pathData: String): Path {
    val path = Path()
    val tokens = pathData.split("(?=[MLHVCSQTAZ])".toRegex()).filter { it.isNotBlank() }

    var currentX = 0f
    var currentY = 0f
    var startX = 0f
    var startY = 0f

    for (token in tokens) {
        val command = token[0]
        val args = token.substring(1).trim().split("[,\\s]+".toRegex()).mapNotNull { it.toFloatOrNull() }

        when (command) {
            'M' -> {
                if (args.size >= 2) {
                    currentX = args[0]
                    currentY = args[1]
                    startX = currentX
                    startY = currentY
                    path.moveTo(currentX, currentY)
                }
            }
            'L' -> {
                if (args.size >= 2) {
                    currentX = args[0]
                    currentY = args[1]
                    path.lineTo(currentX, currentY)
                }
            }
            'H' -> {
                if (args.isNotEmpty()) {
                    currentX = args[0]
                    path.lineTo(currentX, currentY)
                }
            }
            'V' -> {
                if (args.isNotEmpty()) {
                    currentY = args[0]
                    path.lineTo(currentX, currentY)
                }
            }
            'C' -> {
                if (args.size >= 6) {
                    path.cubicTo(
                        args[0], args[1],
                        args[2], args[3],
                        args[4], args[5]
                    )
                    currentX = args[4]
                    currentY = args[5]
                }
            }
            'A' -> {
                if (args.size >= 7) {
                    // 简化为直线（圆弧用直线近似）
                    currentX = args[5]
                    currentY = args[6]
                    path.lineTo(currentX, currentY)
                }
            }
            'Z' -> {
                path.close()
                currentX = startX
                currentY = startY
            }
        }
    }
    return path
}
