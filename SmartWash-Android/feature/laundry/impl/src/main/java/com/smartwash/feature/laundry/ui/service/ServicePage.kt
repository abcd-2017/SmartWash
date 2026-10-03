package com.smartwash.feature.laundry.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DryCleaning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.common.model.RequestState
import com.smartwash.common.ui.components.GroupCard
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
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

    androidx.compose.foundation.layout.Box(
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
                // 页面标题
                item {
                    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                    Column(
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    ) {
                        Text(
                            text = stringResource(R.string.laundry_service),
                            style = AppTextStyles.PageTitle,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spaceXxs))
                        Text(
                            text = stringResource(R.string.service_page_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                }

                // 服务项目列表 — 单张卡片包裹，内部以 0.5dp 发丝线分隔（规范 §3.4）
                item {
                    GroupCard(
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    ) {
                        laundryItems.forEachIndexed { index, item ->
                            ServiceItemRow(item = item)
                            if (index < laundryItems.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = AppColors.colorScheme.hairline
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(AppDimens.spaceXl)) }
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
            .height(56.dp)
            .pressable(onClick = {})
            .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(
            icon = serviceIcon(item),
            size = 36.dp,
            iconSize = 18.dp,
            containerColor = containerColor,
            iconTint = iconTint
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.colorScheme.textPrimary
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary
            )
        }
        Text(
            text = stringResource(R.string.currency_format, String.format("%.2f", item.basePrice)),
            style = AppTextStyles.AmountMedium,
            color = AppColors.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        TextButton(onClick = {}) {
            Text(
                text = stringResource(R.string.service_reserve),
                style = MaterialTheme.typography.labelLarge,
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
