package com.smartwash.feature.readlater.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable

/**
 * 稍后读模块内共享小件：剪贴板复制、静态标签 chip、标签筛选 chip、卡内操作胶囊按钮。
 * 均组合共享组件（Surface + pressable），模式对齐 toolbox 的 ToolboxUiCommon，不引入新交互组件。
 */

/** 复制到剪贴板（转短链成功后页面调用） */
internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("readLaterShortUrl", text))
}

/** 静态标签 chip —— 胶囊（radiusFull），用于条目上的标签展示 */
@Composable
internal fun ReadLaterTagChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.radiusFull),
        color = AppColors.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.colorScheme.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = AppDimens.spaceXs, vertical = 2.dp),
        )
    }
}

/**
 * 标签筛选 chip —— 选中 = 品牌底白字，未选中 = 白底描边（模式对齐 ToolboxSelectableChip /
 * CouponPage 筛选胶囊）。外层 48dp 最小热区；chip 行横向滚动兜底（调用方加 horizontalScroll）。
 */
@Composable
internal fun ReadLaterFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 选中底色/文字色/描边色走 selectedColor（200ms 档弹簧，reduced motion 自动降级）
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        AppColors.colorScheme.textSecondary
    }
    val borderColor = if (selected) Color.Transparent else AppColors.colorScheme.outline
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(onClick = onClick, debounce = false),
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
                // 字重统一 SemiBold：字重随选中切换会让文字宽度跳变（对齐 toolbox 裁定）
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = AppDimens.spaceMd, vertical = 6.dp),
            )
        }
    }
}

/**
 * 卡内操作胶囊按钮（生成摘要/标记已读↔未读/转短链/删除）：视觉 32 高描边胶囊 +
 * 外扩热区至 48dp（模式对齐 toolbox CopyPillButton）。[enabled] = false 时置灰不可点。
 */
@Composable
internal fun ReadLaterPillAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = AppColors.colorScheme.textGreen,
) {
    val tint = if (enabled) contentColor else AppColors.colorScheme.textTertiary
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.radiusFull),
            color = Color.Transparent,
            border = BorderStroke(1.dp, if (enabled) AppColors.colorScheme.outline else AppColors.colorScheme.divider),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** 可移除标签 chip（收藏输入条已提交的标签，点击移除）——胶囊 + 48dp 热区 */
@Composable
internal fun ReadLaterRemovableTagChip(
    text: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .pressable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AppDimens.radiusFull),
            color = AppColors.colorScheme.primarySoft,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AppDimens.spaceSm, vertical = 4.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.onPrimarySoft,
                    maxLines = 1,
                )
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.colorScheme.onPrimarySoft,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
