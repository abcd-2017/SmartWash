package com.smartwash.ui.page.laundry

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.smartwash.R
import com.smartwash.ui.common.AppButton
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.utils.RequestState

@Composable
fun LaundryPage(
    navController: NavHostController,
    laundryViewModel: LaundryViewModel = hiltViewModel(),
) {
    val getLaundryItemState by laundryViewModel.getLaundryItemState.collectAsState()
    val laundryItems by laundryViewModel.laundryItems.collectAsState()
    var selectItemId by remember { mutableLongStateOf(-1) }
    var totalPrice by remember { mutableFloatStateOf(0f) }
    var showDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val reservationState by laundryViewModel.reservationState.collectAsState()
    val orderId by laundryViewModel.orderId.collectAsState()
    val userInfo by laundryViewModel.userInfo.collectAsState()

    LaunchedEffect(Unit) { laundryViewModel.getLaundryItem() }

    when (getLaundryItemState) {
        is RequestState.Error -> {
            Toast.makeText(context, context.getString((getLaundryItemState as RequestState.Error).messageResId), Toast.LENGTH_SHORT).show()
            laundryViewModel.resetGetLaundryItemState()
        }
        else -> {}
    }

    when (reservationState) {
        is RequestState.Success -> {
            LaunchedEffect(reservationState) {
                navController.navigate("${PageConstant.Payment.text}/${orderId}") {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        is RequestState.Error -> {
            Toast.makeText(context, context.getString((reservationState as RequestState.Error).messageResId), Toast.LENGTH_SHORT).show()
            laundryViewModel.resetReservationState()
        }
        else -> {}
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.confirm), color = AppColors.colorScheme.primary) }
            },
            text = { Text(stringResource(R.string.please_select_package)) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = stringResource(R.string.book_locker), onBack = { navController.navigateUp() })

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.pagePadding)
            ) {
                // 投递柜选择
                item {
                    Text(text = stringResource(R.string.delivery_locker), style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    AppCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppColors.colorScheme.primaryLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = AppColors.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = userInfo?.schoolVo?.schoolName ?: "",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = userInfo?.schoolVo?.location ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(0.6f),
                                        overflow = TextOverflow.Ellipsis,
                                        color = AppColors.colorScheme.textSecondary
                                    )
                                }
                            }
                            TextButton(onClick = { }) {
                                Text(stringResource(R.string.change), color = AppColors.colorScheme.primary)
                            }
                        }
                    }
                }

                // 套餐选择
                item {
                    Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))
                    Text(text = stringResource(R.string.laundry_package), style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(laundryItems) { item ->
                    LaundryTypeItem(
                        title = item.itemName,
                        description = item.description,
                        isSelected = selectItemId == item.itemId,
                        price = stringResource(R.string.currency_format, item.basePrice.toString())
                    ) {
                        selectItemId = item.itemId
                        totalPrice = item.basePrice
                    }
                    Spacer(modifier = Modifier.height(AppDimens.cardSpacing))
                }
            }

            // 底部栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Column {
                    HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.divider)
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.estimated_cost),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.colorScheme.textSecondary
                            )
                            Text(
                                text = stringResource(R.string.currency_format, totalPrice.toString()),
                                style = MaterialTheme.typography.headlineLarge,
                                color = AppColors.colorScheme.primary
                            )
                        }
                        AppButton(
                            text = stringResource(R.string.confirm_booking),
                            onClick = {
                                if (selectItemId == -1L) showDialog = true
                                else laundryViewModel.reservationLaundry(selectItemId, totalPrice)
                            },
                            modifier = Modifier.width(160.dp),
                            loading = reservationState is RequestState.Loading
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaundryTypeItem(
    title: String,
    description: String,
    isSelected: Boolean,
    price: String,
    cardClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { cardClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AppColors.colorScheme.primaryLight else AppColors.colorScheme.surface,
        shadowElevation = 0.dp,
        border = if (isSelected) BorderStroke(1.5.dp, AppColors.colorScheme.primary) else null
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
                // 选中指示器
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) AppColors.colorScheme.primary.copy(alpha = 0.15f)
                            else AppColors.colorScheme.divider
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(AppColors.colorScheme.textTertiary)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
            }
            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.End) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.colorScheme.primary
                )
            }
        }
    }
}
