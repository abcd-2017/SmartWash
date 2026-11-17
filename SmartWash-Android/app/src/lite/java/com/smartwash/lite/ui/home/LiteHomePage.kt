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
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.smartwash.common.ui.components.AppButton
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
 * lite 首页（吃啥）——**无底栏**，聚焦点单：发起点单（主 CTA）/ 我的点单 / 菜谱库 + 游戏 + 设置。
 *
 * 布局：顶部品牌 hero（CTA 渐变 + onPrimary 文字）→ 唯一实心主 CTA「发起点单」→ 次入口卡
 * 「我的点单 / 菜谱库」→ 次级「游戏」→ 底部「设置」。主 CTA 与次入口形成明确层级（规范：
 * 每屏一个主 CTA，大面积品牌底只属于 hero 与主 CTA）。路由常量复用 dining:api 的
 * [DiningRoute.OrderCreate] / [DiningRoute.OrderList] / [DiningRoute.RecipeLibrary]、
 * game:api 的 [GameRoute.Center]、user:api 的 [UserRoute.Setting]。
 *
 * 视觉全部取自品牌令牌（[AppColors] / [AppDimens] / [AppTextStyles]），未自创色值/间距/圆角。
 * 入场动效经 [motionSpec] 提供 reduced motion 降级（降级时位移归零，仅保留淡入）。
 */
@Composable
fun LiteHomePage(navController: NavController) {
    val reduceMotion = LocalReduceMotion.current
    // 入场状态：一次性触发，reduced motion 时位移分量归零
    var entered by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { entered = 1 }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered == 1) 1f else 0f,
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

        // 品牌区 — hero（规范 §3.1 hero 变体：CTA 渐变底 + onPrimary 文字，无描边、无阴影）。
        // 渐变走 ctaGradient 而非 primary→primaryDark：lite 主色 #EA580C 上白字仅 3.57:1，
        // CTA 起点下移到 #C2410C（5.2:1）；深色主题文字走 onPrimary 深橙（亮橙底 6.7:1）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimens.radiusXl))
                .background(
                    Brush.linearGradient(
                        listOf(
                            AppColors.colorScheme.ctaGradientStart,
                            AppColors.colorScheme.ctaGradientEnd
                        )
                    )
                )
                .padding(AppDimens.cardPadding)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.lite_brand_name),
                    style = AppTextStyles.RootTitle,
                    color = AppColors.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Text(
                    text = stringResource(R.string.lite_brand_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.onPrimary.copy(alpha = 0.82f)
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                Text(
                    text = stringResource(R.string.lite_home_hero_line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        // 主入口「发起点单」— 唯一实心主 CTA（全宽胶囊），与下方次入口卡拉开层级
        Text(
            text = stringResource(R.string.lite_home_create_desc),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.colorScheme.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = entranceAlpha
                    translationY = if (reduceMotion) 0f
                    else (1f - entranceAlpha) * 16f * density
                }
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = entranceAlpha
                translationY = if (reduceMotion) 0f
                else (1f - entranceAlpha) * 16f * density
            }
        ) {
            AppButton(
                text = stringResource(R.string.lite_home_create),
                onClick = { navController.navigate(DiningRoute.OrderCreate()) },
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        // 次入口「我的点单」— 复看进行中的点单 / 历史单
        LiteHomeCard(
            icon = Icons.Filled.ReceiptLong,
            iconContainerColor = AppColors.colorScheme.iconContainerBlue,
            iconTint = AppColors.colorScheme.iconForegroundBlue,
            title = stringResource(R.string.lite_home_orders),
            description = stringResource(R.string.lite_home_orders_desc),
            onClick = { navController.navigate(DiningRoute.OrderList) },
            entranceAlpha = entranceAlpha,
            entranceShift = 12f,
            reduceMotion = reduceMotion,
        )

        Spacer(modifier = Modifier.height(AppDimens.cardSpacing))

        // 次入口「菜谱库」— 公共菜谱浏览；发起页勾选菜品也来自同一份数据
        LiteHomeCard(
            icon = Icons.Filled.Restaurant,
            iconContainerColor = AppColors.colorScheme.iconContainerOrange,
            iconTint = AppColors.colorScheme.iconForegroundOrange,
            title = stringResource(R.string.lite_home_recipes),
            description = stringResource(R.string.lite_home_recipes_desc),
            onClick = { navController.navigate(DiningRoute.RecipeLibrary) },
            entranceAlpha = entranceAlpha,
            entranceShift = 12f,
            reduceMotion = reduceMotion,
        )

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        // 次级「游戏」— 非点单主流程，入场位移分量最小
        LiteHomeCard(
            icon = Icons.Filled.SportsEsports,
            iconContainerColor = AppColors.colorScheme.iconContainerPurple,
            iconTint = AppColors.colorScheme.iconForegroundPurple,
            title = stringResource(R.string.lite_home_game),
            description = stringResource(R.string.lite_home_game_desc),
            onClick = { navController.navigate(GameRoute.Center) },
            entranceAlpha = entranceAlpha,
            entranceShift = 8f,
            reduceMotion = reduceMotion,
        )

        Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

        // 底部「设置」入口 — 轻量行卡，与主/次卡区分层级（无副文案）
        LiteHomeCard(
            icon = Icons.Filled.Settings,
            iconContainerColor = AppColors.colorScheme.iconContainerTeal,
            iconTint = AppColors.colorScheme.iconForegroundTeal,
            title = stringResource(R.string.lite_home_setting),
            description = null,
            onClick = { navController.navigate(UserRoute.Setting) },
            entranceAlpha = entranceAlpha,
            entranceShift = 8f,
            reduceMotion = reduceMotion,
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceXxl))
    }
}

/**
 * lite 首页入口卡 — 复用唯一卡片画法（[AppCard]）+ 图标容器（六色浅底按语义取色）。
 * [description] 为 null 时只渲染单行（用于「设置」这类轻入口）。
 *
 * @param entranceShift 入场位移分量（dp）：主入口 > 次入口 > 次级，形成主次层级；
 *                      reduced motion 时归零，只保留淡入。
 */
@Composable
private fun LiteHomeCard(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    description: String?,
    onClick: () -> Unit,
    entranceAlpha: Float,
    entranceShift: Float,
    reduceMotion: Boolean,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entranceAlpha
                translationY = if (reduceMotion) 0f else (1f - entranceAlpha) * entranceShift * density
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标容器：44dp / 12dp 圆角（规范 §3.2 主容器档），复用共享组件
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
