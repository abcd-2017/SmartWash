package com.smartwash.feature.dining.ui.order

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.generateQrCodeBitmap
import com.smartwash.feature.dining.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 加入二维码内容区（分享页 / 邀请页共用）：joinUrl 可构造 → 生成并展示；不可构造 → 凭证不可得降级提示。
 *
 * 二维码异步生成（先例模式）：[produceState] 以 url 为 key，Default 线程生成不占主线程；
 * 生成期以 surfaceVariant 方块占位（对齐取件页「加载中」盒子语义）。
 */
@Composable
internal fun JoinQrContent(
    joinUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (joinUrl == null) {
            Text(
                text = stringResource(R.string.dining_order_share_qr_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.colorScheme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimens.spaceLg, horizontal = AppDimens.spaceMd),
            )
        } else {
            val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = joinUrl) {
                value = withContext(Dispatchers.Default) { generateQrCodeBitmap(joinUrl, 512) }
            }
            val imageBitmap = qrBitmap?.asImageBitmap()
            if (imageBitmap == null) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusMd))
                        .background(AppColors.colorScheme.surfaceVariant),
                )
            } else {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = stringResource(R.string.dining_order_share_qr_section),
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(AppDimens.radiusMd)),
                )
            }
        }
    }
}
