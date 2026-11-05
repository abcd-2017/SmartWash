package com.smartwash.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.utils.CROSSFADE_ALT_MS
import com.smartwash.common.utils.LocalReduceMotion

/**
 * 统一网络配图组件（调研 C4：Coil 下沉 common/ui，各域共用）。
 *
 * 形态：surfaceVariant 占位 + 180ms crossfade（reduced motion 关闭淡变）；
 * 装饰性配图语义由宿主标题/描述承载，故 contentDescription = null。
 *
 * @param url 网络图直链（MinIO 公网读）
 * @param modifier 尺寸/形状约束（由调用方给 16:9 等）
 * @param contentScale 裁切方式，默认 Crop（卡片头图）
 */
@Composable
fun SmartWashAsyncImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val scheme = AppColors.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val request = remember(url, reduceMotion) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { if (reduceMotion) crossfade(false) else crossfade(CROSSFADE_ALT_MS) }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier.background(scheme.surfaceVariant),
        placeholder = ColorPainter(scheme.surfaceVariant),
        contentScale = contentScale,
    )
}
