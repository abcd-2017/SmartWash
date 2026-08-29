package com.smartwash.divination.core.liuyao

import com.smartwash.divination.core.DiZhi
import com.smartwash.divination.core.DivCategory
import com.smartwash.divination.core.WuXing

/**
 * 六爻断法规则层 —— 只产确定性事实标记，不下吉凶结论（方案文档第三章第 4 节）。
 * 旺相休囚死 / 月破 / 日冲 / 旬空 / 动变回头生克 / 用神候选。
 */
data class LiuYaoFacts(
    /** 用神爻位（1..6）与描述，如 "妻财丙子水 · 五爻" */
    val yongShenPosition: Int?,
    val yongShenLabel: String?,
    /** 爻位 → 旺相休囚死（按月令） */
    val strength: Map<Int, String>,
    /** 月破爻位（爻支冲月支） */
    val yuePo: List<Int>,
    /** 日冲爻位（爻支冲日支） */
    val riChong: List<Int>,
    /** 旬空爻位（爻支落日旬空） */
    val xunKongYao: List<Int>,
    /** 动爻位 → 变爻回头生 / 回头克（变爻五行对动爻五行） */
    val huiTou: Map<Int, String>,
    /** 用神选取说明（含两现裁决），供解读层引用 */
    val yongShenNote: String?,
) {
    companion object {
        /**
         * 用神六亲映射（首选项）：事业/工作取官鬼，财运取妻财，感情取妻财（官鬼次之），
         * 学业取父母，出行取父母，其余以世爻为用。
         */
        private fun preferredQin(category: DivCategory): List<String> = when (category) {
            DivCategory.CAREER -> listOf("官鬼", "父母")
            DivCategory.WEALTH -> listOf("妻财")
            DivCategory.LOVE -> listOf("妻财", "官鬼")
            DivCategory.STUDY -> listOf("父母", "官鬼")
            DivCategory.TRAVEL -> listOf("父母")
            DivCategory.OTHER -> emptyList()
        }

        fun of(chart: LiuYaoChart, category: DivCategory): LiuYaoFacts {
            // 旺相休囚死：当令旺、令生相、生令休、克令囚、令克死（月支五行为令）
            val monthWx = chart.siZhu.month.zhi.wuXing
            val strength = chart.rows.associate { row ->
                row.position to when (row.zhiWuXing) {
                    monthWx -> "旺"
                    monthWx.generates() -> "相"
                    monthWx.generatedBy() -> "休"
                    monthWx.overcomeBy() -> "囚"
                    else -> "死"
                }
            }

            val monthZhi = chart.siZhu.month.zhi
            val dayZhi = chart.siZhu.day.zhi
            val kongZhi = chart.siZhu.day.xunKong

            val yuePo = chart.rows.filter { it.ganZhi[1].let(DiZhi::fromChar) == monthZhi.chong() }.map { it.position }
            val riChong = chart.rows.filter { it.ganZhi[1].let(DiZhi::fromChar) == dayZhi.chong() }.map { it.position }
            val xunKongYao = chart.rows.filter {
                it.ganZhi[1].let(DiZhi::fromChar) == kongZhi.first || it.ganZhi[1].let(DiZhi::fromChar) == kongZhi.second
            }.map { it.position }

            // 回头生克：变爻五行对动爻五行
            val huiTou = chart.rows.filter { it.yao.isMoving && it.bianGanZhi != null }.mapNotNull { row ->
                val bianWx = DiZhi.fromChar(row.bianGanZhi!![1]).wuXing
                when (bianWx.relationTo(row.zhiWuXing)) {
                    com.smartwash.divination.core.ShengKeRelation.SHENG_WO -> row.position to "回头生"
                    com.smartwash.divination.core.ShengKeRelation.KE_WO -> row.position to "回头克"
                    else -> null
                }
            }.toMap()

            // 用神：按领域取六亲候选；两现时优先动爻、次取不落旬空；缺六亲取伏神
            var yongPosition: Int? = null
            var yongLabel: String? = null
            var yongNote: String? = null
            val preferred = preferredQin(category)
            if (preferred.isEmpty()) {
                val shiRow = chart.rows.first { it.position == chart.shiYao }
                yongPosition = shiRow.position
                yongLabel = "${shiRow.liuQin}${shiRow.ganZhi}${shiRow.zhiWuXing.label} · 世爻"
                yongNote = "无专属六亲，以世爻为用"
            } else {
                val positionName = { p: Int -> cnPosition(p) }
                outer@ for (qin in preferred) {
                    val candidates = chart.rows.filter { it.liuQin == qin }
                    val picked = candidates.firstOrNull { it.yao.isMoving }
                        ?: candidates.firstOrNull { it.position !in xunKongYao }
                        ?: candidates.firstOrNull()
                    if (picked != null) {
                        yongPosition = picked.position
                        yongLabel = "${picked.liuQin}${picked.ganZhi}${picked.zhiWuXing.label} · ${positionName(picked.position)}爻"
                        val notes = mutableListOf<String>()
                        if (candidates.size > 1) notes.add("两现，取${if (picked.yao.isMoving) "动爻" else "静爻"}")
                        if (picked.position in xunKongYao) notes.add("用神旬空")
                        if (picked.position in yuePo) notes.add("月破")
                        yongNote = notes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                        break@outer
                    }
                    // 装卦缺此六亲 → 取伏神
                    val hiddenSeat = chart.hidden?.seats?.entries?.firstOrNull { it.value.first == qin }
                    if (hiddenSeat != null) {
                        val seat = hiddenSeat.key
                        val pair = hiddenSeat.value
                        yongPosition = seat
                        yongLabel = "伏神 ${pair.first}${pair.second}（伏于${positionName(seat)}爻下）"
                        yongNote = "卦中缺${qin}，取${chart.hidden.gongName}本宫伏神"
                        break@outer
                    }
                }
                if (yongPosition == null) {
                    val shiRow = chart.rows.first { it.position == chart.shiYao }
                    yongPosition = shiRow.position
                    yongLabel = "${shiRow.liuQin}${shiRow.ganZhi}${shiRow.zhiWuXing.label} · 世爻"
                }
            }

            return LiuYaoFacts(
                yongShenPosition = yongPosition,
                yongShenLabel = yongLabel,
                strength = strength,
                yuePo = yuePo,
                riChong = riChong,
                xunKongYao = xunKongYao,
                huiTou = huiTou,
                yongShenNote = yongNote,
            )
        }

        private fun cnPosition(p: Int): String = listOf("初", "二", "三", "四", "五", "上")[p - 1]
    }
}
