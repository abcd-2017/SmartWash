package com.smartwash.feature.toolbox.ui.delivery

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 会话倒计时（发起页与会话页共用）：以 expireAt 为锚点本地秒级滴答，
 * 每次轮询/重组锚点变更时重新对时；≤60s 切 dangerInk 紧迫色。
 * 数字一律 tnum（规范 §2.2：倒计时变化不左右跳）。
 */

/** 剩余秒数滴答（expireAt 不可解析返回 null；归零后停在 0） */
@Composable
internal fun rememberRemainingSeconds(expireAt: String?): Long? {
    val expire = remember(expireAt) { DeliveryRules.parseOrNull(expireAt) }
    return if (expire == null) {
        null
    } else {
        produceState<Long?>(initialValue = null, key1 = expire) {
            while (isActive) {
                value = ChronoUnit.SECONDS.between(LocalDateTime.now(), expire).coerceAtLeast(0)
                if ((value ?: 0L) <= 0L) break
                delay(1_000)
            }
        }.value
    }
}

/** 倒计时文字（「剩余 mm:ss / H:MM:SS」），紧迫态（≤60s）自动切 dangerInk */
@Composable
internal fun DeliveryCountdownText(
    expireAt: String?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val remaining = rememberRemainingSeconds(expireAt) ?: return
    val urgent = remaining <= 60L
    Text(
        text = stringResource(R.string.toolbox_delivery_countdown_format, DeliveryRules.formatRemaining(remaining)),
        style = style.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Medium,
        color = if (urgent) AppColors.colorScheme.dangerInk else color,
        modifier = modifier,
    )
}
