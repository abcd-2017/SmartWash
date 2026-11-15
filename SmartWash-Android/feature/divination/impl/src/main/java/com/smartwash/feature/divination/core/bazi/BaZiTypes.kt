package com.smartwash.feature.divination.core.bazi

import com.smartwash.feature.divination.core.DiZhi
import com.smartwash.feature.divination.core.TianGan
import com.smartwash.feature.divination.core.WuXing

/**
 * 八字基础类型 —— 观象台第 5 术"四柱"内核（纯 Kotlin，零 Android 依赖，可 JVM 单测）。
 *
 * 分工：
 *  - 四柱/纳音/命宫/胎元/身宫/旬空/大运 等确定性数据 → 委托 lunar-java 的 EightChar 对拍（见 BaZiChart）；
 *  - 本文件只放"自研证据链"所需的基础查表与枚举：
 *    十神（子平）、地支藏干（通根/强度打分用）、旺相休囚、格局候选。
 *  依据：docs/divination/references/术数排盘解卦完整方案.md §4.5 + 附录 B。
 */

/** 十神（以日干为"我"） */
enum class ShiShen(val label: String) {
    BI_JIAN("比肩"), JIE_CAI("劫财"),
    SHI_SHEN("食神"), SHANG_GUAN("伤官"),
    ZHENG_CAI("正财"), PIAN_CAI("偏财"),
    ZHENG_GUAN("正官"), QI_SHA("七杀"),
    ZHENG_YIN("正印"), PIAN_YIN("偏印"),
    RI_ZHU("日主"),
}

/**
 * 十神推演。以日干为"我"，按五行生克 + 阴阳定正偏：
 *  同我 → 比劫（同阴阳=比肩，异=劫财）
 *  我生 → 食伤（同阴阳=食神，异=伤官）
 *  我克 → 财  （同阴阳=偏财，异=正财）
 *  克我 → 官杀（同阴阳=七杀，异=正官）
 *  生我 → 印  （同阴阳=偏印，异=正印）
 */
object ShiShenUtil {

    fun of(dayGan: TianGan, otherGan: TianGan): ShiShen {
        if (dayGan == otherGan) return ShiShen.RI_ZHU
        val dayYang = dayGan.ordinal % 2 == 0   // 甲丙戊庚壬阳
        val otherYang = otherGan.ordinal % 2 == 0
        val sameYang = dayYang == otherYang
        val dayWx = dayGan.wuXing
        val otherWx = otherGan.wuXing

        return when {
            // 同我 → 比劫
            dayWx == otherWx -> if (sameYang) ShiShen.BI_JIAN else ShiShen.JIE_CAI
            // 我生 → 食伤
            dayWx.generates() == otherWx ->
                if (sameYang) ShiShen.SHI_SHEN else ShiShen.SHANG_GUAN
            // 我克 → 财
            dayWx.overcomes() == otherWx ->
                if (sameYang) ShiShen.PIAN_CAI else ShiShen.ZHENG_CAI
            // 克我 → 官杀
            otherWx.overcomes() == dayWx ->
                if (sameYang) ShiShen.QI_SHA else ShiShen.ZHENG_GUAN
            // 生我 → 印
            else -> if (sameYang) ShiShen.PIAN_YIN else ShiShen.ZHENG_YIN
        }
    }
}

/** 地支藏干（本气/中气/余气），用于"通根强度"打分。依据主流子平藏干表。 */
data class HiddenGan(
    val benQi: TianGan,
    val zhongQi: TianGan? = null,
    val yuQi: TianGan? = null,
) {
    val all: List<TianGan> get() = listOfNotNull(benQi, zhongQi, yuQi)

    companion object {
        /** 子午卯酉 藏 1 气；寅申巳亥 / 辰戌丑未 藏本中余三气（部分二气） */
        val TABLE: Map<DiZhi, HiddenGan> = mapOf(
            DiZhi.ZI to HiddenGan(TianGan.GUI),                                     // 子 癸
            DiZhi.CHOU to HiddenGan(TianGan.JI, TianGan.GUI, TianGan.XIN),          // 丑 己癸辛
            DiZhi.YIN to HiddenGan(TianGan.JIA, TianGan.BING, TianGan.WU),          // 寅 甲丙戊
            DiZhi.MAO to HiddenGan(TianGan.YI),                                     // 卯 乙
            DiZhi.CHEN to HiddenGan(TianGan.WU, TianGan.YI, TianGan.GUI),           // 辰 戊乙癸
            DiZhi.SI to HiddenGan(TianGan.BING, TianGan.GENG, TianGan.WU),          // 巳 丙庚戊
            DiZhi.WU to HiddenGan(TianGan.DING, TianGan.JI),                        // 午 丁己
            DiZhi.WEI to HiddenGan(TianGan.JI, TianGan.DING, TianGan.YI),           // 未 己丁乙
            DiZhi.SHEN to HiddenGan(TianGan.GENG, TianGan.REN, TianGan.WU),         // 申 庚壬戊
            DiZhi.YOU to HiddenGan(TianGan.XIN),                                    // 酉 辛
            DiZhi.XU to HiddenGan(TianGan.WU, TianGan.XIN, TianGan.DING),           // 戌 戊辛丁
            DiZhi.HAI to HiddenGan(TianGan.REN, TianGan.JIA),                       // 亥 壬甲
        )

        fun of(zhi: DiZhi): HiddenGan = TABLE.getValue(zhi)
    }
}

/** 旺相休囚死：月支五行对日主五行的旺相休囚死（当令者旺、令生者相、我生令者休、令克者囚、令生我者死——以月令为"令"）。 */
enum class WangShuai(val label: String) {
    WANG("旺"), XIANG("相"), XIU("休"), QIU("囚"), SI("死"),
}

object WangShuaiUtil {
    /**
     * @param monthWx 月令五行（当令者）
     * @param wx      被考察五行
     * 规则（以月令为令）：
     *   wx == 月令        → 旺（当令）
     *   月令生 wx         → 相（令生）
     *   wx 生月令         → 休（我生令）
     *   月令克 wx         → 囚（令克）
     *   wx 克月令         → 死（我克令）
     */
    fun of(monthWx: WuXing, wx: WuXing): WangShuai = when {
        wx == monthWx -> WangShuai.WANG
        monthWx.generates() == wx -> WangShuai.XIANG   // 月令生我
        monthWx.generatedBy() == wx -> WangShuai.XIU   // 我生月令
        monthWx.overcomes() == wx -> WangShuai.QIU     // 月令克我
        else -> WangShuai.SI                            // 我克月令
    }
}

/** 格局候选（子平八格 + 从格，仅"候选 + 依据"，不下单一结论） */
enum class GeJu(val label: String) {
    // 普通八格：月令藏干透出定格
    ZHENG_GUAN("正官格"), QI_SHA("七杀格"),
    ZHENG_CAI("正财格"), PIAN_CAI("偏财格"),
    ZHENG_YIN("正印格"), PIAN_YIN("偏印格"),
    SHI_SHEN("食神格"), SHANG_GUAN("伤官格"),
    BI_JIAN("建禄格"), JIE_CAI("羊刃格"),
    // 从格：身极弱而满盘异党 → 从
    CONG_CAI("从财格"), CONG_SHA("从杀格"), CONG_ER("从儿格"), CONG_RUO("从弱格"),
    CONG_WANG("从旺格"), CONG_QING("从强格"),
    // 特殊
    QU_ZHI("曲直格"), YAN_SHANG("炎上格"), JIA_SE("稼穑格"),
    CONG_GE("从革格"), RUN_XIA("润下格"),
}

/**
 * 日主强弱档位（自研证据链用，明确标注"v1 示例权重"，非权威）。
 * 完全仿逆向报告的 9 档（身旺/偏旺/平和/偏弱/身弱/从强/从弱/假从强/假从弱），
 * 但打分公式由我们自研（逆向往弱打分有 bug，不能照抄）。
 */
enum class Strength(val label: String) {
    SHEN_WANG("身旺"), PIAN_WANG("偏旺"), PING_HE("平和"),
    PIAN_RUO("偏弱"), SHEN_RUO("身弱"),
    CONG_QIANG("从强"), CONG_RUO("从弱"),
}