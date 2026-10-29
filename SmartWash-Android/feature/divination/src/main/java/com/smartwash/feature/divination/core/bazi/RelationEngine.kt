package com.smartwash.feature.divination.core.bazi

import com.smartwash.feature.divination.core.DiZhi
import com.smartwash.feature.divination.core.GanZhi
import com.smartwash.feature.divination.core.SiZhu
import com.smartwash.feature.divination.core.TianGan

/**
 * 干支关系引擎 —— 天干五合/相冲、地支六合/三合/三会/六冲/三刑/六害。
 * 自研（纯表/规则，零 lunar 依赖），供八字证据链与后续可复用（六爻等）。
 *
 * 依据：docs §4.5「冲合刑会：天干五合、地支六合/三合局/三会方/六冲/刑/害」。
 */

/** 天干五合：甲己合土、乙庚合金、丙辛合水、丁壬合木、戊癸合火 */
private val TIAN_GAN_HE: Map<Pair<TianGan, TianGan>, TianGan> = buildMap {
    put(TianGan.JIA to TianGan.JI, TianGan.WU); put(TianGan.JI to TianGan.JIA, TianGan.WU)
    put(TianGan.YI to TianGan.GENG, TianGan.XIN); put(TianGan.GENG to TianGan.YI, TianGan.XIN)
    put(TianGan.BING to TianGan.XIN, TianGan.GUI); put(TianGan.XIN to TianGan.BING, TianGan.GUI)
    put(TianGan.DING to TianGan.REN, TianGan.JIA); put(TianGan.REN to TianGan.DING, TianGan.JIA)
    put(TianGan.WU to TianGan.GUI, TianGan.BING); put(TianGan.GUI to TianGan.WU, TianGan.BING)
}

/** 地支六合：子丑土、寅亥木、卯戌火、辰酉金、巳申水、午未土 */
private val DI_ZHI_LIU_HE: Map<DiZhi, DiZhi> = mapOf(
    DiZhi.ZI to DiZhi.CHOU, DiZhi.CHOU to DiZhi.ZI,
    DiZhi.YIN to DiZhi.HAI, DiZhi.HAI to DiZhi.YIN,
    DiZhi.MAO to DiZhi.XU, DiZhi.XU to DiZhi.MAO,
    DiZhi.CHEN to DiZhi.YOU, DiZhi.YOU to DiZhi.CHEN,
    DiZhi.SI to DiZhi.SHEN, DiZhi.SHEN to DiZhi.SI,
    DiZhi.WU to DiZhi.WEI, DiZhi.WEI to DiZhi.WU,
)

/** 地支三合局：申子辰水、寅午戌火、巳酉丑金、亥卯未木 */
private val DI_ZHI_SAN_HE: List<Set<DiZhi>> = listOf(
    setOf(DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN),
    setOf(DiZhi.YIN, DiZhi.WU, DiZhi.XU),
    setOf(DiZhi.SI, DiZhi.YOU, DiZhi.CHOU),
    setOf(DiZhi.HAI, DiZhi.MAO, DiZhi.WEI),
)

/** 地支三会方：寅卯辰会木、巳午未会火、申酉戌会金、亥子丑会水 */
private val DI_ZHI_SAN_HUI: List<Set<DiZhi>> = listOf(
    setOf(DiZhi.YIN, DiZhi.MAO, DiZhi.CHEN),
    setOf(DiZhi.SI, DiZhi.WU, DiZhi.WEI),
    setOf(DiZhi.SHEN, DiZhi.YOU, DiZhi.XU),
    setOf(DiZhi.HAI, DiZhi.ZI, DiZhi.CHOU),
)

/** 地支六冲：子午、丑未、寅申、卯酉、辰戌、巳亥 */
private val DI_ZHI_CHONG: Map<DiZhi, DiZhi> = DiZhi.entries.associate { it to DiZhi.entries[(it.ordinal + 6) % 12] }

/** 地支三刑：寅巳申、丑戌未；自刑：辰辰、午午、酉酉、亥亥 */
private val DI_ZHI_XING: List<Set<DiZhi>> = listOf(
    setOf(DiZhi.YIN, DiZhi.SI, DiZhi.SHEN),
    setOf(DiZhi.CHOU, DiZhi.XU, DiZhi.WEI),
)
private val DI_ZHI_ZI_XING: Set<DiZhi> = setOf(DiZhi.CHEN, DiZhi.WU, DiZhi.YOU, DiZhi.HAI)

/** 地支六害：子未、丑午、寅巳、卯辰、申亥、酉戌 */
private val DI_ZHI_HAI: Map<DiZhi, DiZhi> = mapOf(
    DiZhi.ZI to DiZhi.WEI, DiZhi.WEI to DiZhi.ZI,
    DiZhi.CHOU to DiZhi.WU, DiZhi.WU to DiZhi.CHOU,
    DiZhi.YIN to DiZhi.SI, DiZhi.SI to DiZhi.YIN,
    DiZhi.MAO to DiZhi.CHEN, DiZhi.CHEN to DiZhi.MAO,
    DiZhi.SHEN to DiZhi.HAI, DiZhi.HAI to DiZhi.SHEN,
    DiZhi.YOU to DiZhi.XU, DiZhi.XU to DiZhi.YOU,
)

/** 关系类型 */
enum class RelType(val label: String) {
    TIAN_GAN_HE("天干五合"),
    DI_ZHI_LIU_HE("六合"),
    DI_ZHI_SAN_HE("三合局"),
    DI_ZHI_SAN_HUI("三会方"),
    CHONG("六冲"),
    XING("三刑"),
    ZI_XING("自刑"),
    HAI("六害"),
}

/** 一条支（或干）间的关系 */
data class Relation(
    val type: RelType,
    val gans: List<TianGan> = emptyList(),  // 参与的天干（天干五合）
    val zhis: List<DiZhi> = emptyList(),    // 参与的地支（其余类型）
)

object RelationEngine {

    /** 天干五合：给定三四个天干，找相合对 */
    fun tianGanHe(gans: List<TianGan>): List<Relation> {
        val result = mutableListOf<Relation>()
        for (i in gans.indices) {
            for (j in i + 1 until gans.size) {
                val product = TIAN_GAN_HE[gans[i] to gans[j]]
                if (product != null) {
                    result.add(Relation(RelType.TIAN_GAN_HE, gans = listOf(gans[i], gans[j])))
                }
            }
        }
        return result
    }

    /** 地支关系：查四支中出现的六合/三合/三会/六冲/三刑/六害 */
    fun diZhi(zhis: List<DiZhi>): List<Relation> {
        val result = mutableListOf<Relation>()
        val seen = mutableSetOf<String>()

        fun add(r: Relation) {
            val keyStr = r.type.name + ":" +
                (r.zhis.map { it.name }.sorted().joinToString(","))
            if (seen.add(keyStr)) result.add(r)
        }

        // 六合
        for ((a, b) in DI_ZHI_LIU_HE) {
            if (zhis.contains(a) && zhis.contains(b)) {
                add(Relation(RelType.DI_ZHI_LIU_HE, zhis = listOf(a, b)))
            }
        }
        // 三合局
        for (set in DI_ZHI_SAN_HE) {
            val contained = zhis.filter { it in set }
            if (contained.size >= 2) {
                add(Relation(RelType.DI_ZHI_SAN_HE, zhis = contained.sortedBy { it.ordinal }))
            }
        }
        // 三会方
        for (set in DI_ZHI_SAN_HUI) {
            val contained = zhis.filter { it in set }
            if (contained.size >= 2) {
                add(Relation(RelType.DI_ZHI_SAN_HUI, zhis = contained.sortedBy { it.ordinal }))
            }
        }
        // 六冲
        for ((a, b) in DI_ZHI_CHONG) {
            if (zhis.contains(a) && zhis.contains(b)) {
                add(Relation(RelType.CHONG, zhis = listOf(a, b)))
            }
        }
        // 三刑
        for (set in DI_ZHI_XING) {
            val contained = zhis.filter { it in set }
            if (contained.size >= 2) {
                add(Relation(RelType.XING, zhis = contained.sortedBy { it.ordinal }))
            }
        }
        // 自刑
        for (z in DI_ZHI_ZI_XING) {
            if (zhis.count { it == z } >= 2) {
                add(Relation(RelType.ZI_XING, zhis = listOf(z, z)))
            }
        }
        return result
    }

    /** 对整盘（四柱）跑关系 */
    fun ofChart(siZhu: SiZhu): List<Relation> {
        val gans = listOf(siZhu.year.gan, siZhu.month.gan, siZhu.day.gan, siZhu.hour.gan)
        val zhis = listOf(siZhu.year.zhi, siZhu.month.zhi, siZhu.day.zhi, siZhu.hour.zhi)
        return tianGanHe(gans) + diZhi(zhis)
    }
}