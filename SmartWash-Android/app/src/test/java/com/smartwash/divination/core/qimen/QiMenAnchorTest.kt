package com.smartwash.divination.core.qimen

import com.nlf.calendar.Solar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 奇门遁甲排盘差分锚点（oracle：demo/qimen/verify_qimen.js，qfdk/qimen 转盘排法）。
 * 锚点输入：2026-08-29 12:00 → 阴遁七局下元（处暑 147、符头甲戌），旬首己。
 * 全链路落宫逐宫断言（地盘戊落七宫、天盘戊落八宫、值符落三宫、生门落一宫）。
 */
class QiMenAnchorTest {

    private val chart: QiMenChart by lazy {
        QiMenChart.compile(Solar.fromYmdHms(2026, 8, 29, 12, 0, 0))
    }

    @Test
    fun `锚点1 定局阴遁七局下元旬首己`() {
        assertEquals("处暑", chart.jieQiName)
        assertEquals("阴遁", chart.yinYang)
        assertEquals(7, chart.juNumber)
        assertEquals("下元", chart.yuan)
        assertEquals("己", chart.xunShouYi)
        assertEquals("丙午", chart.siZhu.year.label)
        assertEquals("丙申", chart.siZhu.month.label)
        assertEquals("乙亥", chart.siZhu.day.label)
        assertEquals("壬午", chart.siZhu.hour.label)
    }

    @Test
    fun `锚点2 地盘三奇六仪逐宫`() {
        // 阴遁七局：戊从七宫起逆飞
        val expected = mapOf(1 to "丁", 2 to "癸", 3 to "壬", 4 to "辛", 5 to "庚", 6 to "己", 7 to "戊", 8 to "乙", 9 to "丙")
        assertEquals(expected, chart.diPan)
        assertEquals(7, chart.diPan.entries.first { it.value == "戊" }.key)
    }

    @Test
    fun `锚点3 天盘转盘逐宫`() {
        val expected = mapOf(1 to "癸", 2 to "壬", 3 to "己", 4 to "丁", 5 to "庚", 6 to "丙", 7 to "辛", 8 to "戊", 9 to "乙")
        assertEquals(expected, chart.tianPan)
        // 天盘戊落八宫；值符（旬首仪己）随时干壬落三宫
        assertEquals(8, chart.tianPan.entries.first { it.value == "戊" }.key)
        assertEquals(3, chart.zhiFuLuoGong)
        assertEquals(6, chart.zhiFuYuanGong)
        assertEquals("天心", chart.zhiFuXing)
    }

    @Test
    fun `锚点4 九星八门八神逐宫`() {
        assertEquals(mapOf(1 to "禽芮", 2 to "天冲", 3 to "天心", 4 to "天蓬", 5 to "", 6 to "天英", 7 to "天辅", 8 to "天柱", 9 to "天任"), chart.jiuXing)
        assertEquals(mapOf(1 to "生门", 2 to "惊门", 3 to "杜门", 4 to "景门", 5 to "", 6 to "休门", 7 to "开门", 8 to "伤门", 9 to "死门"), chart.baMen)
        assertEquals(mapOf(1 to "太阴", 2 to "玄武", 3 to "值符", 4 to "九天", 5 to "", 6 to "六合", 7 to "白虎", 8 to "腾蛇", 9 to "九地"), chart.baShen)
        // 生门落一宫；值使门开门落七宫
        assertEquals("生门", chart.baMen.getValue(1))
        assertEquals("开门", chart.zhiShiMen)
        assertEquals(7, chart.zhiShiGong)
    }

    @Test
    fun `锚点5 暗干逐宫`() {
        assertEquals(mapOf(1 to "己", 2 to "戊", 3 to "乙", 4 to "丙", 5 to "丁", 6 to "癸", 7 to "壬", 8 to "辛", 9 to "庚"), chart.anGan)
    }

    @Test
    fun `锚点6 空亡驿马`() {
        assertEquals(listOf("申", "酉"), chart.kongWangZhi)
        assertEquals(listOf(2, 7), chart.kongWangGong)
        assertEquals("申", chart.maZhi)
        assertEquals(2, chart.maGong)
    }
}
