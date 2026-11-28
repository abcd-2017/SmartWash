package com.smartwash.feature.dining.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * 购买清单分享海报（纯 Android Graphics，无 Compose 依赖）：竖版米白底 + 标题 + 食材行 + 品牌脚。
 * 版式 720 宽、高度随行数自适应，最多绘制 [MAX_ROWS] 行（超出以「还有 N 项…」收口）。
 *
 * 分享链路复用 :feature:update 的 FileProvider 授权目录（cacheDir/asset_tag_labels）与
 * toolbox 标签图同先例，经系统分享面板分发（可保存/发到家庭群）。
 */
object DiningShoppingListPoster {

    private const val WIDTH = 720
    private const val MARGIN = 48f
    private const val ROW_HEIGHT = 64f
    private const val MAX_ROWS = 24
    private const val HEADER_HEIGHT = 250f
    private const val FOOTER_HEIGHT = 150f

    private const val BG = 0xFFF5F6F3.toInt()
    private const val TITLE = 0xFF1F2937.toInt()
    private const val SUBTITLE = 0xFF6B7280.toInt()
    private const val BODY = 0xFF374151.toInt()
    private const val ACCENT = 0xFF2E7D5B.toInt()
    private const val DIVIDER = 0xFFE5E7EB.toInt()
    private const val FOOTER = 0xFF9CA3AF.toInt()

    /** 海报行：标签 + 可选数量（右对齐）+ 是否已买（画勾选框） */
    data class Line(val label: String, val amount: String? = null, val checked: Boolean = false)

    /** 结果模型 → 海报行（v2 聚合行优先，老快照回落名并集；已买态按名对齐） */
    fun buildLines(result: DiningShoppingListResult, checked: Set<String>): List<Line> {
        val totals = result.ingredientTotals
        return if (totals != null) {
            totals.map { total ->
                val amount = total.totalValue
                    ?.let { DiningNutritionCalculator.formatAmount(it) }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { value -> if (total.unit.isBlank()) value else "$value${total.unit}" }
                Line(label = total.name, amount = amount, checked = total.name in checked)
            }
        } else {
            result.ingredients.map { name -> Line(label = name, checked = name in checked) }
        }
    }

    fun build(title: String, subtitle: String, lines: List<Line>, footer: String): Bitmap {
        val shown = lines.take(MAX_ROWS)
        val overflowCount = lines.size - shown.size
        val height = (HEADER_HEIGHT + shown.size * ROW_HEIGHT +
            (if (overflowCount > 0) ROW_HEIGHT else 0f) + FOOTER_HEIGHT).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 10f, accentPaint())

        val titlePaint = textPaint(TITLE, 46f, bold = true)
        val subtitlePaint = textPaint(SUBTITLE, 26f)
        val rowPaint = textPaint(BODY, 30f)
        val amountPaint = textPaint(SUBTITLE, 26f)
        amountPaint.textAlign = Paint.Align.RIGHT
        val morePaint = textPaint(FOOTER, 26f)
        val footerPaint = textPaint(FOOTER, 24f)
        footerPaint.textAlign = Paint.Align.CENTER

        // 标题（左对齐，自动缩到 34sp 再省略）
        var titleSize = 46f
        while (titleSize > 34f && titlePaint.measureText(title) > WIDTH - MARGIN * 2) {
            titleSize -= 2f
            titlePaint.textSize = titleSize
        }
        canvas.drawText(ellipsize(title, titlePaint, WIDTH - MARGIN * 2), MARGIN, 108f, titlePaint)
        canvas.drawText(ellipsize(subtitle, subtitlePaint, WIDTH - MARGIN * 2), MARGIN, 158f, subtitlePaint)
        canvas.drawRect(MARGIN, 196f, WIDTH - MARGIN, 198f, dividerPaint())

        // 食材行
        var y = 196f + ROW_HEIGHT
        shown.forEach { line ->
            // 勾选框：已买填色 + 白勾，未买描边空心
            val box = RectF(MARGIN, y - 34f, MARGIN + 34f, y)
            if (line.checked) {
                canvas.drawRoundRect(box, 8f, 8f, accentPaint())
                canvas.drawLine(MARGIN + 8f, y - 17f, MARGIN + 15f, y - 9f, checkPaint())
                canvas.drawLine(MARGIN + 15f, y - 9f, MARGIN + 27f, y - 26f, checkPaint())
            } else {
                canvas.drawRoundRect(box, 8f, 8f, hollowPaint())
            }
            canvas.drawText(ellipsize(line.label, rowPaint, WIDTH - MARGIN * 2 - 260f),
                MARGIN + 52f, y - 8f, rowPaint)
            if (!line.amount.isNullOrBlank()) {
                canvas.drawText(line.amount, WIDTH - MARGIN, y - 8f, amountPaint)
            }
            y += ROW_HEIGHT
        }
        if (overflowCount > 0) {
            canvas.drawText("还有 $overflowCount 项…", MARGIN + 52f, y - 8f, morePaint)
        }

        // 品牌脚
        canvas.drawRect(MARGIN, height - 96f, WIDTH - MARGIN, height - 94f, dividerPaint())
        canvas.drawText(footer, WIDTH / 2f, height - 56f, footerPaint)
        return bitmap
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun accentPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private fun dividerPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIVIDER }
    private fun checkPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private fun hollowPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = DIVIDER
    }

    /** 绘制层省略：超出 maxWidth 时截断加 … */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
