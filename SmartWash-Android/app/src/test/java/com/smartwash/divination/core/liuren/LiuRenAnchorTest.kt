package com.smartwash.divination.core.liuren

import com.smartwash.divination.core.DiZhi
import com.smartwash.divination.core.GanZhi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 大六壬起课锚点（课例：ZhouYiLab/docs/大六壬起课-步骤.md 辛卯日乙未时寅将实例）。
 * 三传九宗门本期实现贼克/比用主链；涉害/遥克等抛"暂不支持"。
 */
class LiuRenAnchorTest {

    /** 文档实例：甲辰年 乙亥月 辛卯日 乙未时，月将寅（功曹，小雪后亥月） */
    private val chart: LiuRenChart by lazy {
        LiuRenChart.compile(
            dayGanZhi = GanZhi.parse("辛卯"),
            hourGanZhi = GanZhi.parse("乙未"),
            yueJiang = DiZhi.YIN,
            yueJiangName = "功曹",
        )
    }

    @Test
    fun `锚点1 月将加时天地盘`() {
        assertEquals("寅", chart.yueJiang)
        // 寅加未上：天盘(未)=寅、天盘(戌)=巳、天盘(巳)=子、天盘(卯)=戌
        assertEquals("寅", chart.tianPan.getValue("未"))
        assertEquals("巳", chart.tianPan.getValue("戌"))
        assertEquals("子", chart.tianPan.getValue("巳"))
        assertEquals("戌", chart.tianPan.getValue("卯"))
    }

    @Test
    fun `锚点2 四课巳辛子巳戌卯巳戌`() {
        val expected = listOf(
            "巳" to "辛",
            "子" to "巳",
            "戌" to "卯",
            "巳" to "戌",
        )
        expected.forEachIndexed { index, (upper, lower) ->
            val course = chart.courses[index]
            assertEquals("第${index + 1}课上神", upper, course.upper)
            assertEquals("第${index + 1}课下神", lower, course.lower)
        }
        // 一课为干课（辛寄戌）
        assertEquals(true, chart.courses[0].lowerIsGan)
        // 三课下神为日支卯
        assertEquals("卯", chart.courses[2].lower)
    }

    @Test
    fun `锚点3 三传重审课戌巳子`() {
        // 下贼上唯一（三课卯木克戌土）→ 重审课；中末传依次天盘传出
        assertEquals(listOf("戌", "巳", "子"), chart.sanChuan)
        assertEquals("重审课", chart.keTi)
        assertEquals("贼克", chart.jiuZongMen)
        assertEquals(null, chart.unsupportedNote)
    }

    @Test
    fun `锚点4 昼占贵人与十二天将`() {
        // 未时昼占，辛日昼贵在午；贵人临午（巳午未申酉戌）→ 逆布
        assertEquals(true, chart.isDay)
        assertEquals("午", chart.guiRen)
        assertEquals("贵人", chart.tianJiang.getValue("午"))
        assertEquals("螣蛇", chart.tianJiang.getValue("巳"))
        // 三传所乘：戌乘太常、巳乘螣蛇、子乘天空
        assertEquals(listOf("太常", "螣蛇", "天空"), chart.sanChuanJiang)
    }

    @Test
    fun `比用课甲辰日丑时子将`() {
        // 甲辰日丑时子将：三处下贼上（丑/甲、子/丑、卯/辰），甲日阳干比用取阳支子 → 初传子
        val chart = LiuRenChart.compile(
            dayGanZhi = GanZhi.parse("甲辰"),
            hourGanZhi = GanZhi.parse("乙丑"),
            yueJiang = DiZhi.ZI,
            yueJiangName = "神后",
        )
        assertEquals(true, chart.courses[0].xiaZeShang)
        assertEquals(listOf("子", "亥", "戌"), chart.sanChuan)
        assertEquals("比用课", chart.keTi)
    }

    @Test
    fun `元首课甲子日寅时午将`() {
        // 甲子日寅时午将：上克下唯一（辰土克子水）→ 元首课，三传辰申寅
        val chart = LiuRenChart.compile(
            dayGanZhi = GanZhi.parse("甲子"),
            hourGanZhi = GanZhi.parse("丙寅"),
            yueJiang = DiZhi.WU,
            yueJiangName = "胜光",
        )
        assertEquals("辰", chart.courses[2].upper)
        assertEquals(true, chart.courses[2].shangKeXia)
        assertEquals(listOf("辰", "申", "子"), chart.sanChuan)
        assertEquals("元首课", chart.keTi)
    }

    @Test
    fun `比用取与日干俱比之上神`() {
        // 合成课例：两处上克下（申金克甲木、丑土克子水），甲日阳干 → 比用取阳支申
        val tianPan = DiZhi.entries.associate { it to DiZhi.entries[(it.ordinal + 2) % 12] }
            .mapValues { it.value }
        val courses = listOf(
            LiuRenCourse("一课", "申", "甲", true, shangKeXia = true, xiaZeShang = false),
            LiuRenCourse("三课", "丑", "子", false, shangKeXia = true, xiaZeShang = false),
        )
        val result = LiuRenChart.resolveSanChuan(GanZhi.parse("甲子"), tianPan, courses)
        assertEquals("比用课", result.keTi)
        assertEquals(listOf(DiZhi.SHEN, DiZhi.XU, DiZhi.ZI), result.chuan)
    }

    @Test
    fun `俱比涉害暂不支持`() {
        // 两处下贼上且上神俱阳（辰、戌）→ 涉害（本期 TODO）
        val tianPan = DiZhi.entries.associate { it to DiZhi.entries[(it.ordinal + 2) % 12] }
        val courses = listOf(
            LiuRenCourse("一课", "辰", "甲", true, shangKeXia = false, xiaZeShang = true),
            LiuRenCourse("四课", "戌", "寅", false, shangKeXia = false, xiaZeShang = true),
        )
        try {
            LiuRenChart.resolveSanChuan(GanZhi.parse("甲子"), tianPan, courses)
            throw AssertionError("应抛出涉害暂不支持")
        } catch (e: LiuRenUnsupportedException) {
            assertEquals("涉害", e.gate)
        }
    }

    @Test
    fun `伏吟返吟暂不支持`() {
        // 伏吟：月将等于占时（寅将寅时）→ compile 不抛出，以 unsupportedNote 呈现
        val fuYin = LiuRenChart.compile(GanZhi.parse("甲寅"), GanZhi.parse("丙寅"), DiZhi.YIN, "功曹")
        assertEquals(null, fuYin.sanChuan)
        assertEquals(true, fuYin.unsupportedNote?.contains("伏吟") == true)

        // 返吟：月将临冲位（子将午时）
        val fanYin = LiuRenChart.compile(GanZhi.parse("甲辰"), GanZhi.parse("庚午"), DiZhi.ZI, "神后")
        assertEquals(null, fanYin.sanChuan)
        assertEquals(true, fanYin.unsupportedNote?.contains("返吟") == true)
    }
}
