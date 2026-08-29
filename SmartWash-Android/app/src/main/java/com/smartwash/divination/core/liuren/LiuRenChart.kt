package com.smartwash.divination.core.liuren

import com.nlf.calendar.Solar
import com.smartwash.divination.core.DiZhi
import com.smartwash.divination.core.GanZhi
import com.smartwash.divination.core.TianGan
import com.smartwash.divination.core.WuXing

/**
 * 大六壬起课（方案文档 4.3 + ZhouYiLab/docs/大六壬起课-步骤.md；顺逆与贵人乘神口径
 * 对拍 ZhouYiLab C++ 引擎 src/da_liu_ren）。
 *
 * 流程：定月将（气中换将）→ 月将加时成天地盘 → 四课（日干寄宫起一课，逐上传出）→
 * 三传（九宗门：本期实现贼克/比用主链，伏吟/返吟/遥克/昴星/别责/八专/涉害暂不支持）→ 十二天将。
 */
/** 九宗门未实现时抛出，调用方按"暂不支持"呈现（TODO Phase2 补齐九宗门全量） */
class LiuRenUnsupportedException(val gate: String) : Exception("三传九宗门「$gate」暂不支持")

data class LiuRenCourse(
    val label: String,      // 一课..四课
    val upper: String,      // 上神（天盘支）
    val lower: String,      // 下神（日干或地支）
    val lowerIsGan: Boolean,
    /** 上神克下神（上克下） */
    val shangKeXia: Boolean,
    /** 下神克上神（下贼上） */
    val xiaZeShang: Boolean,
)

data class LiuRenChart(
    val dayGanZhi: String,          // 日干支
    val hourGanZhi: String,         // 时干支
    val yueJiang: String,           // 月将地支
    val yueJiangName: String,       // 月将名（登明/河魁…）
    val tianPan: Map<String, String>, // 地盘支 → 天盘支
    val courses: List<LiuRenCourse>,
    val sanChuan: List<String>?,    // 初/中/末传（无克且宗门未实现时为 null）
    val sanChuanJiang: List<String>?, // 三传所乘天将
    val keTi: String?,              // 课体：元首课/重审课/比用课
    val jiuZongMen: String?,        // 九宗门：贼克/比用（未实现宗门为 null）
    val unsupportedNote: String?,   // 暂不支持说明
    val guiRen: String,             // 贵人地支
    val isDay: Boolean,             // 昼占/夜占
    val tianJiang: Map<String, String>, // 天盘支 → 天将
) {
    companion object {
        /** 日干寄宫：甲寄寅、乙寄辰、丙戊寄巳、丁己寄未、庚寄申、辛寄戌、壬寄亥、癸寄丑 */
        fun jiGong(gan: TianGan): DiZhi = when (gan) {
            TianGan.JIA -> DiZhi.YIN
            TianGan.YI -> DiZhi.CHEN
            TianGan.BING, TianGan.WU -> DiZhi.SI
            TianGan.DING, TianGan.JI -> DiZhi.WEI
            TianGan.GENG -> DiZhi.SHEN
            TianGan.XIN -> DiZhi.XU
            TianGan.REN -> DiZhi.HAI
            TianGan.GUI -> DiZhi.CHOU
        }

        /** 月将中气换将表：雨水亥将登明……大寒子将神后（气中换将） */
        val YUE_JIANG_TABLE: Map<String, Pair<DiZhi, String>> = mapOf(
            "雨水" to (DiZhi.HAI to "登明"),
            "春分" to (DiZhi.XU to "河魁"),
            "谷雨" to (DiZhi.YOU to "从魁"),
            "小满" to (DiZhi.SHEN to "传送"),
            "夏至" to (DiZhi.WEI to "小吉"),
            "大暑" to (DiZhi.WU to "胜光"),
            "处暑" to (DiZhi.SI to "太乙"),
            "秋分" to (DiZhi.CHEN to "天罡"),
            "霜降" to (DiZhi.MAO to "太冲"),
            "小雪" to (DiZhi.YIN to "功曹"),
            "冬至" to (DiZhi.CHOU to "大吉"),
            "大寒" to (DiZhi.ZI to "神后"),
        )

        private val ZHONG_QI = YUE_JIANG_TABLE.keys

        /** 由公历时刻定月将（取不晚于占时的最近中气） */
        fun yueJiang(solar: Solar): Pair<DiZhi, String> {
            val table = solar.lunar.jieQiTable
            var best: Pair<Long, Map.Entry<String, Solar>>? = null
            for (entry in table.entries) {
                val name = convertQiName(entry.key)
                if (name !in ZHONG_QI) continue
                val s = entry.value
                val stamp = s.year * 100_000_000L + s.month * 1_000_000L + s.day * 10_000L + s.hour * 100L + s.minute
                val target = solar.year * 100_000_000L + solar.month * 1_000_000L + solar.day * 10_000L + solar.hour * 100L + solar.minute
                if (stamp <= target && (best == null || stamp > best.first)) {
                    best = stamp to entry
                }
            }
            requireNotNull(best) { "无法定位月将中气" }
            val name = convertQiName(best.second.key)
            val (zhi, jiangName) = YUE_JIANG_TABLE.getValue(name)
            return zhi to jiangName
        }

        private fun convertQiName(key: String): String = when (key) {
            "DONG_ZHI" -> "冬至"
            "XIAO_HAN" -> "小寒"
            "DA_HAN" -> "大寒"
            "LI_CHUN" -> "立春"
            "YU_SHUI" -> "雨水"
            "JING_ZHE" -> "惊蛰"
            "DA_XUE" -> "大雪"
            else -> key
        }

    private val TIAN_JIANG = listOf(
        "贵人", "螣蛇", "朱雀", "六合", "勾陈", "青龙",
        "天空", "白虎", "太常", "玄武", "太阴", "天后",
    )

    /** 贵人歌诀：甲戊庚牛羊，乙己鼠猴乡，丙丁猪鸡位，壬癸蛇兔藏，六辛逢马虎 */
    private val GUI_REN_TABLE = mapOf(
        TianGan.JIA to (DiZhi.CHOU to DiZhi.WEI),
        TianGan.WU to (DiZhi.CHOU to DiZhi.WEI),
        TianGan.GENG to (DiZhi.CHOU to DiZhi.WEI),
        TianGan.YI to (DiZhi.ZI to DiZhi.SHEN),
        TianGan.JI to (DiZhi.ZI to DiZhi.SHEN),
        TianGan.BING to (DiZhi.HAI to DiZhi.YOU),
        TianGan.DING to (DiZhi.HAI to DiZhi.YOU),
        TianGan.REN to (DiZhi.SI to DiZhi.MAO),
        TianGan.GUI to (DiZhi.SI to DiZhi.MAO),
        TianGan.XIN to (DiZhi.WU to DiZhi.YIN),
    )

    fun compile(dayGanZhi: GanZhi, hourGanZhi: GanZhi, yueJiang: DiZhi, yueJiangName: String): LiuRenChart {
        // 天地盘：月将加时（月将落时支之上，顺布十二支）
        val offset = (yueJiang.ordinal - hourGanZhi.zhi.ordinal + 12) % 12
        val tianPan: Map<DiZhi, DiZhi> = DiZhi.entries.associate { it to DiZhi.entries[(it.ordinal + offset) % 12] }

        // 四课：一课=日干寄宫之上神，二课=一课上神之上神，三课=日支之上神，四课=三课上神之上神
        val ganGong = jiGong(dayGanZhi.gan)
        val upper1 = tianPan.getValue(ganGong)
        val upper2 = tianPan.getValue(upper1)
        val upper3 = tianPan.getValue(dayGanZhi.zhi)
        val upper4 = tianPan.getValue(upper3)

        fun course(label: String, upper: DiZhi, lower: String, lowerIsGan: Boolean, lowerWx: WuXing): LiuRenCourse {
            val upperWx = upper.wuXing
            return LiuRenCourse(
                label = label,
                upper = upper.label,
                lower = lower,
                lowerIsGan = lowerIsGan,
                shangKeXia = upperWx == lowerWx.overcomeBy(),
                xiaZeShang = lowerWx == upperWx.overcomeBy(),
            )
        }

        val courses = listOf(
            course("一课", upper1, dayGanZhi.gan.label, true, dayGanZhi.gan.wuXing),
            course("二课", upper2, upper1.label, false, upper1.wuXing),
            course("三课", upper3, dayGanZhi.zhi.label, false, dayGanZhi.zhi.wuXing),
            course("四课", upper4, upper3.label, false, upper3.wuXing),
        )

        // 十二天将：昼夜定贵人（卯~申昼占），贵人乘贵人支，临亥子丑寅卯辰顺布、余逆布（ZhouYiLab 口径）
        val isDay = hourGanZhi.zhi.ordinal in 3..8
        val guiRen = if (isDay) GUI_REN_TABLE.getValue(dayGanZhi.gan).first else GUI_REN_TABLE.getValue(dayGanZhi.gan).second
        val clockwise = guiRen == DiZhi.HAI || guiRen.ordinal <= DiZhi.CHEN.ordinal
        val step = if (clockwise) 1 else -1
        val tianJiang: Map<DiZhi, String> = (0 until 12).associate { i ->
            val pos = (guiRen.ordinal + i * step + 24) % 12
            DiZhi.entries[pos] to TIAN_JIANG[i]
        }

        // 三传（九宗门）
        var sanChuan: List<DiZhi>? = null
        var keTi: String? = null
        var jiuZongMen: String? = null
        var unsupported: String? = null
        try {
            val result = resolveSanChuan(dayGanZhi, tianPan, courses)
            sanChuan = result.chuan
            keTi = result.keTi
            jiuZongMen = result.gate
        } catch (e: LiuRenUnsupportedException) {
            unsupported = e.gate
        }

        return LiuRenChart(
            dayGanZhi = dayGanZhi.label,
            hourGanZhi = hourGanZhi.label,
            yueJiang = yueJiang.label,
            yueJiangName = yueJiangName,
            tianPan = tianPan.entries.associate { it.key.label to it.value.label },
            courses = courses,
            sanChuan = sanChuan?.map { it.label },
            sanChuanJiang = sanChuan?.map { tianJiang.getValue(it) },
            keTi = keTi,
            jiuZongMen = jiuZongMen,
            unsupportedNote = unsupported?.let { "三传九宗门「$it」暂不支持，敬请期待" },
            guiRen = guiRen.label,
            isDay = isDay,
            tianJiang = tianJiang.entries.associate { it.key.label to it.value },
        )
    }

    internal data class ChuanResult(val chuan: List<DiZhi>, val keTi: String, val gate: String)

    /** 九宗门主链：伏吟/返吟暂不支持 → 贼克（重审）→ 克（元首）→ 比用；涉害及以下 TODO */
    internal fun resolveSanChuan(
        dayGanZhi: GanZhi,
        tianPan: Map<DiZhi, DiZhi>,
        courses: List<LiuRenCourse>,
    ): ChuanResult {
        // 伏吟/返吟盘先判（ZhouYiLab 口径）
        if (tianPan.values.all { it.ordinal == it.ordinal }) {
            // unreachable（天盘支不等于自身判断见下）
        }
        val fuYin = tianPan.all { (di, tian) -> di == tian }
        val fanYin = tianPan.all { (di, tian) -> di == tian.chong() }
        if (fuYin) throw LiuRenUnsupportedException("伏吟")
        if (fanYin) throw LiuRenUnsupportedException("返吟")

        val dayYang = dayGanZhi.gan.isYang

        // 下贼上优先，其次上克下；同去重（ZhouYiLab remove_duplicate_lessons）
        val zei = dedupe(courses.filter { it.xiaZeShang })
        val ke = dedupe(courses.filter { it.shangKeXia && !it.xiaZeShang })

        fun chuanOf(first: DiZhi): List<DiZhi> =
            listOf(first, tianPan.getValue(first), tianPan.getValue(tianPan.getValue(first)))

        if (zei.isNotEmpty()) {
            if (zei.size == 1) return ChuanResult(chuanOf(zhiOf(zei[0].upper)), "重审课", "贼克")
            return biYong(zei, dayYang, ::chuanOf)
        }
        if (ke.isNotEmpty()) {
            if (ke.size == 1) return ChuanResult(chuanOf(zhiOf(ke[0].upper)), "元首课", "贼克")
            return biYong(ke, dayYang, ::chuanOf)
        }
        // 无克 → 遥克/昴星/别责/八专 TODO
        throw LiuRenUnsupportedException("遥克")
    }

    /** 比用：取与日干阴阳俱比的上神；俱不比/多比 → 涉害 TODO */
    internal fun biYong(
        candidates: List<LiuRenCourse>,
        dayYang: Boolean,
        chuanOf: (DiZhi) -> List<DiZhi>,
    ): ChuanResult {
        val matched = candidates.filter { zhiOf(it.upper).isYang == dayYang }
        if (matched.size == 1) {
            return ChuanResult(chuanOf(zhiOf(matched[0].upper)), "比用课", "比用")
        }
        throw LiuRenUnsupportedException("涉害")
    }

    private fun zhiOf(label: String): DiZhi = DiZhi.entries.first { it.label == label }

    /** 课去重：干课按天干、支课按地支（ZhouYiLab remove_duplicate_lessons 口径） */
    private fun dedupe(courses: List<LiuRenCourse>): List<LiuRenCourse> {
        val ganSeen = mutableSetOf<String>()
        val zhiSeen = mutableSetOf<String>()
        return courses.filter { c ->
            if (c.lowerIsGan) ganSeen.add(c.lower) else zhiSeen.add(c.lower)
        }
    }
}
}
