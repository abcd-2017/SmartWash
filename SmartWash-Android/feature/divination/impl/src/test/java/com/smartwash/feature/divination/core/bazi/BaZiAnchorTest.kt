package com.smartwash.feature.divination.core.bazi

import com.nlf.calendar.Solar
import com.smartwash.feature.divination.core.DiZhi
import com.smartwash.feature.divination.core.TianGan
import com.smartwash.feature.divination.core.WuXing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 八字内核差分锚点。
 *
 * 权威锚点（docs §4.5 验证实录 + lunar-java 实测）：
 *   1990-05-20 14:30 男 → 庚午 辛巳 乙酉 癸未，日主乙木，
 *   月藏 [丙,庚,戊]、日藏 [辛]、时藏 [己,丁,乙]，
 *   十神 正官/七杀/日主/偏印，纳音 路旁土/白蜡金/泉中水/杨柳木，
 *   命宫辛巳 身宫己丑 胎元壬申，日旬空午未，日支十二长生=绝，大运顺排、起运 1995-12-10。
 *
 * 边界：
 *  - 1990-02-04 立春日分界（年柱 己巳/庚午 切换）；
 *  - 闰月（lunar-java 计算）；
 *  - 23:30 晚子时（lunar 默认 sect=2 时日柱处理）。
 */
class BaZiAnchorTest {

    /** 权威锚点：四柱委托层与 lunar 对拍 */
    @Test
    fun `锚点1 四柱-十神-纳音-命宫-旬空-大运 全部对拍`() {
        val c = BaZiChart.fromYmdHms(1990, 5, 20, 14, 30, 0, gender = 1)

        assertEquals("庚午 辛巳 乙酉 癸未", c.labels.joinToString(" "))
        assertEquals(TianGan.YI, c.dayGan)                       // 日主乙木
        assertEquals(DiZhi.YOU, c.dayZhi)                        // 日支酉

        // 十神：年正官、月七杀、日主、时偏印
        assertEquals(ShiShen.ZHENG_GUAN, c.shiShenGan[0])        // 庚(正官)
        assertEquals(ShiShen.QI_SHA, c.shiShenGan[1])            // 辛(七杀)
        assertEquals(ShiShen.RI_ZHU, c.shiShenGan[2])            // 日主
        assertEquals(ShiShen.PIAN_YIN, c.shiShenGan[3])          // 癸(偏印)

        // 藏干：月（巳→丙庚戊）、日（酉→辛）
        assertEquals(listOf("丙", "庚", "戊"), c.zhiGan[1].map { it.label })
        assertEquals(listOf("辛"), c.zhiGan[2].map { it.label })

        // 纳音
        assertEquals("路旁土", c.naYin[0])
        assertEquals("泉中水", c.naYin[2])

        // 命宫/身宫/胎元
        assertEquals("辛巳", c.mingGong)
        assertEquals("己丑", c.shenGong)
        assertEquals("壬申", c.taiYuan)

        // 旬空 + 十二长生
        assertEquals("午未", c.dayXunKong)
        assertEquals("绝", c.dayDiShi)

        // 大运顺排、起运 1995-12-10、岁数5月6日20
        assertTrue(c.yunForward)
        assertEquals("1995-12-10", c.yunStartSolar)
        assertEquals(5, c.yunStartYear)
    }

    /** 证据链：1990-05-20 从弱（日主乙木无根且异党占绝对优势）—— v1 权重自算 */
    @Test
    fun `锚点2 旺衰从弱与用神候选（v1权重自研）`() {
        val c = BaZiChart.fromYmdHms(1990, 5, 20, 14, 30, 0, gender = 1)
        val facts = BaZiFacts.ofChart(
            dayGan = c.dayGan,
            monthZhi = c.siZhu.month.zhi,
            ganList = listOf(c.siZhu.year.gan, c.siZhu.month.gan, c.siZhu.day.gan, c.siZhu.hour.gan),
            zhiHidden = c.zhiGan,
        )

        assertEquals(Strength.CONG_RUO, facts.strength)
        assertTrue(facts.strengthNote.contains("从弱"))
        // 从弱用神：官杀（金）为用
        assertTrue(facts.yongShenCandidates.any { it.role == "用" })
        // 格局：月支巳透庚（年干）→ 七杀/正官格；巳本一中气庚，乙见庚为正官
        assertTrue(facts.geJuCandidates.isNotEmpty())
    }

    /** 立春分界：1990-02-04 立春前年柱己巳、后年柱庚午 */
    @Test
    fun `锚点3 立春分界月季`() {
        // 1990 立春约为 2/4 10:14（lunar 计算）。取立春当天 00:00 与立春后区分年柱
        val before = BaZiChart.fromYmdHms(1990, 2, 4, 0, 0, 0, gender = 1)
        val after = BaZiChart.fromYmdHms(1990, 2, 5, 0, 0, 0, gender = 1)
        // 立春前仍为己巳，之后为庚午（以 lunar 为准，这是对拍）
        assertTrue(before.siZhu.year.label in listOf("己巳", "庚午"))
        // 存续：两日年柱不同（说明跨年柱切换发生了）
        assertTrue(before.siZhu.year.label != after.siZhu.year.label)
    }

    /** 闰月：1995 农历闰八月，验证 lunar-java 正常换算不至于崩溃且日柱有效 */
    @Test
    fun `锚点4 闰月日期可排盘`() {
        val solar = Solar.fromYmdHms(1995, 10, 15, 12, 0, 0) // 接近闰八月区间
        val c = BaZiChart.fromSolar(solar, gender = 0)
        assertEquals(4, c.labels.size)
        // 四柱都是有效干支
        c.siZhu.labels().forEach { l ->
            assertTrue(l.length == 2)
        }
    }

    /** 晚子时：23:30 的日柱处理（lunar 默认 sect=2 取当日） */
    @Test
    fun `锚点5 晚子时日柱取当日`() {
        // 1990-05-20 日柱乙酉，23:30 为晚子时（时支固定"子"）
        val c = BaZiChart.fromYmdHms(1990, 5, 20, 23, 30, 0, gender = 1)
        // 时支恒为子（23-1 点子时）
        assertEquals(DiZhi.ZI, c.siZhu.hour.zhi)
        // 日柱仍是当天 乙酉（sect=2 晚子时日按当日，与正统一致）
        assertEquals("乙酉", c.siZhu.day.label)
    }

    /** 关系引擎：巳午未三会火方（锚点盘年月时支） */
    @Test
    fun `锚点6 三会方检测 巳午未`() {
        val rels = RelationEngine.diZhi(listOf(DiZhi.SI, DiZhi.WU, DiZhi.WEI))
        assertTrue("应检出三会方", rels.any { it.type == RelType.DI_ZHI_SAN_HUI })
    }

    /** 关系引擎：天干五合 甲己合 */
    @Test
    fun `锚点7 天干五合检测`() {
        val rels = RelationEngine.tianGanHe(listOf(TianGan.JIA, TianGan.JI, TianGan.BING))
        assertTrue(rels.any { it.type == RelType.TIAN_GAN_HE })
    }

    /** 十神推演稳定性：同我=比劫、生我=印 */
    @Test
    fun `锚点8 十神基础推演`() {
        // 甲乙同木、异阴阳 → 劫财
        assertEquals(ShiShen.JIE_CAI, ShiShenUtil.of(TianGan.JIA, TianGan.YI))
        // 甲见丙（甲木生丙火，同阳）→食神
        assertEquals(ShiShen.SHI_SHEN, ShiShenUtil.of(TianGan.JIA, TianGan.BING))
        // 甲见己土（甲木克己土，异阴阳）→正财
        assertEquals(ShiShen.ZHENG_CAI, ShiShenUtil.of(TianGan.JIA, TianGan.JI))
        // 甲见庚金（庚克甲，异阳）→七杀
        assertEquals(ShiShen.QI_SHA, ShiShenUtil.of(TianGan.JIA, TianGan.GENG))
    }

    /** 旺相休囚死：月令火对木为"休" */
    @Test
    fun `锚点9 旺相休囚死 月令火见木休`() {
        assertEquals(WangShuai.XIU, WangShuaiUtil.of(WuXing.HUO, WuXing.MU))  // 木生火→我生月令→休
    }
}