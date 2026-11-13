package com.smartwash.feature.toolbox.ui.assettag

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.smartwash.common.utils.generateQrCodeBitmap

/**
 * 物品标签图合成（一物一码 M2）：物品名 + 二维码 + 标签码，白底竖版标签，
 * 经系统分享面板分发（可保存 / 打印张贴）。纯 Android Graphics，无 Compose 依赖。
 *
 * 版式（720×880 @1x）：
 *   ┌──────────────┐
 *   │  物品名称     │  44sp 粗体居中（过长缩到 28sp，再长省略）
 *   │              │
 *   │   ▣ QR 460   │  居中，留 24 边距
 *   │              │
 *   │  tAg1234     │  26sp 标签码（tnum 视觉）
 *   └──────────────┘
 */
fun buildAssetTagLabelBitmap(itemName: String, url: String, code: String? = null): Bitmap {
    require(url.isNotBlank()) { "label url must not be blank" }
    val width = 720
    val height = 880
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        textSize = if (itemName.length > 12) 40f else 52f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#D1D5DB")
    }

    // 外框（裁剪边界参考线）
    val margin = 24f
    canvas.drawRect(margin, margin, width - margin, height - margin, borderPaint)

    // 物品名：单行居中，过长在绘制层截断（省略号）
    val name = ellipsize(itemName, namePaint, width - margin * 4)
    canvas.drawText(name, width / 2f, 120f, namePaint)

    // 二维码：460×420 区域内保持比例（QR 恒方阵，取 420）
    val qrSize = 420
    val qr = generateQrCodeBitmap(url, qrSize)
    val qrLeft = (width - qrSize) / 2f
    canvas.drawBitmap(qr, qrLeft, 190f, null)

    // 标签码
    canvas.drawText(code ?: "", width / 2f, height - 90f, codePaint)
    return bitmap
}

/** 绘制层省略：超出 maxWidth 时截断加 … */
private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) {
        return text
    }
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
        end--
    }
    return text.substring(0, end) + "…"
}
