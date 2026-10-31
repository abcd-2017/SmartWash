package com.smartwash.ui.page.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.motionSpec
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.ui.page.HomePageConstant
import com.smartwash.ui.page.index.IndexPage
import com.smartwash.ui.page.workbench.WorkbenchPage
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.laundry.ui.service.ServicePage
import com.smartwash.feature.user.impl.ui.userinfo.UserInfoPage
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.pressable
import kotlinx.coroutines.launch

/** 底部 tab 页序（= 底栏顺序）：Pager 页码与底栏索引共用此表 */
private val homeTabItems = listOf(
    HomePageConstant.Index,
    HomePageConstant.Service,
    HomePageConstant.Workbench,
    HomePageConstant.UserInfo,
)

@Composable
fun HomePage(
    navController: NavHostController,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val hasUserSchool by homeViewModel.hasUserSchool.collectAsState()
    val getSchoolState by homeViewModel.getSchoolState.collectAsState()
    val reduceMotion = LocalReduceMotion.current
    val pagerState = rememberPagerState(pageCount = { homeTabItems.size })
    val coroutineScope = rememberCoroutineScope()
    val view = currentView()

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

    // 滑动切 tab 到位的选中触感（跳过首组合；点击路径由 BottomBar 触发）
    var lastSettledPage by remember { mutableIntStateOf(pagerState.settledPage) }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != lastSettledPage) {
            lastSettledPage = pagerState.settledPage
            view.performHaptic(HapticEffect.SELECTION)
        }
    }

    Scaffold(
        containerColor = AppColors.colorScheme.background,
        bottomBar = { BottomBar(pagerState, reduceMotion) }
    ) { paddingValues ->
        // 非首页 tab 按系统返回先回首页 tab（复刻原 tab 栈 pop 行为），首页则交还系统退出
        BackHandler(enabled = pagerState.settledPage != 0) {
            coroutineScope.launch {
                if (reduceMotion) pagerState.scrollToPage(0) else pagerState.animateScrollToPage(0)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(paddingValues),
        ) { page ->
            when (page) {
                0 -> IndexPage(isActive = pagerState.settledPage == 0, navController = navController)
                1 -> ServicePage(navController = navController)
                2 -> WorkbenchPage(navController = navController)
                else -> UserInfoPage(navController, isActive = pagerState.settledPage == 3)
            }
        }
    }
}

@Composable
fun BottomBar(pagerState: PagerState, reduceMotion: Boolean) {
    val coroutineScope = rememberCoroutineScope()
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
            homeTabItems.forEachIndexed { index, item ->
                // targetPage：滑动中途即高亮目标 tab，选中反馈跟手
                val isSelected = pagerState.targetPage == index
                BottomNavItem(
                    solidPath = item.solidPath,
                    outlinePath = item.outlinePath,
                    label = item.description,
                    isSelected = isSelected,
                    onClick = {
                        view.performHaptic(HapticEffect.SELECTION)
                        if (!isSelected) {
                            coroutineScope.launch {
                                // reduced motion（规范 §8）：程序化切页取消位移，瞬跳
                                if (reduceMotion) pagerState.scrollToPage(index)
                                else pagerState.animateScrollToPage(index)
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

    // 规范 §4.5：选中 scale 1.06，未选中 1.0；reduced motion 时目标恒为 1（无缩放动画）
    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else if (isSelected) 1.06f else 1f,
        animationSpec = motionSpec(defaultSpring()),
        label = "bottomNavScale",
    )
    // label 颜色 200ms 平滑过渡（小组件预算），与图标 600ms 渐变衔接；
    // reduced motion 降 180ms 短 tween，颜色可读性保留（规范 §8）
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) AppColors.colorScheme.primary
        else AppColors.colorScheme.textTertiary,
        animationSpec = motionSpec(tween(DURATION_MICRO_MS)),
        label = "bottomNavLabelColor",
    )

    Column(
        modifier = Modifier
            .pressable(onClick = onClick, debounce = false)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 设计稿「首页」画板底栏测量：icon.md 24dp（DESIGN-SPEC §5 bottomNav），Canvas 绘制带动画
        AnimatedBottomNavIcon(
            solidPath = solidPath,
            outlinePath = outlinePath,
            isSelected = isSelected,
            reduceMotion = reduceMotion,
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
        )
        // 设计稿底栏：图标与 label 间距 4.5 ≈ spaceXxs
        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}



/**
 * 规范 §4.5 底栏图标选中动效（600ms）：0ms 外轮廓勾边 → 140ms 灰实心淡出 →
 * 170ms 品牌色淡入 → 400ms 描边退场 → 600ms 终态。灰实心与品牌填充共用同一条
 * 带镂空路径（evenOdd），交叉淡变期间镂空始终是底色；勾边只沿外轮廓。
 * 路径解析用官方 androidx.core.graphics.PathParser（完整 SVG 语法）。
 * 取消选中：不复播 600ms 时间线（勾边属选中入场专属）——时间线保持终态，
 * 由 [deselectFade] 在灰/绿同一条实心路径上做 DURATION_MICRO_MS 交叉淡变收尾，
 * 避免切 tab 时新旧两个图标同时跑全时序的拖沓感。
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
    // 取消选中淡变系数：0 = 正常时间线状态，1 = 已取消选中（灰实心终态）。
    // 初值按首次组合的 isSelected 定（未选中的 tab 直接以灰实心起步，不跑淡变）
    val deselectFade = remember { Animatable(if (isSelected) 0f else 1f) }
    LaunchedEffect(isSelected, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(if (isSelected) 1f else 0f)
            deselectFade.snapTo(if (isSelected) 0f else 1f)
        } else if (isSelected) {
            deselectFade.snapTo(0f)
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 600, easing = LinearEasing))
        } else {
            // progress 保持当前值不动，仅灰/绿交叉淡变（Canvas 按 deselectFade 分流绘制）
            deselectFade.animateTo(1f, tween(durationMillis = DURATION_MICRO_MS, easing = LinearEasing))
        }
    }

    val brandColor = AppColors.colorScheme.primary
    val grayColor = AppColors.colorScheme.textTertiary

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
            if (deselectFade.value > 0f) {
                // 取消选中：灰/绿交叉淡变（同一条 evenOdd 实心路径，§4.2⑤），无勾边；
                // 灰 + 绿 alpha 恒为 1，不出现亮度凹陷
                drawPath(path = solid, color = grayColor.copy(alpha = deselectFade.value), style = Fill)
                drawPath(path = solid, color = brandColor.copy(alpha = 1f - deselectFade.value), style = Fill)
            } else {
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
}
