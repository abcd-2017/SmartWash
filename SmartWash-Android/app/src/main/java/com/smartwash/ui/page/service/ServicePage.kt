package com.smartwash.ui.page.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DryCleaning
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.R
import com.smartwash.network.vo.laundry.LaundryItem
import com.smartwash.ui.common.LoadingState
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.RequestState
import com.smartwash.ui.theme.IconBox

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
            // 页面标题
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                ) {
                    Text(
                        text = stringResource(R.string.laundry_service),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.service_page_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 服务项目列表
            items(laundryItems) { item ->
                ServiceItemCard(
                    item = item,
                    modifier = Modifier.padding(
                        horizontal = AppDimens.pagePadding,
                        vertical = AppDimens.cardSpacing / 2
                    )
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
        }
    }
}

@Composable
private fun ServiceItemCard(
    item: LaundryItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon = serviceIcon(item))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.currency_format, String.format("%.2f", item.basePrice)),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.colorScheme.primary
                )
            }
        }
    }
}

private val serviceIcons = listOf(
    Icons.Filled.LocalLaundryService, // 洗衣机
    Icons.Rounded.DryCleaning,        // 干洗袋
    Icons.Rounded.Checkroom,          // 衣架
    Icons.Filled.CleaningServices,    // 清洁
)

private fun serviceIcon(item: LaundryItem): ImageVector {
    val name = item.itemName
    return when {
        name.contains("干") -> Icons.Rounded.DryCleaning
        name.contains("精") || name.contains("护理") || name.contains("奢") -> Icons.Rounded.Checkroom
        name.contains("熨") -> Icons.Filled.CleaningServices
        name.contains("洗") || name.contains("标准") -> Icons.Filled.LocalLaundryService
        // 兜底：按 itemId 轮转，保证不同套餐图标不同
        else -> serviceIcons[(item.itemId % serviceIcons.size).toInt()]
    }
}
