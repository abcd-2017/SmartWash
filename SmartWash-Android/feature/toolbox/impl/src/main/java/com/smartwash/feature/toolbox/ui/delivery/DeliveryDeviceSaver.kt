package com.smartwash.feature.toolbox.ui.delivery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.toolbox.R
import com.smartwash.feature.toolbox.logic.DeliveryRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * 设备投递的设备侧 IO 小件（选取文件元信息解析 / 条目流落缓存 / 媒体库保存 / 预览位图解码）。
 * 全部 suspend 且切 IO/Default 线程，供 ViewModel 编排，页面不触碰 IO。
 */

/**
 * 系统媒体库写入失败：只携带 UI 文案资源 id（[resId]），由 UI 层映射为 stringResource 文案，
 * 禁止在异常消息里硬编码中文（规范 §9「所有用户可见文案在 strings.xml，无硬编码」）。
 */
internal class DeliveryMediaException(
    @StringRes val resId: Int,
) : Exception()

/** 所选文件的元信息（发布 multipart 用） */
internal data class DeliveryPickedFile(
    val uri: Uri,
    val displayName: String,
    val size: Long,
)

/**
 * 解析所选 Uri 的显示名与大小（DocumentsProvider 一般都带 SIZE）。
 * SIZE 列是 provider 快照值（documentsui「最近」在选取后文件被修改/元信息过期即失真），
 * 与实际流不符会被 multipart contentLength 判 ProtocolException 且误报网络失败，
 * 故一律以实际流计数复核修正（封顶 MAX+1 提前止损，超限不再读完整个流）；
 * 流打不开时回落快照值，把失败留给发布阶段以真实错误暴露。
 */
internal suspend fun resolvePickedFile(context: Context, uri: Uri): DeliveryPickedFile =
    withContext(Dispatchers.IO) {
        var name = "delivery-file"
        var size = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        // 实际流计数复核（修正快照漂移）：含 0 字节空文件——流能打开即以实际计数为准
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                var total = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (total <= DeliveryRules.MAX_FILE_BYTES) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                }
                size = total
            }
        }
        if (size < 0) {
            // 元信息查询与流计数均失败：发布必然失败，降级留痕
            AppLogger.warn(TAG, "resolvePickedFile: 文件大小探测失败，按 0 字节降级 uri=$uri")
        }
        DeliveryPickedFile(uri = uri, displayName = name, size = size.coerceAtLeast(0))
    }

/** 缓存目录名（预览下载与补测临时文件共用，onCleared/dismiss 时整体清扫） */
internal const val DELIVERY_CACHE_DIR = "delivery"

/** 条目流落缓存文件（图片预览用），返回缓存文件；文件名经安全化（防路径分隔符） */
internal suspend fun writeToCache(context: Context, source: InputStream, rawName: String): File =
    withContext(Dispatchers.IO) {
        val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "delivery_${System.nanoTime()}" }
        val dir = File(context.cacheDir, DELIVERY_CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "${System.nanoTime()}_$safeName")
        file.outputStream().use { output -> source.use { input -> input.copyTo(output) } }
        file
    }

/** 清扫投递预览缓存目录（dismiss/关闭会话页时调用；目录不存在时静默） */
internal suspend fun cleanupDeliveryCache(context: Context) = withContext(Dispatchers.IO) {
    File(context.cacheDir, DELIVERY_CACHE_DIR).deleteRecursively()
}

/** mime 兜底：条目 mimeType 缺失时按扩展名推断，再兜底二进制流（MediaStore 必填字段） */
internal fun resolveMimeType(mimeType: String?, displayName: String): String {
    mimeType?.takeIf { it.isNotBlank() }?.let { return it }
    val ext = displayName.substringAfterLast('.', "").lowercase(Locale.US)
    if (ext.isNotBlank()) {
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    }
    return "application/octet-stream"
}

/**
 * 保存条目内容流到系统媒体库（scoped storage，minSdk 30 无需权限）：
 * 图片 → Pictures/SmartWash，文件 → Download/SmartWash。返回显示名；
 * 写入失败删除半成品（IS_PENDING 记录一并回收）。
 */
internal suspend fun saveDeliveryContent(
    context: Context,
    isImage: Boolean,
    displayName: String,
    mimeType: String?,
    source: InputStream,
): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "delivery_${System.nanoTime()}" }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
        put(MediaStore.MediaColumns.MIME_TYPE, resolveMimeType(mimeType, safeName))
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (isImage) PICTURES_PATH else DOWNLOADS_PATH,
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val collection = if (isImage) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }
    val target = resolver.insert(collection, values)
        ?: throw DeliveryMediaException(R.string.toolbox_delivery_error_media_insert_denied)
    try {
        resolver.openOutputStream(target)?.use { output ->
            source.use { input -> input.copyTo(output) }
        } ?: throw DeliveryMediaException(R.string.toolbox_delivery_error_media_stream_open)
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(target, done, null, null)
        safeName
    } catch (e: Throwable) {
        runCatching { resolver.delete(target, null, null) }
        throw e
    }
}

private const val PICTURES_PATH = "Pictures/SmartWash"
private const val DOWNLOADS_PATH = "Download/SmartWash"

private const val TAG = "DeliveryDeviceSaver"

/**
 * 缓存文件解码预览位图：先读边界再按 2048dp 长边取样，防 20MB 原图整张进内存。
 * 解码失败（异形格式）返回 null，调用方回退「直接保存」文案。
 */
internal suspend fun decodeSampledBitmap(file: File, maxDim: Int = 2048): Bitmap? =
    withContext(Dispatchers.Default) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull().also { bitmap ->
            if (bitmap == null) {
                // 异形格式解码失败：调用方回退「直接保存原文件」路径，降级留痕
                AppLogger.warn(TAG, "decodeSampledBitmap: 预览位图解码失败，回退直接保存 file=${file.name}")
            }
        }
    }

/** 文件大小未知的防御展示（理论到不了：发布前置了 20MB 校验） */
internal fun describeSize(bytes: Long?): String =
    bytes?.let { DeliveryRules.formatFileSize(it) } ?: "—"
