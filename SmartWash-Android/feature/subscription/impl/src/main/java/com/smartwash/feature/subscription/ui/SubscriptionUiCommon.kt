package com.smartwash.feature.subscription.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.components.AppFilterChip
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.pressable
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.api.SubscriptionCycleType
import com.smartwash.feature.subscription.api.SubscriptionStatus
import com.smartwash.feature.subscription.logic.SubscriptionRules
import java.time.LocalDate

/**
 * 订阅模块内共享小件：静态标签 chip（状态/类型标注）、可选筛选 chip、日期选择弹窗、
 * 自绘开关（自动续费）、剪贴板复制。均组合共享组件（Surface + pressable / M3 DatePicker），
 * 不引入 Material 交互组件（规范 §7 禁 state layer 涟漪），不引入新交互组件。
 */

/** 复制到剪贴板（取消网址「复制」与详情页共用） */
internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("subscription", text))
}

/** 静态标签 chip —— 胶囊（radiusFull），labelSmall，用于条目上的状态/类型/角标标注 */
@Composable
internal fun SubscriptionTagChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = style,
            color = contentColor,
            modifier = Modifier.padding(horizontal = AppDimens.spaceXs, vertical = AppDimens.spaceXxs),
        )
    }
}

/**
 * 状态筛选 chip —— 统一走公共 [AppFilterChip]（SOLID）：
 * 选中 = primary 实底 + onPrimary 白字，未选中 = surface 底 + 1dp outline 描边 + textSecondary。
 * 48dp 最小热区 / 常驻 1dp 描边 / 恒定 SemiBold / selected 语义等口径由公共组件内置
 * （原实现即无 haptic，故不传 hapticOnSelect）。
 */
@Composable
internal fun SubscriptionSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppFilterChip(
        text = text,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * 日期选择弹窗（下次续费日/试用截止共用）。确定后回调所选 LocalDate；
 * 内部按 UTC 取整日，与 [SubscriptionRules.localDateFromPickerMillis] 互逆。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionDatePickerDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    // 未选日期直接点「确定」时给出校验提示（原实现静默吞掉回调，用户以为按钮坏了——评审 P2）
    var showSelectDateHint by remember { mutableStateOf(false) }
    // 对齐清氧规范：弹窗圆角收敛到 radiusLg 16dp（M3 默认 28dp 不在 §2.3 五档内），
    // 容器走 surface 令牌 + tonalElevation 归零（防 M3 默认叠加色偏灰）
    val datePickerColors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
    )
    // 选择日期后清除校验提示
    LaunchedEffect(pickerState.selectedDateMillis) {
        if (pickerState.selectedDateMillis != null) showSelectDateHint = false
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogTextAction(text = stringResource(R.string.subscription_date_confirm)) {
                val selectedMillis = pickerState.selectedDateMillis
                if (selectedMillis == null) {
                    showSelectDateHint = true
                } else {
                    SubscriptionRules.localDateFromPickerMillis(selectedMillis)?.let {
                        showSelectDateHint = false
                        onConfirm(it)
                    }
                }
            }
        },
        dismissButton = {
            DialogTextAction(text = stringResource(R.string.subscription_date_cancel), onDismiss)
        },
        shape = RoundedCornerShape(AppDimens.radiusLg),
        tonalElevation = 0.dp,
        colors = datePickerColors,
    ) {
        DatePicker(state = pickerState, colors = datePickerColors)
        if (showSelectDateHint) {
            Text(
                text = stringResource(R.string.subscription_date_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.error,
                modifier = Modifier.padding(top = AppDimens.spaceXs),
            )
        }
    }
}

/** 弹窗文字动作（pressable 自绘，规避 M3 TextButton 的 state layer——规范 §7） */
@Composable
private fun DialogTextAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .pressable(onClick = onClick, debounce = false)
            .heightIn(min = 44.dp)
            .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.colorScheme.textGreen,
        )
    }
}

/** 订阅状态文案映射（列表 chip / 详情信息行共用） */
@Composable
internal fun statusText(status: SubscriptionStatus): String = stringResource(
    when (status) {
        SubscriptionStatus.ACTIVE -> R.string.subscription_status_active
        SubscriptionStatus.PAUSED -> R.string.subscription_status_paused
        SubscriptionStatus.CANCELED -> R.string.subscription_status_canceled
        SubscriptionStatus.FINISHED -> R.string.subscription_status_finished
    }
)

/**
 * 状态 chip 底色映射（2026-11-11 列表页 statusChipContainer 裁定口径，详情页同源消费）：
 * PAUSED = warningContainer（橙警示浅底）/ CANCELED = iconContainerPink / 其余（含终态）= surfaceVariant
 */
@Composable
internal fun statusChipContainer(status: SubscriptionStatus): Color = when (status) {
    SubscriptionStatus.PAUSED -> AppColors.colorScheme.warningContainer
    SubscriptionStatus.CANCELED -> AppColors.colorScheme.iconContainerPink
    else -> AppColors.colorScheme.surfaceVariant
}

/**
 * 状态 chip 文字色（与 [statusChipContainer] 成对令牌）：PAUSED = onWarningContainer（6.47:1）/
 * CANCELED = dangerInk / 其余（含终态）= textSecondary（on surfaceVariant 4.51:1）。
 * textTertiary 1.98:1 禁作可读标签（DESIGN-SPEC §10）；图标前景令牌不得拼作 12sp 文字色
 */
@Composable
internal fun statusChipContent(status: SubscriptionStatus): Color = when (status) {
    SubscriptionStatus.PAUSED -> AppColors.colorScheme.onWarningContainer
    SubscriptionStatus.CANCELED -> AppColors.colorScheme.dangerInk
    else -> AppColors.colorScheme.textSecondary
}

/** 周期描述：月/年 × 间隔（1 期收敛为「每月/每年」） */
@Composable
internal fun cycleDescription(cycleType: SubscriptionCycleType, interval: Int): String =
    when (cycleType) {
        SubscriptionCycleType.MONTH ->
            if (interval <= 1) stringResource(R.string.subscription_cycle_monthly)
            else stringResource(R.string.subscription_cycle_every_n_months, interval)
        SubscriptionCycleType.YEAR ->
            if (interval <= 1) stringResource(R.string.subscription_cycle_yearly)
            else stringResource(R.string.subscription_cycle_every_n_years, interval)
    }

/** 区块标题（详情页卡头，AppTextStyles.SectionTitle 统一档） */
@Composable
internal fun SubscriptionSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTextStyles.SectionTitle,
        color = AppColors.colorScheme.textPrimary,
        modifier = modifier,
    )
}

/** 续费相对描述（「下次续费 yyyy-MM-dd · N 天后/今日/已逾期 N 天」），列表卡与详情信息行共用 */
@Composable
internal fun renewRelativeText(vo: com.smartwash.feature.subscription.api.network.SubscriptionVo): String {
    val dateText = vo.nextRenewDate.orEmpty()
    val base = stringResource(R.string.subscription_renew_date_value, dateText)
    val daysLeft = vo.daysUntilRenew ?: return base
    return when {
        daysLeft < 0 -> "$base · " + stringResource(R.string.subscription_renew_overdue_format, -daysLeft)
        daysLeft == 0 -> "$base · " + stringResource(R.string.subscription_renew_today)
        else -> "$base · " + stringResource(R.string.subscription_renew_in_days_format, daysLeft)
    }
}

/** 续费相对描述配色（已逾期 dangerInk / 今日 ongoingInk / 其余次文本；终态弱化至 textSecondary） */
@Composable
internal fun renewRelativeColor(vo: com.smartwash.feature.subscription.api.network.SubscriptionVo): Color {
    val status = SubscriptionStatus.fromRawOrDefault(vo.status)
    // 终态弱化止步 textSecondary（on 白卡 4.9:1）：装饰档 textTertiary 禁作可读正文（DESIGN-SPEC §10）
    if (status.isTerminal) return AppColors.colorScheme.textSecondary
    return when {
        (vo.daysUntilRenew ?: 1) < 0 -> AppColors.colorScheme.dangerInk
        (vo.daysUntilRenew ?: 1) == 0 -> AppColors.colorScheme.ongoingInk
        else -> AppColors.colorScheme.textSecondary
    }
}
