package com.smartwash.lite.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smartwash.R
import com.smartwash.common.ui.components.AppCard
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.feature.dining.api.DiningRoute
import com.smartwash.feature.game.api.GameRoute
import com.smartwash.feature.user.api.UserRoute

/**
 * lite 首页（占位骨架）——单卡片结构，核心入口为「点餐」。
 *
 * 布局：顶部品牌区（吃啥 / Today Eat，品牌渐变 hero）→ 主卡「点餐」→ 次卡「游戏」
 * → 底部「设置」入口。路由常量复用 dining:api 的 [DiningRoute.OrderList]、
 * game:api 的 [GameRoute.Center]、user:api 的 [UserRoute.Setting]。
 *
 * 视觉全部取自清氧令牌（[AppColors] / [AppDimens] / [AppTextStyles]），未自创色值/间距/圆角；
 * 图标为 Material 内置（未引外部图标库）；入场动效经 [motionSpec] 提供 reduced motion 降级
 * （降级时位移归零，仅保留 180ms 淡入）。
 */
@Composable
fun LiteHomePage(navController: NavController) {
    val reduceMotion = LocalReduceMotion.current
    // 入场状态：一次性触发，reduced motion 时位移分量归零
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = motionSpec(defaultSpring()),
        label = "liteHomeEntranceAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.pagePadding)
    ) {
        Spacer(modifier = Modifier.height(AppDimens.spaceXl))

        // 品牌区 — 品牌渐变 hero（规范 §3.1 hero 变体：渐变底 + 白字，无描边）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimens.radiusXl))
                .background(
                    Brush.linearGradient(
                        listOf(AppColors.colorScheme.primary, AppColors.colorScheme.primaryDark)
                    )
                )
                .padding(AppDimens.cardPadding)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.lite_brand_name),
                    style = AppTextStyles.RootTitle,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Text(
                    text = stringResource(R.string.lite_brand_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        // 主卡「点餐」— 核心入口
        LiteHomeCard(
            icon = Icons.Filled.Restaurant,
            iconContainerColor = AppColors.colorScheme.iconContainerGreen,
            iconTint = AppColors.colorScheme.iconForegroundGreen,
            title = stringResource(R.string.lite_home_order),
            description = stringResource(R.string.lite_home_order_desc),
            onClick = { navController.navigate(DiningRoute.OrderList) },
            modifier = Modifier.graphicsLayer {
                alpha = entranceAlpha
                translationY = if (reduceMotion) 0f else (1f - entranceAlpha) * 16f * density
            }
        )

        Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

        // 次卡「游戏」
        LiteHomeCard(
            icon = Icons.Filled.SportsEsports,
            iconContainerColor = AppColors.colorScheme.iconContainerPurple,
            iconTint = AppColors.colorScheme.iconForegroundPurple,
            title = stringResource(R.string.lite_home_game),
            description = stringResource(R.string.lite_home_game_desc),
            onClick = { navController.navigate(GameRoute.Center) },
            modifier = Modifier.graphicsLayer {
                alpha = entranceAlpha
                translationY = if (reduceMotion) 0f else (1f - entranceAlpha) * 16f * density
            }
        )

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        // 底部「设置」入口 — 轻量行卡，与主/次卡区分层级
        LiteHomeCard(
            icon = Icons.Filled.Settings,
            iconContainerColor = AppColors.colorScheme.iconContainerTeal,
            iconTint = AppColors.colorScheme.iconForegroundTeal,
            title = stringResource(R.string.lite_home_setting),
            description = null,
            onClick = { navController.navigate(UserRoute.Setting) },
            modifier = Modifier.graphicsLayer {
                alpha = entranceAlpha
                translationY = if (reduceMotion) 0f else (1f - entranceAlpha) * 16f * density
            }
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceXxl))
    }
}

/**
 * lite 首页入口卡 — 复用清氧唯一卡片画法（[AppCard]）+ 图标容器（六色浅底按语义取色）。
 * [description] 为 null 时只渲染单行（用于「设置」这类轻入口）。
 */
@Composable
private fun LiteHomeCard(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标容器：44dp / 12dp 圆角（规范 §3.2 主容器档），复用清氧共享组件
            IconBox(
                icon = icon,
                size = 44.dp,
                iconSize = 24.dp,
                containerColor = iconContainerColor,
                iconTint = iconTint,
            )
            Spacer(modifier = Modifier.size(AppDimens.spaceMd))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = AppTextStyles.CardTitle,
                    color = AppColors.colorScheme.textPrimary
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppColors.colorScheme.textTertiary
            )
        }
    }
}
