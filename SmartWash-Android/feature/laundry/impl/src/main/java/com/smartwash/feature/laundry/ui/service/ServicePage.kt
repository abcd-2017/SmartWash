package com.smartwash.feature.laundry.ui.service

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DryCleaning
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.utils.model.RequestState
import androidx.compose.material3.Icon
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.ui.theme.ServiceDry
import com.smartwash.common.ui.theme.ServiceLuxury
import com.smartwash.common.ui.theme.ServicePress
import com.smartwash.common.ui.theme.ServiceShoes
import com.smartwash.common.ui.theme.ServiceWash
import com.smartwash.common.utils.pressable
import com.smartwash.feature.laundry.R
import com.smartwash.feature.laundry.network.vo.LaundryItem

@Composable
fun ServicePage(
    serviceViewModel: ServiceViewModel = hiltViewModel()
) {
    val laundryItems by serviceViewModel.laundryItems.collectAsState()
    val getLaundryItemState by serviceViewModel.getLaundryItemState.collectAsState()

    LaunchedEffect(Unit) {
        serviceViewModel.getLaundryItem()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        if (getLaundryItemState is RequestState.Loading) {
            LoadingState(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // 搜索胶囊
                item {
                    SearchBar()
                }

                // 运营位
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    PromoBanner()
                }

                // 洗护分类标题
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = stringResource(R.string.laundry_service),
                        style = AppTextStyles.SectionTitle,
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 服务项目列表 — 单张卡片包裹，内部以 0.5dp 发丝线分隔（规范 v3 §3.4）
                item {
                    ServiceListCard(items = laundryItems)
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.service_tips),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textTertiary,
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 搜索胶囊 — 44dp 高，圆角 22px，白底 + 1px 描边（规范 v3）。
 */
@Composable
private fun SearchBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding)
            .padding(top = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline),
        shadowElevation = AppElevation.level1
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AppColors.colorScheme.textTertiary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.service_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textTertiary
            )
        }
    }
}

/**
 * 运营位 — 浅绿渐变底，圆角 20px（规范 v3）。
 */
@Composable
private fun PromoBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        shape = RoundedCornerShape(AppDimens.radiusXl),
        color = AppColors.colorScheme.primaryLight,
        shadowElevation = AppElevation.level1
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(AppDimens.radiusMd),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocalLaundryService,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = AppColors.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.service_promo_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.service_promo_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
        }
    }
}

/**
 * 服务项目列表卡片 — 白底 + 1px 描边 + 轻阴影（规范 v3 §3.1）。
 * 36dp 图标容器 + 等宽价格 + 行内"预约 >"动作。
 */
@Composable
private fun ServiceListCard(items: List<LaundryItem>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.colorScheme.outline),
        shadowElevation = AppElevation.level1
    ) {
        Column {
            items.forEachIndexed { index, item ->
                ServiceItemRow(item = item)
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.5.dp)
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
) {
    val (containerColor, iconTint) = serviceColors(item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = {})
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(
            icon = serviceIcon(item),
            size = 36.dp,
            iconSize = 18.dp,
            containerColor = containerColor,
            iconTint = iconTint
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.colorScheme.textPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textTertiary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.currency_format, String.format("%.2f", item.basePrice)),
                style = AppTextStyles.AmountMedium.copy(fontSize = 19.sp),
                color = AppColors.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.service_reserve) + " >",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.primary
            )
        }
    }
}

private val serviceIcons = listOf(
    Icons.Filled.LocalLaundryService,
    Icons.Rounded.DryCleaning,
    Icons.Rounded.Checkroom,
    Icons.Filled.CleaningServices,
)

private fun serviceIcon(item: LaundryItem): ImageVector {
    val name = item.itemName
    return when {
        name.contains("干") -> Icons.Rounded.DryCleaning
        name.contains("精") || name.contains("护理") || name.contains("奢") -> Icons.Rounded.Checkroom
        name.contains("熨") -> Icons.Filled.CleaningServices
        name.contains("洗") || name.contains("标准") -> Icons.Filled.LocalLaundryService
        else -> serviceIcons[(item.itemId % serviceIcons.size).toInt()]
    }
}

/**
 * 服务类型 → (图标容器浅底, 图标深色)。
 * 容器浅底取自规范 §2.1 六色浅底（iconContainer*），图标色取自对应深色版（规范 §3.2）。
 */
@Composable
private fun serviceColors(item: LaundryItem): Pair<Color, Color> {
    val name = item.itemName
    return when {
        name.contains("干") -> AppColors.colorScheme.iconContainerPurple to ServiceDry
        name.contains("精") || name.contains("护理") || name.contains("奢") -> AppColors.colorScheme.iconContainerBlue to ServiceLuxury
        name.contains("熨") -> AppColors.colorScheme.iconContainerOrange to ServicePress
        name.contains("鞋") -> AppColors.colorScheme.iconContainerPink to ServiceShoes
        name.contains("洗") || name.contains("标准") -> AppColors.colorScheme.iconContainerGreen to ServiceWash
        else -> AppColors.colorScheme.iconContainerGreen to ServiceWash
    }
}
