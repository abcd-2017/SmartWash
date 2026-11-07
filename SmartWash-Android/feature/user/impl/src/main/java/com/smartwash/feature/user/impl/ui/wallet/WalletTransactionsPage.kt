package com.smartwash.feature.user.impl.ui.wallet

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.AppButtonVariant
import com.smartwash.common.ui.components.EmptyState
import com.smartwash.common.ui.components.LoadingDots
import com.smartwash.common.ui.components.LoadingState
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.feature.user.impl.R
import com.smartwash.feature.user.impl.network.vo.user.TransactionVo

/**
 * 钱包流水页四态（参照 :feature:payment 充值记录页范式）：
 * 列表 / 首载失败 / 空态 / 首载加载（失败不得停留骨架）。
 *
 * [Error] 携带「进入错误态那一刻」捕获的异常：Crossfade 淡出期间 live refresh 可能已随
 * retry() 变为 Loading，渲染分支只读状态对象、不触碰 live loadState，避免强转崩溃。
 */
internal sealed interface WalletListState {
    data object Content : WalletListState

    /** [error] 为进入错误态时捕获的异常（不读 live loadState） */
    data class Error(val error: Throwable) : WalletListState

    data object Empty : WalletListState
    data object Loading : WalletListState
}

/**
 * 由分页状态推导页面态（纯函数）：
 * 1. 有内容 → Content（refresh 失败但旧内容仍在，保持可读）
 * 2. 无内容且 refresh 为 Error → Error（携带异常）
 * 3. 首轮加载未完成 → Loading（骨架而非空态，防首帧闪空态）
 * 4. 无内容且 refresh 为 NotLoading → Empty
 * 5. 其余（Loading）→ Loading
 */
internal fun walletListStateOf(
    itemCount: Int,
    refresh: LoadState,
    refreshSession: Boolean = false,
): WalletListState = when {
    itemCount > 0 -> WalletListState.Content
    refresh is LoadState.Error -> WalletListState.Error(refresh.error)
    refreshSession -> WalletListState.Loading
    refresh is LoadState.NotLoading -> WalletListState.Empty
    else -> WalletListState.Loading
}

/**
 * 流水时间展示格式化（对齐充值记录页 2026-11-01 缺陷 8e 的防御性兼容）：
 * 已是空格分隔串则原样展示，ISO（含 T）则转为 yyyy-MM-dd HH:mm；解析失败原样返回。
 */
internal fun formatTransactionTime(raw: String): String {
    if (raw.isBlank()) return raw
    if (!raw.contains('T')) return raw
    return try {
        val parsed = java.time.LocalDateTime.parse(raw.take(19))
        parsed.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) {
        raw
    }
}

/**
 * 钱包流水页：充值/消费流水分页列表（入口=我的页账户卡「钱包流水」行）。
 *
 * 金额符号端上重算（对齐鸿蒙端决策）：服务端 payment 已下发负数，展示一律取绝对值，
 * 按 type 加 +/− 前缀，防双符号。
 */
@Composable
fun WalletTransactionsPage(
    navController: NavController,
    viewModel: WalletTransactionsViewModel = hiltViewModel(),
) {
    val transactions = viewModel.pagingFlow.collectAsLazyPagingItems()

    // 首轮加载门控：首帧 NotLoading+0 项不闪空态
    var loadStarted by rememberSaveable { mutableStateOf(false) }
    var everLoaded by rememberSaveable { mutableStateOf(false) }
    val refreshState = transactions.loadState.refresh
    LaunchedEffect(refreshState) {
        when (refreshState) {
            is LoadState.Loading -> {
                loadStarted = true
                everLoaded = true
            }
            is LoadState.NotLoading -> {
                if (transactions.itemCount > 0 || loadStarted) loadStarted = false
            }
            is LoadState.Error -> loadStarted = false
        }
    }

    val listState = walletListStateOf(
        itemCount = transactions.itemCount,
        refresh = transactions.loadState.refresh,
        refreshSession = !everLoaded,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.wallet_transactions),
                onBack = { navController.navigateUp() }
            )

            // 列表 ↔ 空态 ↔ 首载骨架：交叉淡变（reduced motion 由 motionSpec 降级为 180ms）
            Crossfade(
                targetState = listState,
                animationSpec = motionSpec(tween(CROSSFADE_ALT_MS)),
                label = "walletListState",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    WalletListState.Content -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
                        // 卡间距 16dp（规范 §2.3）
                        verticalArrangement = Arrangement.spacedBy(AppDimens.cardSpacing)
                    ) {
                        items(
                            count = transactions.itemCount,
                            // 契约无唯一 id：用全局索引做稳定 key（分页只 append，不产生重复）
                            key = { index -> "tx-$index" }
                        ) { index ->
                            val transaction = transactions[index]
                            if (transaction != null) {
                                TransactionCard(transaction)
                            }
                        }

                        transactions.apply {
                            // 尾部：append 加载中 → 转圈（reduced motion 降级静态三点灰）；
                            // append 失败 → 错误行点击重试
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
                                        onRetry = { transactions.retry() }
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    is WalletListState.Error -> EmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        message = state.error.message
                            ?: stringResource(R.string.wallet_load_failed),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            AppButton(
                                text = stringResource(R.string.user_action_retry),
                                onClick = { transactions.retry() },
                                variant = AppButtonVariant.SECONDARY,
                            )
                        }
                    )

                    WalletListState.Empty -> EmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        message = stringResource(R.string.no_wallet_transactions),
                        modifier = Modifier.fillMaxSize(),
                    )

                    WalletListState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * 分页尾部「加载更多失败」错误行 — 整行可点重试（对齐充值记录页 AppendErrorFooter）。
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
            text = stringResource(R.string.wallet_append_failed),
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
private fun TransactionCard(transaction: TransactionVo) {
    // DESIGN-SPEC v0.4 §3.1 唯一卡片画法：surface 白底 + 1px outline 描边 + level1 阴影
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
            // §3.2 容器规格（44dp / 12dp 圆角 / 24dp 图标）：充值 = 账户青，消费 = 洗护绿
            //（六色容器按语义取）；消费图标复用品牌件 ic_washing_machine，不新增 Icons.* 种类
            val isRecharge = transaction.type == TYPE_RECHARGE
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AppDimens.iconContainerRadius))
                    .background(
                        if (isRecharge) {
                            AppColors.colorScheme.iconContainerTeal
                        } else {
                            AppColors.colorScheme.iconContainerGreen
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isRecharge) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = AppColors.colorScheme.iconForegroundTeal
                    )
                } else {
                    Icon(
                        painter = painterResource(com.smartwash.common.ui.R.drawable.ic_washing_machine),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = AppColors.colorScheme.iconForegroundGreen
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(if (isRecharge) R.string.recharge else R.string.transaction_payment),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTransactionTime(transaction.transactionTime.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 金额：端上按 type 重算符号 + 绝对值展示（防服务端负数与端上前缀双符号）
            val amount = transaction.amount?.abs()
            Text(
                text = if (amount == null) {
                    // 金额缺失不造 0 元假数据，退化为描述占位
                    stringResource(if (isRecharge) R.string.recharge else R.string.transaction_payment)
                } else {
                    val formatted = String.format("%.2f", amount)
                    stringResource(
                        if (isRecharge) R.string.wallet_amount_in_format
                        else R.string.wallet_amount_out_format,
                        formatted
                    )
                },
                // titleLarge 22/28/600 对齐充值记录页金额档；tnum 等宽防数字抖动（规范 §2.2）
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = if (isRecharge) {
                    AppColors.colorScheme.primaryDark
                } else {
                    AppColors.colorScheme.textPrimary
                },
                // 金额绝不折行：极端字号下宁截断不折行
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

/** 流水类型常量（后端契约："recharge"/"payment"） */
private const val TYPE_RECHARGE = "recharge"
