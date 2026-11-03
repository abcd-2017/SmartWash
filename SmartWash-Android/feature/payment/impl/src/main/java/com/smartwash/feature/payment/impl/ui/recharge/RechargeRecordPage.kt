package com.smartwash.feature.payment.impl.ui.recharge

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.impl.network.vo.recharge.RechargeRecordVo
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.motionSpec

/** 充值记录页三态：列表 / 空态 / 首载加载 */
private enum class RecordListState { Content, Empty, Loading }

/**
 * 充值时间展示格式化（2026-11-01 缺陷 8e）：
 * 后端已补 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`（原 LocalDateTime 默认 ISO 序列化）；
 * 端上防御性兼容两种入参——已是空格分隔串则原样展示，ISO（含 T）则转为 `yyyy-MM-dd HH:mm`。
 * 解析失败原样返回，不吞数据。
 */
internal fun formatRechargeTime(raw: String): String {
    if (raw.isBlank()) return raw
    if (!raw.contains('T')) return raw
    return try {
        val parsed = java.time.LocalDateTime.parse(raw.take(19))
        parsed.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) {
        raw
    }
}

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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
                        // 卡间距 16dp（规范 §2.3）；卡间 1dp 分隔线属「发丝线用在卡外」违规（§1.3），已移除
                        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                    ) {
                        items(
                            count = records.itemCount,
                            key = { index -> records.peek(index)?.recordId ?: "pending-$index" }
                        ) { index ->
                            val record = records[index]
                            if (record != null) {
                                RechargeRecordCard(record)
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
        modifier = Modifier.fillMaxWidth().softCardShadow(),
        shape = RoundedCornerShape(AppDimens.radiusLg),
        color = AppColors.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // §3.2 容器规格（44dp / 12dp 圆角 / 24dp 图标，六色浅底 + 对应前景）：
            // 品牌件是 VectorDrawable 且本仓库 compose 版本无 ImageVector.vectorResource，
            // 无法走 IconBox 的 ImageVector 参数——按同规格自绘等价容器；
            // 官方品牌色 tint 属 §10 红线豁免
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
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        when (record.rechargeType) {
                            "1" -> R.drawable.ic_wechat
                            "2" -> R.drawable.ic_alipay
                            else -> R.drawable.ic_wechat // 余额充值历史数据无品牌语义，占位同微信
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when (record.rechargeType) {
                        // 官方品牌色（2026-11-01 缺陷 8a，红线豁免：品牌色不得换）
                        "1" -> WeChatBrandGreen
                        "2" -> AlipayBrandBlue
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
                    // 时间格式化（2026-11-01 缺陷 8e）：后端历史上以 ISO 串直出（LocalDateTime
                    // 缺 @JsonFormat，后端已补 yyyy-MM-dd HH:mm:ss），端上做防御性兼容转换并限单行，
                    // 消除「2026-10-31T21:33:12」原样展示与挤压折行
                    text = formatRechargeTime(record.rechargeTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 金额（2026-11-01 用户反馈：原 titleLarge 22sp 与左侧 14sp 类型字差 8sp，观感失衡）
            // → 金额中字档 18sp（v0.4 AmountMedium，列表内价格的标准档），与类型字 14sp 保持 4sp 级差
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.recharge_record_amount_format, String.format("%.2f", record.amount)),
                // AppTextStyles.AmountMedium 自带 tnum（等宽防数字抖动）
                style = AppTextStyles.AmountMedium,
                color = AppColors.colorScheme.primaryDark,
                // 2026-11-01：金额绝不换行——Row 内非 weight 子项按整行宽约束测量，
                // 正常金额远小于可用宽；此处再显式限单行，作为极端字号下的兜底（宁截断不折行）
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
