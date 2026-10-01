package com.smartwash.feature.laundry.ui.service

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DryCleaning
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 服务项目列表 — 紧凑分隔线列表
                items(laundryItems, key = { it.itemId }) { item ->
                    ServiceItemRow(
                        item = item,
                        modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = AppColors.colorScheme.divider
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ServiceItemRow(
    item: LaundryItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable(onClick = {})
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = serviceIcon(item),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = serviceColor(item)
        )
        Spacer(modifier = Modifier.width(12.dp))
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
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
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

private fun serviceColor(item: LaundryItem): Color {
    val name = item.itemName
    return when {
        name.contains("干") -> ServiceDry
        name.contains("精") || name.contains("护理") || name.contains("奢") -> ServiceLuxury
        name.contains("熨") -> ServicePress
        name.contains("鞋") -> ServiceShoes
        name.contains("洗") || name.contains("标准") -> ServiceWash
        else -> ServiceWash
    }
}
