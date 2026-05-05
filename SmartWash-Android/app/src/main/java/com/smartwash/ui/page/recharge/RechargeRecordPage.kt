package com.smartwash.ui.page.recharge

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.R
import com.smartwash.network.vo.recharge.RechargeRecordVo
import com.smartwash.ui.common.AppCard
import com.smartwash.ui.common.EmptyState
import com.smartwash.ui.common.PageHeader
import com.smartwash.ui.theme.AlipayBlue
import com.smartwash.ui.theme.AppColors
import com.smartwash.ui.theme.AppDimens
import com.smartwash.ui.theme.WeChatGreen

@Composable
fun RechargeRecordPage(
    navController: NavController,
    viewModel: RechargeRecordViewModel = hiltViewModel(),
) {
    val records = viewModel.pagingFlow.collectAsLazyPagingItems()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.recharge_record),
                onBack = { navController.navigateUp() }
            )

            if (records.itemCount > 0) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.pagePadding)
                ) {
                    items(records.itemCount) { index ->
                        records[index]?.let { RechargeRecordCard(it) }
                    }

                    records.apply {
                        when {
                            loadState.refresh is LoadState.Loading -> {
                                item {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally),
                                        color = AppColors.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            loadState.append is LoadState.Loading -> {
                                item {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally),
                                        color = AppColors.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (records.loadState.refresh is LoadState.NotLoading) {
                EmptyState(
                    icon = Icons.Default.AccountBalanceWallet,
                    message = stringResource(R.string.no_recharge_records)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AppColors.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun RechargeRecordCard(record: RechargeRecordVo) {
    AppCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (record.rechargeType) {
                                "1" -> WeChatGreen.copy(alpha = 0.1f)
                                "2" -> AlipayBlue.copy(alpha = 0.1f)
                                else -> AppColors.colorScheme.primaryLight
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (record.rechargeType) {
                            "1" -> Icons.AutoMirrored.Default.Message
                            "2" -> Icons.Default.Payment
                            else -> Icons.Default.AccountBalanceWallet
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = when (record.rechargeType) {
                            "1" -> WeChatGreen
                            "2" -> AlipayBlue
                            else -> AppColors.colorScheme.primary
                        }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (record.rechargeType) {
                            "1" -> stringResource(R.string.weixin_pay)
                            "2" -> stringResource(R.string.alipay)
                            else -> stringResource(R.string.recharge)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.rechargeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.colorScheme.textSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.recharge_record_amount_format, String.format("%.2f", record.amount)),
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.colorScheme.primary
                )
            }
        }
    }
}
