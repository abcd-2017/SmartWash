package com.smartwash.feature.divination.core.bazi

import com.smartwash.feature.divination.core.DiZhi
import com.smartwash.feature.divination.core.TianGan
import com.smartwash.feature.divination.core.WuXing

/**
 * 八字"证据链"规则层 —— 只产确定性事实标记，不下吉凶结论（仿六爻 LiuYaoFacts 定位）。
 *
 * 产出三件事：
 *  1. 五行力量打分（旺衰证据链）：天干透出 + 地支藏干通根，按月令旺相休囚加权；
 *  2. 日主强弱档位（身旺/偏旺/平和/偏弱/身弱/从强/从弱）；
 *  3. 格局候选 + 用神/忌神五行候选（只给"候选 + 理由"，不下单一定论）。
 *
 * 权重口径为「v1 示例」，严格自研。数量级参考逆向报告的藏干权重方向，
 * 但**不照抄其有 bug 的累加逻辑**，也**不宣称与问真一致**。UI 需展示"v1 示例权重"。
 *
 * 评分细则（v1）：
 *  天干透出（年/月/时干，日干除外）贡献 1.0；
 *  地支藏干通根：本气 1.0、中气 0.7、余气 0.5；
 *  月令旺相休囚死加权：旺 1.5 / 相 1.2 / 休 1.0 / 囚 0.8 / 死 0.6。
 */

/** 某五行的力量构成 */
data class WuXingScore(
    val wuXing: WuXing,
    val tianGanScore: Double,   // 天干透出贡献
    val zhiScore: Double,       // 地支藏干通根贡献
    val weighted: Double,       // 月令加权后总分
)

/** 用神/忌神候选 */
data class YongShenCandidate(
    val wuXing: WuXing,
    val role: String,           // 用 / 喜 / 忌
    val reason: String,
)

data class BaZiFacts(
    val dayGan: TianGan,
    val dayWx: WuXing,
    val scores: Map<WuXing, WuXingScore>,
    val strength: Strength,
    val strengthNote: String,
    val geJuCandidates: List<GeJu>,
    val yongShenCandidates: List<YongShenCandidate>,
) {

    companion object {

        /**
         * @param dayGan      日主（日干）
         * @param monthZhi    月支（当令）
         * @param ganList     年/月/日/时 四天干
         * @param zhiHidden   年/月/日/时 四支藏干（本/中/余），zhiHidden[1] 为月支
         */
        fun ofChart(
            dayGan: TianGan,
            monthZhi: DiZhi,
            ganList: List<TianGan>,
            zhiHidden: List<List<TianGan>>,
        ): BaZiFacts {
            val dayWx = dayGan.wuXing
            val monthWx = monthZhi.wuXing

            // ---- 1. 天干透出分 ----
            val tianScore = WuXing.entries.associateWith { 0.0 }.toMutableMap()
            ganList.forEach { g ->
                if (g == dayGan) return@forEach  // 日干本身是"我"，不给所在五行计党力
                tianScore[g.wuXing] = tianScore.getValue(g.wuXing) + 1.0
            }

            // ---- 2. 地支藏干通根分 ----
            val zhiScore = WuXing.entries.associateWith { 0.0 }.toMutableMap()
            zhiHidden.forEach { hidden ->
                hidden.forEachIndexed { i, g ->
                    val w = when (i) {
                        0 -> 1.0
                        1 -> 0.7
                        else -> 0.5
                    }
                    zhiScore[g.wuXing] = zhiScore.getValue(g.wuXing) + w
                }
            }

            // ---- 3. 月令加权 ----
            val factor = WuXing.entries.associateWith { wx ->
                when (WangShuaiUtil.of(monthWx, wx)) {
                    WangShuai.WANG -> 1.5
                    WangShuai.XIANG -> 1.2
                    WangShuai.XIU -> 1.0
                    WangShuai.QIU -> 0.8
                    WangShuai.SI -> 0.6
                }
            }
            val weighted = WuXing.entries.associateWith { wx ->
                (tianScore.getValue(wx) + zhiScore.getValue(wx)) * factor.getValue(wx)
            }
            val scores = WuXing.entries.associateWith { wx ->
                WuXingScore(wx, tianScore.getValue(wx), zhiScore.getValue(wx), weighted.getValue(wx))
            }

            // ---- 4. 日主强弱：同党（印 + 比劫）vs 异党（官杀 + 财 + 食伤） ----
            val help = weighted.getValue(dayWx) + weighted.getValue(dayWx.generatedBy())
            val drain = weighted.values.sum() - help
            val total = weighted.values.sum()

            var strength: Strength
            var note: String
            when {
                help >= drain * 2.2 -> { strength = Strength.SHEN_WANG; note = "同党(${"%.1f".format(help)}) ≥ 异党2.2倍(${"%.1f".format(drain * 2.2)})，身旺" }
                help >= drain * 1.4 -> { strength = Strength.PIAN_WANG; note = "同党(${"%.1f".format(help)}) 明显大于异党(${"%.1f".format(drain)})，偏旺" }
                help <= drain * 0.45 -> { strength = Strength.SHEN_RUO; note = "同党(${"%.1f".format(help)}) 远小于异党(${"%.1f".format(drain)})，身弱" }
                help <= drain * 0.72 -> { strength = Strength.PIAN_RUO; note = "同党(${"%.1f".format(help)}) 小于异党(${"%.1f".format(drain)})，偏弱" }
                else -> { strength = Strength.PING_HE; note = "同党(${"%.1f".format(help)}) 与异党(${"%.1f".format(drain)}) 接近，中和" }
            }

            // 从格修正：日主无根（日支/月支本气无日主五行）且同党占比极小 → 从弱；极大 → 从强
            val dayRoot = zhiScore.getValue(dayWx) > 0.5
            val dayRatio = if (total > 0) weighted.getValue(dayWx) / total else 0.0
            if (strength == Strength.SHEN_RUO && !dayRoot && dayRatio < 0.15) {
                strength = Strength.CONG_RUO
                note = "日主无根且同党占比 ${"%.1f".format(dayRatio * 100)}% < 15% → 从弱"
            } else if (strength == Strength.SHEN_WANG && dayRatio > 0.85) {
                strength = Strength.CONG_QIANG
                note = "同党占比 ${"%.1f".format(dayRatio * 100)}% > 85% → 从强"
            }

            return BaZiFacts(
                dayGan = dayGan,
                dayWx = dayGan.wuXing,
                scores = scores,
                strength = strength,
                strengthNote = note,
                geJuCandidates = geJuOf(dayGan, ganList, zhiHidden),
                yongShenCandidates = yongShen(dayGan.wuXing, strength, scores),
            )
        }

        // ---- 格局候选：月令藏干透干定格（子平八格），未透则月令本位 ----
        private fun geJuOf(
            dayGan: TianGan,
            ganList: List<TianGan>,
            zhiHidden: List<List<TianGan>>,
        ): List<GeJu> {
            val monthHidden = zhiHidden.getOrElse(1) { emptyList() }
            if (monthHidden.isEmpty()) return emptyList()

            val candidates = mutableListOf<GeJu>()
            // 月令每个藏干，若透出年/月/时干（不含日干）→ 按其十神定格
            for (zhiGan in monthHidden) {
                if (ganList.any { it == zhiGan && it != dayGan }) {
                    val ss = ShiShenUtil.of(dayGan, zhiGan)
                    val geJu = gejuByShiShen(ss) ?: continue
                    candidates += geJu
                }
            }
            // 未透出 → 月令本位（建禄 / 羊刃）
            if (candidates.isEmpty()) {
                val benQi = monthHidden[0]
                val ss = ShiShenUtil.of(dayGan, benQi)
                when (ss) {
                    ShiShen.BI_JIAN -> candidates += GeJu.BI_JIAN
                    ShiShen.JIE_CAI -> candidates += GeJu.JIE_CAI
                    else -> candidates += gejuByShiShen(ss) ?: GeJu.ZHENG_YIN
                }
            }
            return candidates.distinct().toList()
        }

        /** 十神 → 格局（比劫不入普通格，返回 null 由调用方兜底建禄/羊刃） */
        private fun gejuByShiShen(ss: ShiShen): GeJu? = when (ss) {
            ShiShen.ZHENG_GUAN -> GeJu.ZHENG_GUAN
            ShiShen.QI_SHA -> GeJu.QI_SHA
            ShiShen.ZHENG_CAI -> GeJu.ZHENG_CAI
            ShiShen.PIAN_CAI -> GeJu.PIAN_CAI
            ShiShen.ZHENG_YIN -> GeJu.ZHENG_YIN
            ShiShen.PIAN_YIN -> GeJu.PIAN_YIN
            ShiShen.SHI_SHEN -> GeJu.SHI_SHEN
            ShiShen.SHANG_GUAN -> GeJu.SHANG_GUAN
            ShiShen.BI_JIAN, ShiShen.JIE_CAI, ShiShen.RI_ZHU -> null
        }

        // ---- 用神/忌神候选 ----
        private fun yongShen(
            dayWx: WuXing,
            strength: Strength,
            scores: Map<WuXing, WuXingScore>,
        ): List<YongShenCandidate> {
            val result = mutableListOf<YongShenCandidate>()
            val yin = dayWx.generatedBy()          // 生我→印
            val biJie = dayWx                        // 同我→比劫
            val guanSha = dayWx.overcomeBy()         // 克我→官杀
            val shiShang = dayWx.generates()         // 我生→食伤
            val cai = dayWx.overcomes()              // 我克→财
            when (strength) {
                Strength.SHEN_RUO, Strength.PIAN_RUO -> {
                    result += YongShenCandidate(yin, "喜", "身弱喜印（其生我）以扶身")
                    result += YongShenCandidate(biJie, "喜", "身弱喜比劫（同五行）以帮身")
                    result += YongShenCandidate(guanSha, "忌", "身弱忌官杀（其克我）更泄身")
                }
                Strength.SHEN_WANG, Strength.PIAN_WANG, Strength.CONG_QIANG -> {
                    result += YongShenCandidate(guanSha, "喜", "身旺喜官杀（其克我）制身")
                    result += YongShenCandidate(shiShang, "喜", "身旺喜食伤（我生）泄秀")
                    result += YongShenCandidate(cai, "喜", "身旺喜财（我克）耗身")
                    result += YongShenCandidate(yin, "忌", "身旺忌印（其生我）助旺")
                }
                Strength.PING_HE -> {
                    val weakest = scores.minByOrNull { it.value.weighted }?.value?.wuXing ?: dayWx
                    result += YongShenCandidate(weakest, "喜", "中和取最弱五行以平衡")
                }
                Strength.CONG_RUO -> {
                    result += YongShenCandidate(guanSha, "用", "从弱喜官杀（异党主帅）")
                    result += YongShenCandidate(cai, "喜", "从弱喜财（流通）")
                }
            }
            return result
        }
    }
}