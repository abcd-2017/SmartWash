package com.smartwash.divination.core

/**
 * 干支与五行基础类型 —— 观象台内核共享底座（纯 Kotlin，无 Android 依赖，可 JVM 单测）。
 * 口诀依据：docs/divination/references/术数排盘解卦完整方案.md 附录 B。
 */

/** 五行（木火土金水），含生克关系推演 */
enum class WuXing(val label: String) {
    MU("木"),
    HUO("火"),
    TU("土"),
    JIN("金"),
    SHUI("水"),
    ;

    /** 我生者：木生火、火生土、土生金、金生水、水生木 */
    fun generates(): WuXing = when (this) {
        MU -> HUO
        HUO -> TU
        TU -> JIN
        JIN -> SHUI
        SHUI -> MU
    }

    /** 生我者 */
    fun generatedBy(): WuXing = when (this) {
        HUO -> MU
        TU -> HUO
        JIN -> TU
        SHUI -> JIN
        MU -> SHUI
    }

    /** 我克者：木克土、土克水、水克火、火克金、金克木 */
    fun overcomes(): WuXing = when (this) {
        MU -> TU
        HUO -> JIN
        TU -> SHUI
        JIN -> MU
        SHUI -> HUO
    }

    /** 克我者 */
    fun overcomeBy(): WuXing = when (this) {
        TU -> MU
        JIN -> HUO
        SHUI -> TU
        MU -> JIN
        HUO -> SHUI
    }

    /** 相对关系（以 this 为"我"） */
    fun relationTo(other: WuXing): ShengKeRelation = when (other) {
        this -> ShengKeRelation.BI_HE
        generates() -> ShengKeRelation.WO_SHENG
        generatedBy() -> ShengKeRelation.SHENG_WO
        overcomes() -> ShengKeRelation.WO_KE
        else -> ShengKeRelation.KE_WO
    }
}

/** 生克关系（以主体为"我"） */
enum class ShengKeRelation(val label: String) {
    BI_HE("比和"),
    WO_SHENG("我生"),
    SHENG_WO("生我"),
    WO_KE("我克"),
    KE_WO("克我"),
}

/** 天干（甲~癸，index 与六十甲子序一致） */
enum class TianGan(val label: String, val wuXing: WuXing) {
    JIA("甲", WuXing.MU),
    YI("乙", WuXing.MU),
    BING("丙", WuXing.HUO),
    DING("丁", WuXing.HUO),
    WU("戊", WuXing.TU),
    JI("己", WuXing.TU),
    GENG("庚", WuXing.JIN),
    XIN("辛", WuXing.JIN),
    REN("壬", WuXing.SHUI),
    GUI("癸", WuXing.SHUI),
    ;

    /** 阳干 / 阴干 */
    val isYang: Boolean get() = ordinal % 2 == 0

    companion object {
        fun fromChar(c: Char): TianGan = entries.first { it.label == c.toString() }
    }
}

/** 地支（子~亥，index 与六十甲子序一致） */
enum class DiZhi(val label: String, val wuXing: WuXing) {
    ZI("子", WuXing.SHUI),
    CHOU("丑", WuXing.TU),
    YIN("寅", WuXing.MU),
    MAO("卯", WuXing.MU),
    CHEN("辰", WuXing.TU),
    SI("巳", WuXing.HUO),
    WU("午", WuXing.HUO),
    WEI("未", WuXing.TU),
    SHEN("申", WuXing.JIN),
    YOU("酉", WuXing.JIN),
    XU("戌", WuXing.TU),
    HAI("亥", WuXing.SHUI),
    ;

    /** 阳支：子寅辰午申戌 */
    val isYang: Boolean get() = ordinal % 2 == 0

    /** 六冲：相隔六位 */
    fun chong(): DiZhi = entries[(ordinal + 6) % 12]

    /** 六合：子丑、寅亥、卯戌、辰酉、巳申、午未 */
    fun he(): DiZhi = when (this) {
        ZI -> CHOU
        CHOU -> ZI
        YIN -> HAI
        HAI -> YIN
        MAO -> XU
        XU -> MAO
        CHEN -> YOU
        YOU -> CHEN
        SI -> SHEN
        SHEN -> SI
        WU -> WEI
        WEI -> WU
    }

    companion object {
        fun fromChar(c: Char): DiZhi = entries.first { it.label == c.toString() }
    }
}

/** 一组干支（如 乙亥），index 为六十甲子序（甲子=0） */
data class GanZhi(val gan: TianGan, val zhi: DiZhi) {

    val label: String get() = gan.label + zhi.label

    /** 六十甲子序号 0..59 */
    val jiaZiIndex: Int = (0 until 60).first { it % 10 == gan.ordinal && it % 12 == zhi.ordinal }

    /** 所在旬首（甲子/甲戌/甲申/甲午/甲辰/甲寅） */
    val xun: GanZhi get() = GanZhi(TianGan.JIA, DiZhi.entries[(zhi.ordinal - gan.ordinal + 12 * 2) % 12])

    /**
     * 旬空两支：旬首地支前两位。
     * 甲子旬空戌亥、甲戌旬空申酉、甲申旬空午未、甲午旬空辰巳、甲辰旬空寅卯、甲寅旬空子丑
     */
    val xunKong: Pair<DiZhi, DiZhi>
        get() {
            val startZhi = xun.zhi.ordinal
            return DiZhi.entries[(startZhi + 10) % 12] to DiZhi.entries[(startZhi + 11) % 12]
        }

    companion object {
        /** 按六十甲子序构造 */
        fun fromJiaZiIndex(index: Int): GanZhi {
            val n = ((index % 60) + 60) % 60
            return GanZhi(TianGan.entries[n % 10], DiZhi.entries[n % 12])
        }

        fun parse(text: String): GanZhi = GanZhi(TianGan.fromChar(text[0]), DiZhi.fromChar(text[1]))
    }
}

/** 四柱（年月日时干支） */
data class SiZhu(
    val year: GanZhi,
    val month: GanZhi,
    val day: GanZhi,
    val hour: GanZhi,
) {
    fun labels(): List<String> = listOf(year.label, month.label, day.label, hour.label)
}
