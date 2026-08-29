package com.smartwash.divination.core.meihua

import com.nlf.calendar.Solar
import com.smartwash.divination.core.Trigram
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 梅花易数时间起卦差分锚点（oracle：docs/divination/references/verify_meihua_anchor.py）。
 * 锚点输入：2026-08-29 12:00 → 上艮(31%8=7) 下坎(38%8=6) 二爻动(38%6=2) → 山水蒙。
 */
class MeiHuaAnchorTest {

    private val chart: MeiHuaChart by lazy {
        MeiHuaChart.timeChart(Solar.fromYmdHms(2026, 8, 29, 12, 0, 0))
    }

    @Test
    fun `锚点1 上艮下坎二爻动`() {
        assertEquals(Trigram.GEN, chart.upper)
        assertEquals(Trigram.KAN, chart.lower)
        assertEquals(2, chart.movingYao)
    }

    @Test
    fun `锚点2 本卦山水蒙`() {
        assertEquals("山水蒙", chart.benName)
        assertEquals("010001", chart.benMark)
    }

    @Test
    fun `锚点3 互卦地雷复与变卦山地剥`() {
        assertEquals("地雷复", chart.huName)
        assertEquals("100000", chart.huMark)
        assertEquals("山地剥", chart.bianName)
        assertEquals("000001", chart.bianMark)
    }

    @Test
    fun `锚点4 体艮土用坎水体克用`() {
        assertEquals(Trigram.GEN, chart.ti)
        assertEquals(Trigram.KAN, chart.yong)
        assertEquals(TiYongRelation.TI_KE_YONG, chart.tiYongRelation)
    }

    @Test
    fun `数字卦口径一致`() {
        // 同参数下 数字卦(31,7) 与时间卦同构：31%8=7 艮、38%8=6 坎、38%6=2
        val byNumber = MeiHuaChart.numbersChart(31, 7)
        assertEquals("山水蒙", byNumber.benName)
        assertEquals(2, byNumber.movingYao)
    }
}
