package com.smartwash.feature.laundry.ui.service

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DryCleaning
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.common.utils.model.RequestState
import androidx.compose.material3.Icon
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.currentView
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.performHaptic
import com.smartwash.common.utils.pressable
import com.smartwash.feature.laundry.R
import com.smartwash.feature.laundry.api.LaundryRoute
import com.smartwash.feature.laundry.network.vo.LaundryItem

@Composable
fun ServicePage(
    navController: NavHostController,
    serviceViewModel: ServiceViewModel = hiltViewModel()
) {
    val laundryItems by serviceViewModel.laundryItems.collectAsState()
    val getLaundryItemState by serviceViewModel.getLaundryItemState.collectAsState()

    LaunchedEffect(Unit) {
        serviceViewModel.getLaundryItem()
    }

    val laundryState = getLaundryItemState
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        // 骨架 ↔ 内容整屏硬切 → 交叉淡变（reduced motion 经 motionSpec 自动降级为 180ms）
        Crossfade(
            targetState = laundryState is RequestState.Loading,
            animationSpec = motionSpec(enterTween(DURATION_MICRO_MS)),
            modifier = Modifier.fillMaxSize(),
            label = "serviceContent"
        ) { isLoading ->
            when {
                isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
                // 无缓存且请求失败时零呈现（空白卡）→ 空态 + 重试；只加 UI 分支，不动 VM 状态机
                laundryState is RequestState.Error -> ServiceErrorState(
                    message = (laundryState as RequestState.Error).getMessage(context),
                    onRetry = { serviceViewModel.getLaundryItem() }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 搜索胶囊
                    item {
                        SearchBar()
                    }

                // 洗护分类标题
                item {
                    // 画板实测：搜索胶囊底(80) → 标题顶(116) = 36
                    Spacer(modifier = Modifier.height(36.dp))
                    Text(
                        text = stringResource(R.string.laundry_service),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.colorScheme.textPrimary,
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                    // 服务项目列表 — 单张卡片包裹，内部以 0.5dp 发丝线分隔（规范 v3 §3.4）
                    item {
                        ServiceListCard(
                            items = laundryItems,
                            onReserve = { navController.navigate(LaundryRoute.Laundry.text) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.service_tips),
                            // 画板实测 12/16/400 —— caption 档（bodySmall），非 captionStrong
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.pagePadding),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * 服务页错误空态 — 无缓存且请求失败时给出可读反馈与重试入口（原来是空白卡）。
 * 空态入场自带 reduced motion 降级（见 [EmptyState]）。
 */
@Composable
private fun ServiceErrorState(
    message: String,
    onRetry: () -> Unit
) {
    EmptyState(
        icon = Icons.Rounded.LocalLaundryService,
        message = message,
        modifier = Modifier.fillMaxSize(),
        action = {
            AppButton(
                text = stringResource(R.string.service_retry),
                onClick = onRetry,
                variant = AppButtonVariant.SECONDARY
            )
        }
    )
}

/**
 * 搜索胶囊 — 48dp 高（画板实测），胶囊圆角（radiusFull），
 * 白底 + 1px 描边 + 阴影 AppElevation.level1（规范 §2.4 卡片档，替代原「无阴影」写法）。
 */
@Composable
private fun SearchBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = 8.dp),
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                // 画板实测 #6B6E73 = textSecondary（非 textTertiary #A9AEA9）
                tint = AppColors.colorScheme.textSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.service_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
        }
    }
}

/**
 * 服务项目列表卡片 — 白底 + 1px 描边 + 阴影 AppElevation.level1（规范 §2.4 卡片档，替代原「无阴影」写法）。
 * 36dp 图标容器（四色 iconTile）+ 等宽价格 + 预约胶囊（画板实测 60×24）。
 */
@Composable
private fun ServiceListCard(
    items: List<LaundryItem>,
    onReserve: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Column {
            items.forEachIndexed { index, item ->
                ServiceItemRow(item = item, onReserve = onReserve)
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(1.dp)
                            .background(AppColors.colorScheme.hairline)
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceItemRow(
    item: LaundryItem,
    onReserve: () -> Unit
) {
    val view = currentView()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = {
                // 点击因果明确的时刻补触觉（规范 §7.7）；清单原写 HapticEffect.CLICK，
                // 枚举中无此项，取语义最近的 LIGHT（一般交互轻触）
                view.performHaptic(HapticEffect.LIGHT)
                onReserve()
            })
            // 画板实测行体 74 = 上 15 + 内容 52 + 下 7（卡高 296 = 4×74）；
            // 图标盒/名称/价格同顶（画板 y 均 167），非垂直居中
            .padding(start = 20.dp, end = 20.dp, top = 15.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        val (tileColor, tileTint) = serviceTileColors(item)
        IconBox(
            icon = serviceIcon(item),
            size = 36.dp,
            iconSize = 20.dp,
            containerColor = tileColor,
            iconTint = tileTint
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                // 画板实测 12/16/400 —— caption 档（bodySmall），无 tnum
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.currency_format, String.format("%.2f", item.basePrice)),
                // 画板实测价格色 #1E8C5C（浅）/ #5BC894（深）= green.700，双主题均落 primaryDark
                style = AppTextStyles.AmountMedium,
                color = AppColors.colorScheme.primaryDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            ReservePill()
        }
    }
}

/**
 * 预约胶囊 — 60×24（画板实测），radiusFull，
 * primaryLight 底 + onPrimaryContainer 文字（浅 #F0FBF4/#17322B、深 #17322B/#F0FBF4）；
 * labelMedium = captionStrong 12/16/500。触控热区由整行 pressable 承担（行体 74dp ≥ 48dp）。
 */
@Composable
private fun ReservePill() {
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 24.dp)
            .clip(RoundedCornerShape(AppDimens.radiusFull))
            .background(AppColors.colorScheme.primaryLight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.service_reserve),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 服务图标统一走 `Icons.Rounded`（规范 §10「混用图标变体」：filled 与 rounded 不得混在同一组）。
 * 本组为 serviceIcons 回退序列 + [serviceIcon] 的返回值集合，二者必须同变体。
 *
 * ⚠️ 待办（本次不动）：[serviceIcon] 按中文服务名 `contains("干"/"精"/"熨"/"洗")` 选图标，
 * 名称改动即错配；应改为按 itemId/服务端下发的图标标识映射。属逻辑改动，本次仅统一图标变体。
 */
private val serviceIcons = listOf(
    Icons.Rounded.LocalLaundryService,
    Icons.Rounded.DryCleaning,
    Icons.Rounded.Checkroom,
    Icons.Rounded.CleaningServices,
)

private fun serviceIcon(item: LaundryItem): ImageVector {
    val name = item.itemName
    return when {
        name.contains("干") -> Icons.Rounded.DryCleaning
        name.contains("精") || name.contains("护理") || name.contains("奢") -> Icons.Rounded.Checkroom
        name.contains("熨") -> Icons.Rounded.CleaningServices
        name.contains("洗") || name.contains("标准") -> Icons.Rounded.LocalLaundryService
        else -> serviceIcons[(item.itemId % serviceIcons.size).toInt()]
    }
}

/**
 * 图标盒四色配色（画板「洗衣服务」浅/深两板逐值实测）：
 *  绿（标准洗）—— 底 primaryLight(#F0FBF4/#17322B) + 前景 primaryDark(#1E8C5C/#5BC894)；
 *    不用 iconContainer/iconForegroundGreen（其浅色前景 #2D9B6A、深色底 #1D3229 与画板各差一档）
 *  蓝（干洗）/ 橙（熨烫）/ 紫（奢护）—— iconContainerX / iconForegroundX 系列与画板逐值一致。
 * 分支顺序与 [serviceIcon] 保持同步；fallback 序列对应 serviceIcons：
 * Local=绿 / Dry=蓝 / Checkroom=紫 / Cleaning=橙。
 */
@Composable
private fun serviceTileColors(item: LaundryItem): Pair<Color, Color> {
    val colors = AppColors.colorScheme
    val name = item.itemName
    return when {
        name.contains("干") -> colors.iconContainerBlue to colors.iconForegroundBlue
        name.contains("精") || name.contains("护理") || name.contains("奢") ->
            colors.iconContainerPurple to colors.iconForegroundPurple
        name.contains("熨") -> colors.iconContainerOrange to colors.iconForegroundOrange
        name.contains("洗") || name.contains("标准") -> colors.primaryLight to colors.primaryDark
        else -> when ((item.itemId % serviceIcons.size).toInt()) {
            1 -> colors.iconContainerBlue to colors.iconForegroundBlue
            2 -> colors.iconContainerPurple to colors.iconForegroundPurple
            3 -> colors.iconContainerOrange to colors.iconForegroundOrange
            else -> colors.primaryLight to colors.primaryDark
        }
    }
}
