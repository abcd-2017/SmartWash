package com.smartwash.feature.payment.impl.ui.recharge

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.impl.network.vo.recharge.RechargeRecordVo
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.motionSpec

/** 充值记录页三态：列表 / 空态 / 首载加载 */
private enum class RecordListState { Content, Empty, Loading }

@Composable
fun RechargeRecordPage(
    navController: NavController,
    viewModel: RechargeRecordViewModel = hiltViewModel(),
) {
    val records = viewModel.pagingFlow.collectAsLazyPagingItems()

    // refresh 中（有旧内容）保持列表可读；仅无内容且非空态时走首载骨架
    val listState = when {
        records.itemCount > 0 -> RecordListState.Content
        records.loadState.refresh is LoadState.NotLoading -> RecordListState.Empty
        else -> RecordListState.Loading
    }

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

            // 列表 ↔ 空态 ↔ 首载骨架：交叉淡变（reduced motion 由 motionSpec 降级为 180ms）
            Crossfade(
                targetState = listState,
                animationSpec = motionSpec(tween(CROSSFADE_ALT_MS)),
                label = "rechargeRecordState",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    RecordListState.Content -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AppDimens.pagePadding)
                    ) {
                        items(
                            count = records.itemCount,
                            key = { index -> records.peek(index)?.recordId ?: "pending-$index" }
                        ) { index ->
                            val record = records[index]
                            if (record != null) {
                                Column {
                                    RechargeRecordCard(record)
                                    // 画板实测：卡间 12 内含 1px 分组分隔线（hairline），最后一项不带
                                    if (index < records.itemCount - 1) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(AppDimens.spaceSm),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(AppColors.colorScheme.hairline)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        records.apply {
                            // 尾部转圈仅保留给 append；refresh 不追加尾部指示（旧内容保持可读）。
                            // reduced motion 降级为静态三点灰（§8：取消无限旋转，保留"在忙"可读性，
                            // 与订单页分页 footer 同规格）
                            if (loadState.append is LoadState.Loading) {
                                item(key = "append_loading_footer") {
                                    if (LocalReduceMotion.current) {
                                        LoadingDots(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        )
                                    } else {
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

                    RecordListState.Empty -> EmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        message = stringResource(R.string.no_recharge_records),
                        modifier = Modifier.fillMaxSize()
                    )

                    RecordListState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun RechargeRecordCard(record: RechargeRecordVo) {
    // DESIGN-SPEC v0.4 §3.1 唯一卡片画法：surface 白底 + 1px outline 描边 +
    // level1(1dp) 阴影 ≈ CSS 0 2px 12px rgba(20,40,30,.05)；内边距 20（旧实现无阴影属禁止项）
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        shadowElevation = AppElevation.level1,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                    .background(
                        when (record.rechargeType) {
                            // 画板实测：微信/余额 #F0FBF4·#17322B（= primaryContainer 双主题值）、
                            // 支付宝 #EEF2FB·#1F2637（= iconContainerBlue）
                            "1" -> MaterialTheme.colorScheme.primaryContainer
                            "2" -> AppColors.colorScheme.iconContainerBlue
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (record.rechargeType) {
                        "1" -> Icons.AutoMirrored.Filled.Chat
                        "2" -> Icons.Default.Payment
                        else -> Icons.Default.AccountBalanceWallet
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when (record.rechargeType) {
                        "1" -> AppColors.colorScheme.iconForegroundGreen
                        "2" -> AppColors.colorScheme.iconForegroundBlue
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.rechargeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary
                )
            }
            Text(
                text = stringResource(R.string.recharge_record_amount_format, String.format("%.2f", record.amount)),
                // 画板实测：22/28/600（= titleLarge 档），金额启用 tabular 数字防跳动
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.primaryDark
            )
        }
    }
}
