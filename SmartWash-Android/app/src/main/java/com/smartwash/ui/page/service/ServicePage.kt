package com.smartwash.ui.page.service

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwash.R
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.IconBox

@Composable
fun ServicePage(
    serviceViewModel: ServiceViewModel = hiltViewModel()
) {
    val laundryItems by serviceViewModel.laundryItems.collectAsState()

    LaunchedEffect(Unit) {
        serviceViewModel.getLaundryItem()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero 区域
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.laundry_service),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.service_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.colorScheme.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AppColors.colorScheme.primaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = AppColors.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 服务说明卡
            item {
                AppCard(modifier = Modifier.padding(horizontal = AppDimens.pagePadding)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        IconBox(
                            icon = Icons.Default.CleaningServices,
                            size = 40.dp,
                            iconSize = 20.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.service_description),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.service_description_content),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
            }

            // 服务项目标题
            item {
                Text(
                    text = stringResource(R.string.service_items),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 服务项目网格 — 2列
            val rows = laundryItems.chunked(2)
            items(rows) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                ) {
                    rowItems.forEach { item ->
                        ServiceGridCard(
                            title = item.itemName,
                            description = item.description,
                            price = item.basePrice.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.cardSpacing))
            }

            // 更多服务标题
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.more_services),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = AppDimens.pagePadding)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 上门取件服务
            item {
                ServiceItemCard(
                    service = ServiceItem(
                        title = stringResource(R.string.retrieval_service),
                        description = stringResource(R.string.scan_pickup_desc),
                        price = stringResource(R.string.free),
                        icon = Icons.Default.LocalMall,
                        onClick = {}
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ServiceGridCard(
    title: String,
    description: String,
    price: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.smallCardRadius),
        color = AppColors.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.colorScheme.primaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AppColors.colorScheme.primary
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "￥$price",
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ServiceItemCard(service: ServiceItem) {
    AppCard(
        modifier = Modifier.padding(horizontal = AppDimens.pagePadding),
        onClick = service.onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(8f)
            ) {
                if (service.icon != null) {
                    IconBox(icon = service.icon, size = 44.dp, iconSize = 22.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column {
                    Text(
                        text = service.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = service.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
            Text(
                text = "${if (service.icon == null) "￥" else ""}${service.price}",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.colorScheme.primary
            )
        }
    }
}

data class ServiceItem(
    val title: String,
    val description: String,
    val price: String,
    val icon: ImageVector?,
    val onClick: () -> Unit
)
