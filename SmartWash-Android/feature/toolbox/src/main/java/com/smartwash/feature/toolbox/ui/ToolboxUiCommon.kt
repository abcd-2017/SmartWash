package com.smartwash.feature.toolbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.ShortCodeRules
import java.time.LocalDate

/**
 * 工具箱模块内共享小件：静态标签 chip（列表条目）、可选预设 chip 行（表单）与
 * 日期选择弹窗（创建页/续期共用）。均组合共享组件（Surface + pressable / M3 DatePicker），
 * 不引入新交互组件。
 */

/** 静态标签 chip —— 胶囊（radiusFull），11→12sp labelSmall，用于条目上的类型/可见性标注 */
@Composable
fun ToolboxTagChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = AppDimens.spaceXs, vertical = 2.dp),
        )
    }
}

/** 可选预设 chip —— 选中 = 品牌底白字，未选中 = 白底描边（模式对齐 CouponPage 筛选胶囊） */
@Composable
fun ToolboxSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.pressable(onClick = onClick, debounce = false),
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = if (selected) AppColors.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, AppColors.colorScheme.outline),
    ) {
        Box {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) Color.White else AppColors.colorScheme.textSecondary,
                modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = 6.dp),
            )
        }
    }
}

/**
 * 有效期预设 chip 行（永久 / 7 天 / 30 天 / 自定义）。includePermanent=false 用于
 * 详情页续期（永久不参与修改——PUT 语义中不传即不修改，无法改回永久）。
 */
@Composable
fun ToolboxExpiryPresetRow(
    selected: ShortCodeRules.ExpiryPreset,
    onSelect: (ShortCodeRules.ExpiryPreset) -> Unit,
    modifier: Modifier = Modifier,
    includePermanent: Boolean = true,
) {
    val options = buildList {
        if (includePermanent) {
            add(ShortCodeRules.ExpiryPreset.PERMANENT to stringResource(R.string.toolbox_expiry_preset_permanent))
        }
        add(ShortCodeRules.ExpiryPreset.DAYS_7 to stringResource(R.string.toolbox_expiry_preset_7d))
        add(ShortCodeRules.ExpiryPreset.DAYS_30 to stringResource(R.string.toolbox_expiry_preset_30d))
        add(ShortCodeRules.ExpiryPreset.CUSTOM to stringResource(R.string.toolbox_expiry_preset_custom))
    }
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppDimens.spaceXs),
    ) {
        options.forEach { (preset, label) ->
            ToolboxSelectableChip(
                text = label,
                selected = selected == preset,
                onClick = { onSelect(preset) },
            )
        }
    }
}

/**
 * 日期选择弹窗（自定义过期日期）。确定后回调所选 LocalDate；内部按 UTC 取整日，
 * 与 [ShortCodeRules.localDateFromPickerMillis] 互逆。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxDatePickerDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogTextAction(text = stringResource(R.string.toolbox_date_confirm)) {
                ShortCodeRules.localDateFromPickerMillis(pickerState.selectedDateMillis)?.let(onConfirm)
            }
        },
        dismissButton = {
            DialogTextAction(text = stringResource(R.string.toolbox_date_cancel), onDismiss)
        },
    ) {
        DatePicker(state = pickerState)
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
            .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.colorScheme.primary,
        )
    }
}
