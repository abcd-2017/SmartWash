package com.smartwash.divination.core.liuyao

import com.smartwash.divination.core.DiZhi
import com.smartwash.divination.core.GanZhi
import com.smartwash.divination.core.SiZhu
import com.smartwash.divination.core.Trigram
import com.smartwash.divination.core.WuXing
import com.smartwash.divination.core.YaoValue
import com.nlf.calendar.Solar

/**
 * 六爻纳甲装卦（确定性，零 AI）。
 *
 * 流程（方案文档第三章）：本卦 → 寻世诀安世应 → 认宫诀定宫 → 纳甲干支 → 六亲 →
 * 按日干起六神 → 缺六亲取伏神 → 六冲/六合/游魂/归魂 → 动爻取反得变卦。
 *
 * 爻值编码：1=少阳 2=少阴 3=老阳○(动) 4=老阴×(动)；自初爻到上爻。
 */
data class LiuYaoLine(
    val position: Int,          // 1..6 自下而上
    val yao: YaoValue,
    val ganZhi: String,         // 纳甲干支，如 丙辰
    val zhiWuXing: WuXing,      // 爻支五行
    val liuQin: String,         // 六亲（以本宫五行为我）
    val liuShen: String,        // 六神（按日干起）
    val shiYing: String? = null, // "世" / "应"
    val bianGanZhi: String? = null, // 动爻变出干支
    val bianLiuQin: String? = null, // 变爻六亲（以本宫五行论）
)

/** 变卦行（六亲以本卦宫论，静态行与动变行区分展示由 UI 决定） */
data class LiuYaoBianLine(
    val position: Int,
    val ganZhi: String,
    val liuQin: String,
    val isChanged: Boolean,
)

/** 伏神：本宫纳甲中缺失六亲的藏伏之爻 */
data class LiuYaoHidden(
    val gongName: String,       // 本宫卦名（如 艮为山）
    val seats: Map<Int, Pair<String, String>>, // 爻位(1..6) → (六亲, 干支)
)

data class LiuYaoChart(
    val lines: List<Int>,               // 原始爻值 1..4
    val mark: String,                   // 二进制卦码（初爻在前）
    val name: String,                   // 本卦名
    val gong: String,                   // 卦宫名（艮）
    val gongWuXing: WuXing,             // 宫五行
    val type: String,                   // 六冲/六合/游魂/归魂/""（najia 口径，单标签）
    val shiYao: Int,                    // 世爻位 1..6
    val yingYao: Int,                   // 应爻位
    val rows: List<LiuYaoLine>,
    val movingPositions: List<Int>,     // 动爻位（1..6）
    val bianName: String?,              // 变卦名
    val bianMark: String?,
    val bianGong: String?,
    val bianType: String?,
    val bianRows: List<LiuYaoBianLine>?,
    val hidden: LiuYaoHidden?,
    val siZhu: SiZhu,
    val xunKong: String,                // 日旬空，如 申酉
    val lunarText: String,              // 农历日期文本
) {
    /** 头部声明行：如 "艮宫属土 · 六冲卦" */
    fun gongDeclaration(): String = buildString {
        append(gong).append("宫属").append(gongWuXing.label)
        if (type.isNotEmpty()) append(" · ").append(type).append("卦")
    }

    companion object {

    /** 六爻摇卦排盘入口。time 为起卦时刻（公历） */
    fun compile(lines: List<Int>, time: java.util.Calendar): LiuYaoChart {
        val solar = Solar.fromYmdHms(
            time.get(java.util.Calendar.YEAR),
            time.get(java.util.Calendar.MONTH) + 1,
            time.get(java.util.Calendar.DAY_OF_MONTH),
            time.get(java.util.Calendar.HOUR_OF_DAY),
            time.get(java.util.Calendar.MINUTE),
            time.get(java.util.Calendar.SECOND),
        )
        return compile(lines, solar)
    }

    fun compile(lines: List<Int>, solar: Solar): LiuYaoChart {
        require(lines.size == 6) { "六爻需要 6 个爻值" }
        require(lines.all { it in 1..4 }) { "爻值只能取 1..4" }

        val lunar = solar.lunar
        val bazi = lunar.getBaZi()
        val siZhu = SiZhu(
            year = GanZhi.parse(bazi[0]),
            month = GanZhi.parse(bazi[1]),
            day = GanZhi.parse(bazi[2]),
            hour = GanZhi.parse(bazi[3]),
        )

        // 卦码：阳爻记 1 阴爻记 0（少阳/老阳为 1，少阴/老阴为 0）
        val mark = lines.joinToString("") { if (it == 1 || it == 3) "1" else "0" }
        val (shi, ying) = findShiYing(mark)
        val gongTrigram = findPalace(mark, shi)
        val name = GUA64.getValue(mark)

        // 纳甲（自初爻到上爻）+ 六亲（以本宫五行为我）
        val najia = najiaOf(mark)

        // 六神（按日干起，自初爻顺轮）
        val shenRotation = SHEN6_ROTATION[siZhu.day.gan.ordinal]
        val god6 = List(6) { SHEN6[(shenRotation + it) % 6] }

        val movingPositions = lines.mapIndexedNotNull { i, v -> if (v > 2) i + 1 else null }

        // 变卦：动爻取反；变爻六亲仍以本卦宫五行为我
        val hasBian = movingPositions.isNotEmpty()
        val bianMark = if (hasBian) lines.joinToString("") { if (it == 2 || it == 3) "0" else "1" } else null
        val bianNajia = bianMark?.let { najiaOf(it) }

        val rows = najia.mapIndexed { index, gz ->
            val zhi = DiZhi.fromChar(gz[1])
            val position = index + 1
            LiuYaoLine(
                position = position,
                yao = YaoValue.fromCode(lines[index]),
                ganZhi = gz,
                zhiWuXing = zhi.wuXing,
                liuQin = liuQinOf(gongTrigram.wuXing, zhi.wuXing),
                liuShen = god6[index],
                shiYing = when (position) {
                    shi -> "世"
                    ying -> "应"
                    else -> null
                },
                bianGanZhi = bianNajia?.get(index),
                bianLiuQin = bianNajia?.get(index)?.let { DiZhi.fromChar(it[1]) }?.let { liuQinOf(gongTrigram.wuXing, it.wuXing) },
            )
        }

        // 变卦行（全部六行，UI 只显动变位）
        val bianRows = bianMark?.let { bm ->
            val bgong = findPalace(bm, findShiYing(bm).first)
            najiaOf(bm).mapIndexed { index, gz ->
                val zhi = DiZhi.fromChar(gz[1])
                LiuYaoBianLine(
                    position = index + 1,
                    ganZhi = gz,
                    liuQin = liuQinOf(bgong.wuXing, zhi.wuXing),
                    isChanged = index + 1 in movingPositions,
                )
            }
        }

        return LiuYaoChart(
            lines = lines,
            mark = mark,
            name = name,
            gong = gongTrigram.label,
            gongWuXing = gongTrigram.wuXing,
            type = guaType(mark),
            shiYao = shi,
            yingYao = ying,
            rows = rows,
            movingPositions = movingPositions,
            bianName = bianMark?.let { GUA64.getValue(it) },
            bianMark = bianMark,
            bianGong = bianMark?.let { findPalace(it, findShiYing(it).first).label },
            bianType = bianMark?.let { guaType(it) },
            bianRows = bianRows,
            hidden = hiddenOf(gongTrigram, rows.map { it.liuQin }, gongTrigram.wuXing),
            siZhu = siZhu,
            xunKong = lunar.dayXunKong,
            lunarText = lunar.toString(),
        )
    }

    // ---------- 寻世诀：天同二世天变五，地同四世地变初；本宫六世三世异，人同游魂人变归 ----------
    // （蓝本 najia/utils.py set_shi_yao，逐分支对拍）
    fun findShiYing(mark: String): Pair<Int, Int> {
        val nei = mark.substring(0, 3) // 内卦（初二三爻）
        val wai = mark.substring(3, 6) // 外卦（四五六爻）
        val same = { i: Int -> wai[i] == nei[i] }

        fun shiYao(shi: Int): Pair<Int, Int> = shi to (if (shi > 3) shi - 3 else shi + 3)

        // 天同二世天变五
        if (same(2)) {
            if (!same(1) && !same(0)) return shiYao(2)
        } else {
            if (same(1) && same(0)) return shiYao(5)
        }
        // 人同游魂人变归
        if (same(1)) {
            if (!same(0) && !same(2)) return shiYao(4)
        } else {
            if (same(0) && same(2)) return shiYao(3)
        }
        // 地同四世地变初
        if (same(0)) {
            if (!same(1) && !same(2)) return shiYao(4)
        } else {
            if (same(1) && same(2)) return shiYao(1)
        }
        // 本宫六世
        if (wai == nei) return shiYao(6)
        // 三世异
        return shiYao(3)
    }

    // ---------- 认宫诀：一二三六外卦宫，四五游魂内变更；若问归魂何所取，归魂内卦是本宫 ----------
    fun findPalace(mark: String, shiYao: Int): Trigram {
        val nei = mark.substring(0, 3)
        val wai = mark.substring(3, 6)
        val hun = soulOf(mark)

        // 归魂内卦是本宫
        if (hun == "归魂") return Trigram.fromBinary(nei)
        // 一二三六外卦宫
        if (shiYao in listOf(1, 2, 3, 6)) return Trigram.fromBinary(wai)
        // 四五游魂内变更（内卦取反）
        if (shiYao in listOf(4, 5) || hun == "游魂") {
            val flipped = nei.map { if (it == '1') '0' else '1' }.joinToString("")
            return Trigram.fromBinary(flipped)
        }
        throw IllegalStateException("认宫失败: mark=$mark shi=$shiYao")
    }

    /** 游魂/归魂（人同位异为游魂，位同人异为归魂），无则 null */
    fun soulOf(mark: String): String? {
        val nei = mark.substring(0, 3)
        val wai = mark.substring(3, 6)
        return if (wai[1] == nei[1]) {
            if (wai[0] != nei[0] && wai[2] != nei[2]) "游魂" else null
        } else {
            if (wai[0] == nei[0] && wai[2] == nei[2]) "归魂" else null
        }
    }

    /** 六冲卦：内外卦相同，或天雷无妄/雷天大壮 */
    fun isChong(mark: String): Boolean {
        val nei = mark.substring(0, 3)
        val wai = mark.substring(3, 6)
        if (wai == nei) return true
        return setOf(nei, wai) == setOf("100", "111")
    }

    /** 六合卦：卦名含固定片段 */
    fun isHe(mark: String): Boolean {
        val name = GUA64.getValue(mark)
        return LIUHE_FRAGMENTS.any { name.contains(it) }
    }

    /** 卦类型标签（口径与 najia 一致：游魂/归魂 > 六冲 > 六合） */
    fun guaType(mark: String): String {
        soulOf(mark)?.let { return it }
        if (isChong(mark)) return "六冲"
        if (isHe(mark)) return "六合"
        return ""
    }

    /** 纳甲干支自初爻到上爻 */
    fun najiaOf(mark: String): List<String> {
        val nei = Trigram.fromBinary(mark.substring(0, 3))
        val wai = Trigram.fromBinary(mark.substring(3, 6))
        val neiTable = NAJIA.getValue(nei).first
        val waiTable = NAJIA.getValue(wai).second
        // 表首字为天干，后三字为三爻地支（自初爻/四爻起）
        val inner = listOf(neiTable[1], neiTable[2], neiTable[3]).map { neiTable[0].toString() + it }
        val outer = listOf(waiTable[1], waiTable[2], waiTable[3]).map { waiTable[0].toString() + it }
        return inner + outer
    }

    /** 六亲：同我兄弟，我生子孙，我克妻财，克我官鬼，生我父母 */
    fun liuQinOf(gongWuXing: WuXing, zhiWuXing: WuXing): String = when (gongWuXing.relationTo(zhiWuXing)) {
        com.smartwash.divination.core.ShengKeRelation.BI_HE -> "兄弟"
        com.smartwash.divination.core.ShengKeRelation.WO_SHENG -> "子孙"
        com.smartwash.divination.core.ShengKeRelation.WO_KE -> "妻财"
        com.smartwash.divination.core.ShengKeRelation.KE_WO -> "官鬼"
        com.smartwash.divination.core.ShengKeRelation.SHENG_WO -> "父母"
    }

    /** 伏神：装卦六亲不足五种时，从本宫卦纳甲中取藏伏之爻（najia _hidden 口径，首位命中） */
    private fun hiddenOf(gongTrigram: Trigram, qin6: List<String>, gongWuXing: WuXing): LiuYaoHidden? {
        if (qin6.toSet().size >= 5) return null
        val gongMark = gongTrigram.binary + gongTrigram.binary
        val gongName = GUA64.getValue(gongMark)
        val seats = mutableMapOf<Int, Pair<String, String>>()
        val missing = QING6.filterNot { it in qin6 }
        val gongNajia = najiaOf(gongMark)
        val gongQin = gongNajia.map { gz -> liuQinOf(gongWuXing, DiZhi.fromChar(gz[1]).wuXing) }
        missing.forEach { qin ->
            val seat = gongQin.indexOfFirst { it == qin }
            if (seat >= 0 && seats.none { it.value.first == qin }) {
                seats[seat + 1] = qin to gongNajia[seat]
            }
        }
        return if (seats.isEmpty()) null else LiuYaoHidden(gongName = gongName, seats = seats)
    }
}
}
