package com.smartwash.feature.subscription.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.ui.theme.AppTextStyles
import com.smartwash.common.utils.defaultSpring
import com.smartwash.common.utils.motionSpec
import com.smartwash.common.utils.pressable
import com.smartwash.common.utils.selectedColor
import com.smartwash.feature.subscription.R
import com.smartwash.feature.subscription.SubscriptionCycleType
import com.smartwash.feature.subscription.SubscriptionStatus
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
 * 状态筛选 chip —— 选中 = 品牌底白字，未选中 = 白底描边
 * （模式对齐 ToolboxSelectableChip / CouponPage 筛选胶囊）。
 */
@Composable
internal fun SubscriptionSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 选中底色 / 文字色 / 描边色统一走公共 selectedColor（弹簧过渡，reduced motion 自动降级）
    val containerColor = selectedColor(
        if (selected) AppColors.colorScheme.primary else MaterialTheme.colorScheme.surface
    )
    val contentColor = selectedColor(
        // onPrimary 令牌（浅白/深绿）：深色模式下品牌绿提亮，白字对比度不足
        if (selected) AppColors.colorScheme.onPrimary else AppColors.colorScheme.textSecondary
    )
    // 描边常驻 1dp、颜色在 outline ↔ 透明间过渡：选中切换不再让描边整条消失/出现
    val borderColor = selectedColor(
        if (selected) Color.Transparent else AppColors.colorScheme.outline
    )
    // 外层 44dp 最小热区（规范 §2.3 触控热区 ≥44dp），胶囊本体居中，视觉高度不变
    Box(
        modifier = modifier
            .pressable(onClick = onClick, debounce = false)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.radiusFull),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                // 字重统一 SemiBold：字重随选中切换会让文字宽度跳变，兄弟 chip 连续位移观感更差
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
            )
        }
    }
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
    // 对齐清氧规范：弹窗圆角收敛到 radiusLg 16dp（M3 默认 28dp 不在 §2.3 五档内），
    // 容器走 surface 令牌 + tonalElevation 归零（防 M3 默认叠加色偏灰）
    val datePickerColors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogTextAction(text = stringResource(R.string.subscription_date_confirm)) {
                SubscriptionRules.localDateFromPickerMillis(pickerState.selectedDateMillis)?.let(onConfirm)
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

/**
 * 自绘开关（自动续费）—— 全库无既有 Switch 先例，且 M3 Switch 自带 state layer
 * （规范 §7 禁直接用于交互场景），故自绘：轨道 44×24 胶囊，thumb 20dp 圆点，
 * 位移走弹簧（规范 §7 位移动画禁固定时长 tween），reduced motion 经 motionSpec 自动降级 180ms。
 */
@Composable
internal fun SubscriptionToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = motionSpec(defaultSpring()),
        label = "subscriptionToggleThumb",
    )
    val trackColor = selectedColor(
        if (checked) AppColors.colorScheme.primary else AppColors.colorScheme.textTertiary
    )
    // 外层 44dp 最小热区（规范 §2.3），24dp 轨道居中，视觉尺寸不变
    Box(
        modifier = modifier
            .pressable(onClick = { onCheckedChange(!checked) }, debounce = false)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(AppDimens.radiusFull))
                .background(trackColor),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(width = 20.dp, height = 20.dp)
                    .clip(CircleShape)
                    .background(AppColors.colorScheme.surface),
            )
        }
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
internal fun renewRelativeText(vo: com.smartwash.feature.subscription.network.SubscriptionVo): String {
    val dateText = vo.nextRenewDate.orEmpty()
    val base = stringResource(R.string.subscription_renew_date_value, dateText)
    val daysLeft = vo.daysUntilRenew ?: return base
    return when {
        daysLeft < 0 -> "$base · " + stringResource(R.string.subscription_renew_overdue_format, -daysLeft)
        daysLeft == 0 -> "$base · " + stringResource(R.string.subscription_renew_today)
        else -> "$base · " + stringResource(R.string.subscription_renew_in_days_format, daysLeft)
    }
}

/** 续费相对描述配色（已逾期 dangerInk / 今日 ongoingInk / 其余次文本；终态弱化） */
@Composable
internal fun renewRelativeColor(vo: com.smartwash.feature.subscription.network.SubscriptionVo): Color {
    val status = SubscriptionStatus.fromRawOrDefault(vo.status)
    if (status.isTerminal) return AppColors.colorScheme.textTertiary
    return when {
        (vo.daysUntilRenew ?: 1) < 0 -> AppColors.colorScheme.dangerInk
        (vo.daysUntilRenew ?: 1) == 0 -> AppColors.colorScheme.ongoingInk
        else -> AppColors.colorScheme.textSecondary
    }
}
