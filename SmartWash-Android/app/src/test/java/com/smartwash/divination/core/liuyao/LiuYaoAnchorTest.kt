package com.smartwash.divination.core.liuyao

import com.nlf.calendar.Solar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 六爻纳甲装卦差分锚点（oracle：docs/divination/references/verify_paipan_anchor.py，najia 实现）。
 * 锚点输入：爻值 [2,2,1,4,2,1]（单拆重交）@ 2026-08-29 12:00。
 * 14 个数据点逐项断言，任何改动必须先跑本测试对拍。
 */
class LiuYaoAnchorTest {

    private val chart: LiuYaoChart by lazy {
        LiuYaoChart.compile(listOf(2, 2, 1, 4, 2, 1), Solar.fromYmdHms(2026, 8, 29, 12, 0, 0))
    }

    @Test
    fun `锚点1 本卦名与卦宫`() {
        assertEquals("艮为山", chart.name)
        assertEquals("艮", chart.gong)
    }

    @Test
    fun `锚点2 本卦六冲`() {
        assertEquals("六冲", chart.type)
    }

    @Test
    fun `锚点3 变卦火山旅离宫六合`() {
        assertEquals("火山旅", chart.bianName)
        assertEquals("离", chart.bianGong)
        assertEquals("六合", chart.bianType)
    }

    @Test
    fun `锚点4 四柱`() {
        assertEquals("丙午", chart.siZhu.year.label)
        assertEquals("丙申", chart.siZhu.month.label)
        assertEquals("乙亥", chart.siZhu.day.label)
        assertEquals("壬午", chart.siZhu.hour.label)
    }

    @Test
    fun `锚点5 旬空申酉`() {
        assertEquals("申酉", chart.xunKong)
    }

    @Test
    fun `锚点6 世上应三`() {
        assertEquals(6, chart.shiYao)
        assertEquals(3, chart.yingYao)
    }

    @Test
    fun `锚点7 六亲干支逐爻`() {
        val expected = listOf(
            "兄弟" to "丙辰土",
            "父母" to "丙午火",
            "子孙" to "丙申金",
            "兄弟" to "丙戌土",
            "妻财" to "丙子水",
            "官鬼" to "丙寅木",
        )
        expected.forEachIndexed { index, (qin, gz) ->
            val row = chart.rows[index]
            assertEquals("第${index + 1}爻六亲", qin, row.liuQin)
            assertEquals("第${index + 1}爻干支", gz, row.ganZhi + row.zhiWuXing.label)
        }
    }

    @Test
    fun `锚点8 世应标记落位`() {
        assertNull(chart.rows[0].shiYing)
        assertEquals("应", chart.rows[2].shiYing)
        assertEquals("世", chart.rows[5].shiYing)
    }

    @Test
    fun `锚点9 动爻为四爻老阴`() {
        assertEquals(listOf(4), chart.movingPositions)
        assertEquals("老阴", chart.rows[3].yao.label)
        assertEquals(4, chart.rows[3].yao.code)
    }

    @Test
    fun `锚点10 六神自下而上`() {
        val expected = listOf("青龙", "朱雀", "勾陈", "螣蛇", "白虎", "玄武")
        expected.forEachIndexed { index, shen ->
            assertEquals("第${index + 1}爻六神", shen, chart.rows[index].liuShen)
        }
    }

    @Test
    fun `锚点11 动爻化出子孙酉金回头生`() {
        assertEquals("己酉", chart.rows[3].bianGanZhi)
        assertEquals("子孙", chart.rows[3].bianLiuQin)
    }

    @Test
    fun `锚点12 装卦六亲齐全无伏神`() {
        assertNull(chart.hidden)
    }

    @Test
    fun `锚点13 变卦六亲以本宫论`() {
        val bianRows = requireNotNull(chart.bianRows)
        // 动爻位（四爻）变出 己酉 子孙，其余行保持本卦纳甲
        assertEquals("己酉", bianRows[3].ganZhi)
        assertEquals(true, bianRows[3].isChanged)
        assertEquals("丙辰", bianRows[0].ganZhi)
        assertEquals(false, bianRows[0].isChanged)
    }

    @Test
    fun `锚点14 规则层事实标记`() {
        val facts = LiuYaoFacts.of(chart, com.smartwash.divination.core.DivCategory.CAREER)
        // 月建申金：五爻子水得令生 → 相
        assertEquals("相", facts.strength[5])
        // 旬空申酉：三爻申金、四爻? 戌不空 → 三爻旬空
        assertEquals(listOf(3), facts.xunKongYao)
        // 月建申金冲寅：上爻官鬼寅木月破；日支亥冲巳无爻
        assertEquals(listOf(6), facts.yuePo)
        assertEquals(emptyList<Int>(), facts.riChong)
        // 四爻戌土动化酉金 → 回头生
        assertEquals("回头生", facts.huiTou[4])
    }
}
