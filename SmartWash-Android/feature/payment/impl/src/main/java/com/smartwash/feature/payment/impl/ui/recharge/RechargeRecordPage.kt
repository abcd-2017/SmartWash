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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
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
import com.smartwash.common.utils.pressable

/**
 * 充值记录页四态：列表 / 首载失败 / 空态 / 首载加载（回归 2026-11-06 A6：失败不得停留骨架）。
 *
 * [Error] 携带「进入错误态那一刻」捕获的异常（C1 回归 2026-11-06）：
 * 首刷失败渲染期间 refresh 可能已随 `retry()` 变为 [LoadState.Loading]，而 Crossfade
 * 淡出期间仍会组合旧的 Error 内容——若渲染分支直接强转 live `loadState.refresh`
 * 会抛 `ClassCastException: LoadState$Loading cannot be cast to LoadState$Error`
 * （RechargeRecordPage.kt 原 169 行）。异常随状态对象一起保存后，渲染只读状态对象、
 * 不再触碰 live loadState，重试后不得再进 Error 渲染分支。
 */
internal sealed interface RecordListState {
    data object Content : RecordListState

    /** [error] 为进入错误态时捕获的异常（不读 live loadState，见类注释 C1 回归） */
    data class Error(val error: Throwable) : RecordListState

    data object Empty : RecordListState
    data object Loading : RecordListState
}

/**
 * 由分页状态推导页面态（纯函数，JVM 单测 [RechargeRecordListStateTest] 覆盖）：
 * 1. 有内容 → [RecordListState.Content]（refresh 失败但旧内容仍在，保持可读）
 * 2. 无内容且 refresh 为 [LoadState.Error] → [RecordListState.Error]（携带异常）
 * 3. 刷新会话中且无内容 → [RecordListState.Loading]（骨架而非空态，2026-11-06 闪空态回归）
 * 4. 无内容且 refresh 为 [LoadState.NotLoading] → [RecordListState.Empty]
 * 5. 其余（[LoadState.Loading]）→ [RecordListState.Loading]
 *
 * [refreshSession] 为真表示「列表已被清空、新一轮加载尚未开始」：此时即便 live refresh
 * 仍是上一轮的 NotLoading，也不得判空态（否则渲染一帧空态）。错误分支前置于会话分支，
 * 保证刷新失败时错误态不被骨架掩盖。本页无刷新/重建路径（ViewModel 无 refresh、
 * 无 PullToRefreshBox、无 CHANGED_KEY），传入的是「首轮加载未完成」，属同款防御；
 * 将来若接入刷新，把会话位置真即可。
 *
 * 类型安全：一律 `is` 判断，不做任何强转；调用方（含 Crossfade 淡出中的旧内容）
 * 只面对状态对象，不会因 live loadState 变化而崩溃。
 */
internal fun recordListStateOf(
    itemCount: Int,
    refresh: LoadState,
    refreshSession: Boolean = false,
): RecordListState = when {
    itemCount > 0 -> RecordListState.Content
    refresh is LoadState.Error -> RecordListState.Error(refresh.error)
    refreshSession -> RecordListState.Loading
    refresh is LoadState.NotLoading -> RecordListState.Empty
    else -> RecordListState.Loading
}

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

    // 刷新会话门控（2026-11-06 闪空态回归，同款防御）：本页无刷新/重建路径
    // （ViewModel 无 refresh、页面无 PullToRefreshBox、无 CHANGED_KEY），
    // 故会话退化为「首轮加载」——仍加 everLoaded 门控，保证首帧 NotLoading+0 项不闪空态。
    var loadStarted by rememberSaveable { mutableStateOf(false) }
    var everLoaded by rememberSaveable { mutableStateOf(false) }
    val refreshState = records.loadState.refresh
    LaunchedEffect(refreshState) {
        when (refreshState) {
            is LoadState.Loading -> {
                loadStarted = true
                everLoaded = true
            }
            is LoadState.NotLoading -> {
                if (records.itemCount > 0 || loadStarted) loadStarted = false
            }
            is LoadState.Error -> loadStarted = false
        }
    }

    // refresh 中（有旧内容）保持列表可读；无内容时区分「失败 → 错误卡+重试」与「空态」，
    // 其余（NotLoading 空数据 / Loading / Error 之外）才落骨架——断网首刷（LoadResult.Error）
    // 不能停在无限骨架（回归 2026-11-06 A6）。状态推导抽为纯函数（C1：类型安全 + 可单测）
    val listState = recordListStateOf(
        itemCount = records.itemCount,
        refresh = records.loadState.refresh,
        refreshSession = !everLoaded,
    )

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
                            // 尾部：append 加载中 → 转圈（reduced motion 降级静态三点灰）；append 失败
                            // → 错误行点击重试（Paging 停在 Error 时必须给出恢复入口，A6）
                            when (val appendState = loadState.append) {
                                is LoadState.Loading -> item(key = "append_loading_footer") {
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
                                is LoadState.Error -> item(key = "append_error_footer") {
                                    AppendErrorFooter(
                                        message = appendState.error.message,
                                        onRetry = { records.retry() }
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    is RecordListState.Error -> {
                        // 首刷失败：错误说明 + 重试（样式对齐工作台断网错误态，EmptyState 滚筒母题）
                        // 具体文案优先取 NetworkException.message（ResponseInterceptor 已转译），
                        // 异常无 message（理论不可能）时兜底通用文案。
                        // C1：异常来自状态对象（进入 Error 态时捕获），不再强转 live loadState
                        // ——retry 后 refresh 变 Loading 时 Crossfade 仍在淡出旧 Error 内容，不崩
                        EmptyState(
                            icon = Icons.Default.AccountBalanceWallet,
                            message = state.error.message
                                ?: stringResource(R.string.record_load_failed),
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                AppButton(
                                    small = true,
                                    text = stringResource(R.string.record_action_retry),
                                    onClick = { records.retry() },
                                    variant = AppButtonVariant.SECONDARY,
                                )
                            }
                        )
                    }

                    RecordListState.Empty -> EmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        message = stringResource(R.string.no_recharge_records),
                        modifier = Modifier.fillMaxSize(),
                    )

                    RecordListState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * 分页尾部「加载更多失败」错误行 — 整行可点重试（A6：append Error 不能静默停在原地）。
 * 规范 §7：自绘 pressable 无 ripple，热区 ≥44dp。
 */
@Composable
private fun AppendErrorFooter(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .pressable(onClick = onRetry),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.record_append_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.dangerInk,
        )
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.textSecondary,
            )
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
                // P16 裁定：非微信/支付宝记录（余额充值）随画板换钱包图标
                // account_balance_wallet（Material 内置线性变体），tint primary，弃 ic_wechat 占位
                if (record.rechargeType == "1" || record.rechargeType == "2") {
                    Icon(
                        painter = painterResource(
                            when (record.rechargeType) {
                                "1" -> R.drawable.ic_wechat
                                else -> R.drawable.ic_alipay
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        // 官方品牌色（2026-11-01 缺陷 8a，红线豁免：品牌色不得换）
                        tint = if (record.rechargeType == "1") WeChatBrandGreen else AlipayBrandBlue
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        // 画板取值 #2D9B6A = primary（浅）/提亮档（深）
                        tint = AppColors.colorScheme.primary
                    )
                }
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
            // 金额（P16 裁决 2：以画板为准 22/600 = titleLarge 档；此前 2026-11-01 降档 18 的
            // 用户反馈已被 2026-11-06 裁决记录推翻）
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.recharge_record_amount_format, String.format("%.2f", record.amount)),
                // titleLarge 22/28/600；金额开 tnum 等宽防数字抖动（规范 §2.2 硬规则）
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = AppColors.colorScheme.primaryDark,
                // 2026-11-01：金额绝不换行——Row 内非 weight 子项按整行宽约束测量，
                // 正常金额远小于可用宽；此处再显式限单行，作为极端字号下的兜底（宁截断不折行）
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
