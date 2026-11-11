package com.smartwash.feature.payment.impl.ui.recharge

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartwash.feature.payment.impl.R
import com.smartwash.feature.payment.api.PaymentRoute
import com.smartwash.common.ui.components.AppButton
import com.smartwash.common.ui.components.softCardShadow
import com.smartwash.common.ui.components.AppConfirmDialog
import com.smartwash.common.ui.components.PageHeader
import com.smartwash.common.ui.components.SelectionDot
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppElevation
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.ui.theme.IconBox
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.common.utils.DURATION_MICRO_MS
import com.smartwash.common.utils.enterTween
import com.smartwash.common.utils.exitTween
import com.smartwash.common.utils.pressable
import kotlinx.coroutines.launch
import com.smartwash.common.utils.rememberDebouncedClick
import com.smartwash.common.utils.selectedColor
import java.util.Locale


@Composable
fun RechargePage(
    navController: NavController, rechargeViewModel: RechargeViewModel = hiltViewModel()
) {
    // 默认选中第一档（2026-11-01 缺陷 8c：原初始全空导致页面无任何高亮项）
    var selectedAmount by remember { mutableStateOf<Float?>(10f) }
    var customAmount by remember { mutableStateOf("") }
    // 默认选中微信支付（设计稿 P15：微信支付行有选中勾；原 null 导致进入页面无选中项，
    // 且未选就提交会触发「请选择支付方式」报错）
    var selectedPaymentMethod by remember { mutableStateOf<PaymentMethod?>(PaymentMethod.WECHAT) }
    var isCustomAmountSelected by remember { mutableStateOf(false) }
    var showPayError by remember { mutableStateOf(false) }
    // 金额校验失败原因（null 为通过）：文案随原因细分，见 RechargeAmountError
    var amountError by remember { mutableStateOf<RechargeAmountError?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // 列表状态 hoist（2026-11-05 修复②）：校验失败时可编程滚动到出错区块——
    // 此前错误文案渲染在视口外（支付方式区/金额区），用户点「立即充值」看似无反应
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 焦点管理（2026-11-01 修复）：点档位格收起键盘、点「自定义」格聚焦输入框，
    // 消除"两边获取焦点状态竞态"（此前输入框聚焦时点档位，键盘不收、焦点状态互抢）
    val focusManager = LocalFocusManager.current
    val customInputFocusRequester = remember { FocusRequester() }

    val presetAmounts = listOf(10f, 20f, 50f, 100f, 200f)
    val rechargeState by rechargeViewModel.rechargeState.collectAsState()
    // 画板实测：页面顶部直出账户余额（无卡片），数据 VM 已有 balance 流
    val balance by rechargeViewModel.balance.collectAsState()
    val context = LocalContext.current
    // 充值记录入口的读屏标签（语义挂在 48dp 热区盒上，见 PageHeader actions）
    val recordDesc = stringResource(R.string.recharge_record)

    LaunchedEffect(rechargeState) {
        when (rechargeState) {
            is RequestState.Success -> {
                Toast.makeText(context, context.getString(R.string.recharge_success), Toast.LENGTH_SHORT).show()
                navController.navigateUp()
                rechargeViewModel.setRechargeStateIdle()
            }
            is RequestState.Error -> {
                Toast.makeText(context, (rechargeState as RequestState.Error).getMessage(context), Toast.LENGTH_SHORT).show()
                rechargeViewModel.setRechargeStateIdle()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.colorScheme.background)
            // 设计规范 §4：表单页键盘弹出时输入框保持可见（imePadding）——键盘弹出时整页
            // 上收，支付方式区可经 LazyColumn 滚动查看，底部「立即充值」按钮不被键盘遮挡；
            // 底部按钮的 navigationBarsPadding 与本 inset 经 Compose 消费链自动协调（弹出时不双计）
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.recharge),
                onBack = { navController.navigateUp() },
                actions = {
                    // §7 禁 Material 交互组件：M3 IconButton 自带 state layer 涟漪（颜色/形状不受令牌控制），
                    // 改自绘按压热区。pressable 内置 indication = null + pressScale(0.97f)，与 PageHeader 返回键一致
                    Box(
                        // 触控热区 48dp（与 PageHeader 返回键同规范 §3.2）；
                        // semantics 补 contentDescription——IconBox 内部图标为装饰，读屏标签挂在热区盒上
                        modifier = Modifier
                            .size(48.dp)
                            .pressable(onClick = rememberDebouncedClick { navController.navigate(PaymentRoute.RechargeRecord) })
                            .semantics { contentDescription = recordDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        // §3.2 功能图标必须进六色浅底容器（账户/记录语义 → 青），禁止裸图标着色
                        IconBox(
                            icon = Icons.Default.History,
                            size = 36.dp,
                            iconSize = 20.dp,
                            containerColor = AppColors.colorScheme.iconContainerTeal,
                            iconTint = AppColors.colorScheme.iconForegroundTeal
                        )
                    }
                }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = AppDimens.pagePadding),
                // 画板实测区块间距 24（余额→充值金额标题、输入框→支付方式）
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXl)
            ) {
                // 账户余额（画板实测：直出页面底，无卡片；12/400 + DataLarge primaryDark，≥18sp 绿字豁免）
                item(key = "balance") {
                    Column {
                        Text(
                            text = stringResource(R.string.account_balance_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.colorScheme.textSecondary
                        )
                        Text(
                            text = stringResource(R.string.currency_format, String.format(Locale.CHINA, "%.2f", balance ?: 0f)),
                            style = AppTextStyles.DataLarge,
                            color = AppColors.colorScheme.primaryDark
                        )
                    }
                }

                // 充值金额（设计稿 .amount3：3 列网格 6 格 = 5 档 + 自定义；赠额随档入格，
                // 格 = surfaceVariant 填充无边框、选中 primarySoft 实底，见 AmountGridCell）
                item(key = "amount") {
                    Column {
                        Text(
                            text = stringResource(R.string.recharge_amount),
                            style = AppTextStyles.SectionTitle,
                            color = AppColors.colorScheme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().softCardShadow(),
                            shape = RoundedCornerShape(AppDimens.radiusLg),
                            color = AppColors.colorScheme.surface,
                            border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                                // 3 列 × 2 行（设计稿 grid-template-columns:repeat(3,1fr)；gap 10）
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        presetAmounts.take(3).forEach { amount ->
                                            AmountGridCell(
                                                amount = amount,
                                                bonus = RechargeAmountValidator.PRESET_BONUSES[amount] ?: 0f,
                                                isSelected = selectedAmount == amount && !isCustomAmountSelected,
                                                onClick = {
                                                    selectedAmount = amount
                                                    isCustomAmountSelected = false
                                                    amountError = null
                                                    focusManager.clearFocus()
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        presetAmounts.drop(3).forEach { amount ->
                                            AmountGridCell(
                                                amount = amount,
                                                bonus = RechargeAmountValidator.PRESET_BONUSES[amount] ?: 0f,
                                                isSelected = selectedAmount == amount && !isCustomAmountSelected,
                                                onClick = {
                                                    selectedAmount = amount
                                                    isCustomAmountSelected = false
                                                    amountError = null
                                                    focusManager.clearFocus()
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        // 第 6 格「自定义」：选中即聚焦下方输入框（消除与档位格的焦点竞态）
                                        CustomAmountCell(
                                            isSelected = isCustomAmountSelected,
                                            onClick = {
                                                isCustomAmountSelected = true
                                                amountError = null
                                                customInputFocusRequester.requestFocus()
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                // 动态到账说明（替代原静态「充值多少送多少」干扰行）：按当前选中档展示
                                Spacer(modifier = Modifier.height(12.dp))
                                RechargeSummaryLine(selectedAmount, isCustomAmountSelected)
                                // 自定义金额输入（选中「自定义」格后聚焦可输入）
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.custom_amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.colorScheme.textSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CustomAmountInput(
                                    value = customAmount,
                                    isSelected = isCustomAmountSelected,
                                    focusRequester = customInputFocusRequester,
                                    // 选中态与真实焦点对齐（2026-11-04 缺陷：直接点输入框时键盘弹出但
                                    // 高亮不动，视觉上「焦点没切过去」）。失焦且未输入内容才交还选中态，
                                    // 已输入的自定义金额在点档位格时由 onClick 显式清选中
                                    onFocusChange = { focused ->
                                        if (focused) {
                                            isCustomAmountSelected = true
                                            amountError = null
                                        } else if (customAmount.isEmpty()) {
                                            isCustomAmountSelected = false
                                        }
                                    },
                                    onValueChange = { value ->
                                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                            customAmount = value
                                            if (value.isNotEmpty()) {
                                                selectedAmount = value.toFloatOrNull()
                                                isCustomAmountSelected = true
                                            } else {
                                                // 清空即清选中态（2026-11-01 缺陷 8c）
                                                selectedAmount = null
                                                isCustomAmountSelected = false
                                            }
                                            amountError = null
                                        }
                                    }
                                )
                            }
                        }
                    // 错误提示 0ms 出现/消失导致布局跳变 → 淡入 + 纵向展开/收起
                    AnimatedVisibility(
                        visible = amountError != null,
                        enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                            expandVertically(enterTween(DURATION_MICRO_MS)),
                        exit = fadeOut(exitTween(140)) + shrinkVertically(exitTween(140)),
                    ) {
                        amountError?.let { error ->
                            Text(
                                text = resolveAmountErrorText(error),
                                color = AppColors.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    }
                }

                // 支付方式
                item(key = "payment") {
                    Text(
                        text = stringResource(R.string.payment_method),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.colorScheme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().softCardShadow(),
                        shape = RoundedCornerShape(AppDimens.radiusLg),
                        color = AppColors.colorScheme.surface,
                        border = BorderStroke(1.dp, AppColors.colorScheme.outline)
                    ) {
                        Column {
                            PaymentMethodCard(
                                method = PaymentMethod.WECHAT,
                                isSelected = selectedPaymentMethod == PaymentMethod.WECHAT,
                                onClick = { selectedPaymentMethod = PaymentMethod.WECHAT; showPayError = false }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = AppColors.colorScheme.hairline,
                                // 画板实测：分隔线与文字列对齐（x=92 = 卡内 20 + 图标盒 36 + 间距 12）
                                modifier = Modifier.padding(start = 68.dp, end = 20.dp)
                            )
                            PaymentMethodCard(
                                method = PaymentMethod.ALIPAY,
                                isSelected = selectedPaymentMethod == PaymentMethod.ALIPAY,
                                onClick = { selectedPaymentMethod = PaymentMethod.ALIPAY; showPayError = false }
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = showPayError,
                        enter = fadeIn(enterTween(DURATION_MICRO_MS)) +
                            expandVertically(enterTween(DURATION_MICRO_MS)),
                        exit = fadeOut(exitTween(140)) + shrinkVertically(exitTween(140)),
                    ) {
                        Text(
                            text = stringResource(R.string.select_payment_method),
                            color = AppColors.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // 底部按钮（固定在底部，有顶部分隔线）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.colorScheme.surface)
            ) {
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.colorScheme.outline)
                AppButton(
                    text = if (selectedAmount != null) stringResource(R.string.recharge_now_format, String.format(Locale.CHINA, "%.2f", selectedAmount)) else stringResource(R.string.recharge_now),
                    // 校验前置到按钮点击（2026-11-05 修复②）：任一校验不过 → 置错误态并自动滚到
                    // 出错区块（错误文案原渲染在视口外，用户「点了没反应」）；全部通过才弹确认框
                    onClick = {
                        val amountResult = RechargeAmountValidator.resolve(
                            isCustomAmountSelected = isCustomAmountSelected,
                            presetAmount = selectedAmount,
                            customAmountRaw = customAmount
                        )
                        val payMethodMissing = selectedPaymentMethod == null
                        showPayError = payMethodMissing
                        amountError = amountResult.error
                        when {
                            amountResult.error != null -> {
                                scope.launch { listState.animateScrollToItem(ITEM_INDEX_AMOUNT) }
                            }
                            payMethodMissing -> {
                                scope.launch { listState.animateScrollToItem(ITEM_INDEX_PAYMENT) }
                            }
                            else -> showDialog = true
                        }
                        if (amountResult.error != null || payMethodMissing) {
                            // 业务拒绝（客户端校验不过）：留痕拒绝原因与尝试金额，便于对齐「为什么没弹确认框」
                            AppLogger.warn(
                                TAG,
                                "充值提交被客户端校验拒绝: amountError=${amountResult.error} " +
                                    "支付方式未选=$payMethodMissing 预设金额=$selectedAmount 自定义输入=$customAmount"
                            )
                        }
                    },
                    loading = rechargeState is RequestState.Loading,
                    // 画板实测：左右 24、上 16、下 20（真机叠加手势条避让）；原 padding(16, 24)
                    // 是 Compose 双参重载 = 水平 16 / 垂直 24，与画板正好相反（§3.7 已警示该坑）
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp)
                )
            }
        }
    }

    if (showDialog) {
        AppConfirmDialog(
            title = stringResource(R.string.tip),
            message = stringResource(R.string.confirm_recharge_question),
            onConfirm = {
                showDialog = false
                // 金额与支付方式已在「立即充值」onClick 校验通过（弹窗只在全部通过后弹出），此处直接提交。
                // 传「到账金额」= 支付金额 + 赠送（仅预设档，2026-11-05 修复①）：后端 userRecharge 的
                // amount 即入账金额，此前误传支付金额导致赠送从未到账（选 100 送 8 实际只 +100）
                val amountResult = RechargeAmountValidator.resolve(
                    isCustomAmountSelected = isCustomAmountSelected,
                    presetAmount = selectedAmount,
                    customAmountRaw = customAmount
                )
                val creditAmount = RechargeAmountValidator.creditAmount(
                    amountResult.amount!!,
                    isCustomAmountSelected
                )
                AppLogger.info(
                    TAG,
                    "确认充值: 支付金额=${amountResult.amount} 到账金额=$creditAmount（含预设档赠送） " +
                        "支付方式=${selectedPaymentMethod!!.payType}"
                )
                rechargeViewModel.userRecharge(creditAmount, selectedPaymentMethod!!.payType)
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 金额档位格（P15 裁定，设计稿 .amount3 单元格）：白卡上的 surfaceVariant 填充块
 * 无边框 / r14 / 格高 64；选中态 = primarySoft 浅绿实底、无描边（深色 = DarkPrimarySoft
 * 深绿实底 + 提亮字，令牌成对）。赠额随档常驻（<em> 口径：12sp/500/ongoingInk），
 * 不只在选中时出现——让用户未选中也能看到各档赠额。
 */
@Composable
private fun AmountGridCell(
    amount: Float,
    bonus: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primarySoft else AppColors.colorScheme.surfaceVariant
    )
    val textColor = selectedColor(
        if (isSelected) AppColors.colorScheme.textGreenOnTint else AppColors.colorScheme.textPrimary
    )
    Surface(
        modifier = modifier
            .height(64.dp)
            .pressable(onClick = onClick, debounce = false),
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.currency_format, "${amount.toInt()}"),
                style = AppTextStyles.AmountMedium,
                color = textColor
            )
            if (bonus > 0f) {
                Text(
                    text = stringResource(R.string.recharge_bonus_format, "${bonus.toInt()}"),
                    // v0.4 字阶无 11sp 档（原 11sp 属作废夹档）→ captionStrong 12sp/500；
                    // 赠额是金额，同句开 tnum 防数字抖动（规范 §2.2 硬规则）
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = AppColors.colorScheme.ongoingInk
                )
            }
        }
    }
}

/**
 * 「自定义」格（设计稿 .amount3 第 6 格）：与档位格同尺寸同选中态语言（surfaceVariant
 * 填充无边框 / 选中 primarySoft 实底 / 格高 64），文案 12sp/500。
 */
@Composable
private fun CustomAmountCell(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primarySoft else AppColors.colorScheme.surfaceVariant
    )
    val textColor = selectedColor(
        if (isSelected) AppColors.colorScheme.textGreenOnTint else AppColors.colorScheme.textSecondary
    )
    Surface(
        modifier = modifier
            .height(64.dp)
            .pressable(onClick = onClick, debounce = false),
        shape = RoundedCornerShape(AppDimens.buttonRadius),
        color = bgColor
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.custom_amount_short),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * 动态到账说明（设计稿 .amount3 下方 .s13 行）：按当前选中档展示
 * 「充值 ¥50.00 · 到账 ¥52.00（含赠送 ¥2.00）」，到账金额加粗 + textGreen（2026-11-06 评审 R17）。
 * 替代原静态「充值多少送多少」提示行——静态罗列三档赠额既干扰视觉又无法回应当前选择。
 */
@Composable
private fun RechargeSummaryLine(selectedAmount: Float?, isCustom: Boolean) {
    val amount = selectedAmount ?: 0f
    if (amount <= 0f) return
    val bonus = if (isCustom) 0f else RechargeAmountValidator.PRESET_BONUSES[selectedAmount] ?: 0f
    val total = amount + bonus
    Text(
        text = buildAnnotatedString {
            append(
                stringResource(R.string.recharge_summary_prefix, String.format(Locale.CHINA, "%.2f", amount))
            )
            withStyle(
                SpanStyle(fontWeight = FontWeight.Bold, color = AppColors.colorScheme.textGreen)
            ) {
                append(stringResource(R.string.currency_format, String.format(Locale.CHINA, "%.2f", total)))
            }
            if (bonus > 0f) {
                append(
                    stringResource(R.string.recharge_summary_bonus, String.format(Locale.CHINA, "%.2f", bonus))
                )
            }
        },
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        color = AppColors.colorScheme.textSecondary
    )
}

/**
 * 自定义金额输入（画板实测：独立 48 高输入框 r12、占位 16/400 textSecondary；
 * 输入正文 16sp 符合 §3.7 输入控件标准）。
 * isSelected（2026-11-01 缺陷 8c）：选中自选金额时输入框同步高亮
 * （primarySoft 底 + primary 描边），与档位胶囊互斥高亮，页面始终有明确选中项。
 */
@Composable
private fun CustomAmountInput(
    value: String,
    isSelected: Boolean,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bgColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primarySoft else AppColors.colorScheme.surfaceVariant
    )
    val borderColor = selectedColor(
        if (isSelected) AppColors.colorScheme.primary else AppColors.colorScheme.surfaceVariant
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        // 输入档圆角 14dp（规范 §2.3 inputRadius；原 radiusMd 12dp 与输入框档不符）
        shape = RoundedCornerShape(AppDimens.inputRadius),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { onFocusChange(it.isFocused) }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            placeholder = {
                Text(
                    stringResource(R.string.custom_amount_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.colorScheme.textSecondary
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true
        )
    }
}

@Composable
private fun PaymentMethodCard(
    method: PaymentMethod, isSelected: Boolean, onClick: () -> Unit
) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = onClick, debounce = false)
                // P15 裁定：画板支付行上下内边距 12（行高 60，图标盒 36 居中），原 15 偏松
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // 官方品牌图标（2026-11-01 缺陷 8a）：原微信=Message/支付宝=Payment 通用 Material 图标
        // 违反规范 §4.4「第三方品牌图标不得换成通用组件」红线，替换为官方矢量（Simple Icons，CC0）；
        // 着色用官方品牌色（红线豁免：品牌色不得换）。品牌件是 VectorDrawable 而本仓库 compose 版本
        // 无 ImageVector.vectorResource，无法走 IconBox 的 ImageVector 参数——此处按 IconBox 同规格
        // （36dp 容器 / 12dp 圆角 / 20dp 图标 / 六色浅底 + 对应前景）自绘等价容器
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppDimens.radiusMd))
                .background(
                    when (method) {
                        PaymentMethod.WECHAT -> AppColors.colorScheme.iconContainerGreen
                        PaymentMethod.ALIPAY -> AppColors.colorScheme.iconContainerBlue
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    when (method) {
                        PaymentMethod.WECHAT -> R.drawable.ic_wechat
                        PaymentMethod.ALIPAY -> R.drawable.ic_alipay
                    }
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (method) {
                    // 官方品牌色：微信 #09BB07 / 支付宝 #1677FF（Simple Icons 口径）
                    PaymentMethod.WECHAT -> WeChatBrandGreen
                    PaymentMethod.ALIPAY -> AlipayBrandBlue
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = when (method) {
                PaymentMethod.WECHAT -> stringResource(R.string.weixin_pay)
                PaymentMethod.ALIPAY -> stringResource(R.string.alipay)
            },
            // 画板实测支付行标题 14/400 textPrimary（bodyMedium 档），非 18/600
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.colorScheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        // 圆形选中标记 — 画板实测 24（复用公共 SelectionDot 内圆弹簧长出）
        SelectionDot(selected = isSelected)
    }
}

/** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
private const val TAG = "RechargePage"

// LazyColumn 固定条目序号（余额 0 / 金额 1 / 支付方式 2）：校验失败自动滚动定位用（修复②）
private const val ITEM_INDEX_AMOUNT = 1
private const val ITEM_INDEX_PAYMENT = 2

private enum class PaymentMethod(val payType: String, val descriptionRes: Int) {
    WECHAT("1", R.string.weixin_pay), ALIPAY("2", R.string.alipay)
}

/** 官方品牌色（规范 §10 红线豁免：第三方品牌图标与品牌色不得换；充值页/记录页共用） */
internal val WeChatBrandGreen = Color(0xFF09BB07)
internal val AlipayBrandBlue = Color(0xFF1677FF)

/** 金额校验失败原因 → 文案（校验规则见 [RechargeAmountValidator]） */
@Composable
private fun resolveAmountErrorText(error: RechargeAmountError): String = when (error) {
    RechargeAmountError.NOT_SELECTED -> stringResource(R.string.select_or_input_amount)
    RechargeAmountError.EMPTY -> stringResource(R.string.recharge_amount_empty)
    RechargeAmountError.INVALID_FORMAT -> stringResource(R.string.recharge_amount_invalid_format)
    RechargeAmountError.NOT_POSITIVE -> stringResource(R.string.recharge_amount_not_positive)
    RechargeAmountError.EXCEED_MAX -> stringResource(
        R.string.recharge_amount_exceed_max_format,
        RechargeAmountValidator.MAX_RECHARGE_AMOUNT.toInt().toString()
    )
}
