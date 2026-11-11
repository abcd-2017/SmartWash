package com.smartwash.feature.lab.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.pressable
import com.smartwash.feature.lab.LabToy
import com.smartwash.feature.lab.R

/**
 * 解压实验舱首页：PageHeader + 三个玩具入口卡（标准信息卡画法）。
 * 纯本地页面（模块无网络/无存储），每屏一个锚点、克制不花哨：
 * 三卡同权重纵排，无 hero、无大字锚点。卡片点击进对应玩具页。
 */
@Composable
fun LabHomePage(
    navController: NavHostController,
    viewModel: LabHomeViewModel = hiltViewModel(),
) {
    val toys by viewModel.toys.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        PageHeader(
            title = stringResource(R.string.lab_title),
            onBack = { navController.navigateUp() },
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 10 张卡总高超出首屏：整列可滚，末卡「掌上星图」才可达
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.pagePadding)
                .padding(bottom = AppDimens.sectionSpacing)
                // edge-to-edge 下列表底部避让系统手势区（对齐同模块页脚处理）
                .navigationBarsPadding(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppDimens.cardSpacing),
        ) {
            toys.forEach { toy ->
                ToyEntryCard(
                    toy = toy,
                    onClick = { navController.navigate(toy.route) },
                )
            }
        }
    }
}

/**
 * 玩具入口卡（卡片唯一画法，规范 §3.1：白 surface + 1dp outline 描边 +
 * level1 阴影 + radiusLg 圆角 + cardPadding 内边距）：44dp 图标容器
 * （v0.4 §3.2 主容器档 44/12/24，六色浅底语义）+ 名称 CardTitle（18/24/600）+ 描述 14sp。
 * 整卡 pressable（scale 0.97，reduced motion 自动降级透明度）；
 * a11y：contentDescription = 名称 + 描述。
 */
@Composable
private fun ToyEntryCard(
    toy: LabToy,
    onClick: () -> Unit,
) {
    val scheme = AppColors.colorScheme
    val (containerColor, iconTint) = when (toy) {
        LabToy.BUBBLE_WRAP -> scheme.iconContainerGreen to scheme.iconForegroundGreen
        LabToy.BREATH -> scheme.iconContainerBlue to scheme.iconForegroundBlue
        LabToy.FISH -> scheme.iconContainerTeal to scheme.iconForegroundTeal
        LabToy.SAND -> scheme.iconContainerOrange to scheme.iconForegroundOrange
        LabToy.FLUID -> scheme.iconContainerPurple to scheme.iconForegroundPurple
        LabToy.DRUM -> scheme.iconContainerPink to scheme.iconForegroundPink
        LabToy.PAINTING -> scheme.iconContainerGreen to scheme.iconForegroundGreen
        LabToy.SOUNDSCAPE -> scheme.iconContainerBlue to scheme.iconForegroundBlue
        LabToy.PET -> scheme.iconContainerPink to scheme.iconForegroundPink
        LabToy.STAR_MAP -> scheme.iconContainerPurple to scheme.iconForegroundPurple
    }
    val title = toyLabel(toy)
    val description = toyDescription(toy)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title，$description" }
            .pressable(onClick = onClick)
            .softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = scheme.surface,
        // 规范 §2.4：卡片 = 1px 描边 + 0 2px 12px rgba(20,40,30,.05) → level1
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 44dp 图标容器（v0.4 §3.2 主容器档：44dp / 12dp 圆角 / 24dp 图标）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                    .background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = toyIcon(toy),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint,
                )
            }
            // 图标盒-文字间距取 spaceSm(12)（DESIGN-SPEC v0.4.1 卡内口径 8/12，
            // 原 spaceMd 16 超口径；鸿蒙端 2026-11-11 已同步 12，两端一致）
            Spacer(modifier = Modifier.size(AppDimens.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    // 卡片标题走 v0.4 字阶 title 档语义别名（18/24/600），
                    // 不用 bodyLarge+Medium 的档外组合
                    style = AppTextStyles.CardTitle,
                    color = scheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------- 玩具入口静态映射（图标 Material 内置 Rounded 变体组内一致，规范 §4.1） ----------

private fun toyIcon(toy: LabToy): ImageVector = when (toy) {
    LabToy.BUBBLE_WRAP -> Icons.Rounded.BubbleChart
    LabToy.BREATH -> Icons.Rounded.SelfImprovement
    LabToy.FISH -> Icons.Rounded.Waves
    LabToy.SAND -> Icons.Rounded.Grain
    LabToy.FLUID -> Icons.Rounded.WaterDrop
    LabToy.DRUM -> Icons.Rounded.MusicNote
    LabToy.PAINTING -> Icons.Rounded.Brush
    LabToy.SOUNDSCAPE -> Icons.Rounded.GraphicEq
    LabToy.PET -> Icons.Rounded.Pets
    LabToy.STAR_MAP -> Icons.Rounded.Star
}

@Composable
private fun toyLabel(toy: LabToy): String = when (toy) {
    LabToy.BUBBLE_WRAP -> stringResource(R.string.lab_toy_bubble_wrap)
    LabToy.BREATH -> stringResource(R.string.lab_toy_breath)
    LabToy.FISH -> stringResource(R.string.lab_toy_fish)
    LabToy.SAND -> stringResource(R.string.lab_toy_sand)
    LabToy.FLUID -> stringResource(R.string.lab_toy_fluid)
    LabToy.DRUM -> stringResource(R.string.lab_toy_drum)
    LabToy.PAINTING -> stringResource(R.string.lab_toy_painting)
    LabToy.SOUNDSCAPE -> stringResource(R.string.lab_toy_soundscape)
    LabToy.PET -> stringResource(R.string.lab_toy_pet)
    LabToy.STAR_MAP -> stringResource(R.string.lab_toy_star_map)
}

@Composable
private fun toyDescription(toy: LabToy): String = when (toy) {
    LabToy.BUBBLE_WRAP -> stringResource(R.string.lab_toy_bubble_wrap_desc)
    LabToy.BREATH -> stringResource(R.string.lab_toy_breath_desc)
    LabToy.FISH -> stringResource(R.string.lab_toy_fish_desc)
    LabToy.SAND -> stringResource(R.string.lab_toy_sand_desc)
    LabToy.FLUID -> stringResource(R.string.lab_toy_fluid_desc)
    LabToy.DRUM -> stringResource(R.string.lab_toy_drum_desc)
    LabToy.PAINTING -> stringResource(R.string.lab_toy_painting_desc)
    LabToy.SOUNDSCAPE -> stringResource(R.string.lab_toy_soundscape_desc)
    LabToy.PET -> stringResource(R.string.lab_toy_pet_desc)
    LabToy.STAR_MAP -> stringResource(R.string.lab_toy_star_map_desc)
}
