package com.smartwash.divination.core.meihua

import com.nlf.calendar.Solar
import com.smartwash.divination.core.DiZhi
import com.smartwash.divination.core.Trigram
import com.smartwash.divination.core.WuXing

/**
 * 梅花易数：时间/数字起卦 + 本互变 + 体用（方案文档 4.1，口诀级实现）。
 *
 * 时间卦：(年支数+月数+日数) % 8 = 上卦，+时支数 % 8 = 下卦，总数 % 6 = 动爻；
 * 年支数按子 1 丑 2…亥 12，月/日用农历数，余 0 作 8 / 作 6。
 * 先天卦数：乾一兑二离三震四巽五坎六艮七坤八。
 * 体用：动爻所在之卦为"用"，静卦为"体"；互、变皆论"用"侧。
 */

/** 体用生克判定（规则层产出，断语口径见《梅花易数》） */
enum class TiYongRelation(val label: String, val phrase: String) {
    YONG_SHENG_TI("用生体", "用生体，主进益之喜"),
    BI_HE("比和", "体用比和，诸事顺遂"),
    TI_KE_YONG("体克用", "体克用，可成但费力"),
    TI_SHENG_YONG("体生用", "体生用，耗泄之力，恐有损耗"),
    YONG_KE_TI("用克体", "用克体，所谋受制，宜缓图"),
}

data class MeiHuaChart(
    val lines: List<Int>,               // 六爻值（自初爻，1=阳 0? 统一用 1/2 静爻编码）
    val benMark: String,                // 二进制卦码
    val benName: String,
    val huMark: String,
    val huName: String,
    val bianMark: String,
    val bianName: String,
    val movingYao: Int,                 // 动爻 1..6
    val upper: Trigram,                 // 上卦
    val lower: Trigram,                 // 下卦
    val ti: Trigram,                    // 体卦（静卦）
    val yong: Trigram,                  // 用卦（动爻所在）
    val tiYongRelation: TiYongRelation,
    val huWuXing: WuXing,               // 互卦五行（用侧）
    val bianWuXing: WuXing,             // 变卦五行（用侧）
    val derivation: String?,            // 起卦推演说明（时间卦记录算式，数字卦记录报数）
) {
    val benUpperName: String get() = upper.label
    val benLowerName: String get() = lower.label

    companion object {

    private fun guaName(mark: String): String = GUA64_NAME.getValue(mark)

    private val GUA64_NAME: Map<String, String> = mapOf(
        "111111" to "乾为天", "011111" to "天风姤", "001111" to "天山遁", "000111" to "天地否",
        "000011" to "风地观", "000001" to "山地剥", "000101" to "火地晋", "111101" to "火天大有",
        "110110" to "兑为泽", "010110" to "泽水困", "000110" to "泽地萃", "001110" to "泽山咸",
        "001010" to "水山蹇", "001000" to "地山谦", "001100" to "雷山小过", "110100" to "雷泽归妹",
        "101101" to "离为火", "001101" to "火山旅", "011101" to "火风鼎", "010101" to "火水未济",
        "010001" to "山水蒙", "010011" to "风水涣", "010111" to "天水讼", "101111" to "天火同人",
        "100100" to "震为雷", "000100" to "雷地豫", "010100" to "雷水解", "011100" to "雷风恒",
        "011000" to "地风升", "011010" to "水风井", "011110" to "泽风大过", "100110" to "泽雷随",
        "011011" to "巽为风", "111011" to "风天小畜", "101011" to "风火家人", "100011" to "风雷益",
        "100111" to "天雷无妄", "100101" to "火雷噬嗑", "100001" to "山雷颐", "011001" to "山风蛊",
        "010010" to "坎为水", "110010" to "水泽节", "100010" to "水雷屯", "101010" to "水火既济",
        "101110" to "泽火革", "101100" to "雷火丰", "101000" to "地火明夷", "010000" to "地水师",
        "001001" to "艮为山", "101001" to "山火贲", "111001" to "山天大畜", "110001" to "山泽损",
        "110101" to "火泽睽", "110111" to "天泽履", "110011" to "风泽中孚", "001011" to "风山渐",
        "000000" to "坤为地", "100000" to "地雷复", "110000" to "地泽临", "111000" to "地天泰",
        "111100" to "雷天大壮", "111110" to "泽天夬", "111010" to "水天需", "000010" to "水地比",
    )

    /** 时间卦：起卦时刻（公历） */
    fun timeChart(solar: Solar): MeiHuaChart {
        val lunar = solar.lunar
        val zhiNian = DiZhi.fromChar(lunar.yearZhi[0]).ordinal + 1
        val yue = lunar.month
        val ri = lunar.day
        val zhiShi = DiZhi.fromChar(lunar.timeZhi[0]).ordinal + 1

        val upperTotal = zhiNian + yue + ri
        val total = upperTotal + zhiShi
        val upperNumber = mod(upperTotal, 8)
        val lowerNumber = mod(total, 8)
        val moving = mod(total, 6)

        val derivation = "年支数%d + 月%d + 日%d = %d → 上卦取%s；加时支数%d = %d → 下卦取%s、动爻取%d"
            .format(zhiNian, yue, ri, upperTotal, upperNumber, zhiShi, total, lowerNumber, moving)

        return build(upperNumber, lowerNumber, moving, derivation)
    }

    /** 数字卦：随报两数，上卦取首数，下卦/动爻取两数之和（同上取模） */
    fun numbersChart(a: Int, b: Int): MeiHuaChart {
        val upperNumber = mod(a, 8)
        val lowerNumber = mod(a + b, 8)
        val moving = mod(a + b, 6)
        return build(upperNumber, lowerNumber, moving, "报数$a、$b → 上卦取$a，下卦取${a + b}，动爻取${a + b}")
    }

    /** 由上/下卦先天数与动爻组装本互变与体用 */
    fun build(upperNumber: Int, lowerNumber: Int, moving: Int, derivation: String?): MeiHuaChart {
        val upper = Trigram.entries.first { it.number == upperNumber }
        val lower = Trigram.entries.first { it.number == lowerNumber }
        val benMark = lower.binary + upper.binary
        // 统一爻值编码：阳=少阳(1) 阴=少阴(2)，动爻改记 老阳(3)/老阴(4)
        val lines = benMark.mapIndexed { index, c ->
            val yang = c == '1'
            when {
                yang && index == moving - 1 -> 3
                !yang && index == moving - 1 -> 4
                yang -> 1
                else -> 2
            }
        }

        // 互卦：取本卦 234 爻为下互、345 爻为上互（爻序自初爻 0 起）
        val benLines = benMark.map { it.toString() }
        val huLower = benLines[1] + benLines[2] + benLines[3]
        val huUpper = benLines[2] + benLines[3] + benLines[4]
        val huMark = huLower + huUpper

        // 变卦：动爻翻转
        val bianMark = benMark.mapIndexed { index, c ->
            if (index == moving - 1) (if (c == '1') "0" else "1") else c.toString()
        }.joinToString("")

        // 体用：动爻在下卦(1..3) → 下卦为用、上卦为体；反之亦然
        val movingInLower = moving <= 3
        val ti = if (movingInLower) upper else lower
        val yong = if (movingInLower) lower else upper

        val relation = when (ti.wuXing.relationTo(yong.wuXing)) {
            com.smartwash.divination.core.ShengKeRelation.SHENG_WO -> TiYongRelation.YONG_SHENG_TI
            com.smartwash.divination.core.ShengKeRelation.BI_HE -> TiYongRelation.BI_HE
            com.smartwash.divination.core.ShengKeRelation.WO_KE -> TiYongRelation.TI_KE_YONG
            com.smartwash.divination.core.ShengKeRelation.WO_SHENG -> TiYongRelation.TI_SHENG_YONG
            com.smartwash.divination.core.ShengKeRelation.KE_WO -> TiYongRelation.YONG_KE_TI
        }

        return MeiHuaChart(
            lines = lines,
            benMark = benMark,
            benName = guaName(benMark),
            huMark = huMark,
            huName = guaName(huMark),
            bianMark = bianMark,
            bianName = guaName(bianMark),
            movingYao = moving,
            upper = upper,
            lower = lower,
            ti = ti,
            yong = yong,
            tiYongRelation = relation,
            huWuXing = Trigram.fromBinary(huMark.substring(3, 6)).wuXing,
            bianWuXing = Trigram.fromBinary(bianMark.substring(3, 6)).wuXing,
            derivation = derivation,
        )
    }

    private fun mod(value: Int, base: Int): Int {
        val r = value % base
        return if (r == 0) base else r
    }
}
}
