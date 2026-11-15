package com.smartwash.feature.divination.core.bazi

import com.nlf.calendar.EightChar
import com.nlf.calendar.Solar
import com.smartwash.feature.divination.core.DiZhi
import com.smartwash.feature.divination.core.GanZhi
import com.smartwash.feature.divination.core.SiZhu
import com.smartwash.feature.divination.core.TianGan

/**
 * 四柱排盘结果 —— 确定性数据全部委托 lunar-java 的 EightChar（可对拍、权威），
 * 本文件只做 Kotlin 封装 + 证据链字段组织。
 *
 * 锚点（docs §4.5 验证实录）：1990-05-20 14:30 男 → 庚午 辛巳 乙酉 癸未
 * 实测 lunar 输出：四柱 庚午/辛巳/乙酉/癸未；日干乙；月藏 [丙,庚,戊]；日藏 [辛]；
 *  十神 正官/七杀/日主/偏印；纳音 路旁土/白蜡金/泉中水/杨柳木；
 *  命宫 辛巳 身宫 己丑 胎元 壬申；旬空 午未；日支十二长生 绝；大运顺排、起运 1995-12-10。
 */
data class BaZiChart(
    val siZhu: SiZhu,                       // 四柱
    val gender: Int,                        // 1=男 0=女
    val dayGan: TianGan,                    // 日主（日干）
    val dayZhi: DiZhi,                      // 日支
    val shiShenGan: List<ShiShen?>,         // 年/月/日/时 天干十神（日于=日主）
    val zhiGan: List<List<TianGan>>,        // 四支藏干
    val naYin: List<String>,                // 四柱纳音
    val taiYuan: String,                    // 胎元
    val mingGong: String,                   // 命宫
    val shenGong: String,                   // 身宫
    val dayXunKong: String,                 // 日旬空
    val dayDiShi: String,                   // 日支十二长生（对日干）
    val yunForward: Boolean,                // 大运顺/逆
    val yunStartSolar: String,              // 起运公历
    val yunStartYear: Int,                  // 起运岁数
) {

    val labels: List<String> get() = siZhu.labels()

    companion object {

        /** 出生公历 → 八字盘。gender：1=男 0=女。sect：晚子时处理（2=晚子时日按当日） */
        fun fromSolar(solar: Solar, gender: Int): BaZiChart {
            val lunar = solar.lunar
            val e: EightChar = lunar.getEightChar()
            return build(e, gender)
        }

        fun fromYmdHms(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, gender: Int): BaZiChart {
            val solar = Solar.fromYmdHms(year, month, day, hour, minute, second)
            return fromSolar(solar, gender)
        }

        private fun build(e: EightChar, gender: Int): BaZiChart {
            val siZhu = SiZhu(
                year = GanZhi.parse(e.year),
                month = GanZhi.parse(e.month),
                day = GanZhi.parse(e.day),
                hour = GanZhi.parse(e.time),
            )
            val dayGan = siZhu.day.gan
            val yearGan = siZhu.year.gan
            val monthGan = siZhu.month.gan
            val hourGan = siZhu.hour.gan
            val shiShen = listOf(
                if (yearGan == dayGan) ShiShen.RI_ZHU else ShiShenUtil.of(dayGan, yearGan),
                if (monthGan == dayGan) ShiShen.RI_ZHU else ShiShenUtil.of(dayGan, monthGan),
                ShiShen.RI_ZHU,
                if (hourGan == dayGan) ShiShen.RI_ZHU else ShiShenUtil.of(dayGan, hourGan),
            )
            val zhiGan = listOf(siZhu.year.zhi, siZhu.month.zhi, siZhu.day.zhi, siZhu.hour.zhi)
                .map { HiddenGan.of(it).all }

            val yun = e.getYun(if (gender == 1) 1 else 0)
            return BaZiChart(
                siZhu = siZhu,
                gender = gender,
                dayGan = dayGan,
                dayZhi = siZhu.day.zhi,
                shiShenGan = shiShen,
                zhiGan = zhiGan,
                naYin = listOf(e.yearNaYin, e.monthNaYin, e.dayNaYin, e.timeNaYin),
                taiYuan = e.taiYuan,
                mingGong = e.mingGong,
                shenGong = e.shenGong,
                dayXunKong = e.dayXunKong,
                dayDiShi = e.dayDiShi,
                yunForward = yun.isForward,
                yunStartSolar = "${yun.startSolar.year}-${p2(yun.startSolar.month)}-${p2(yun.startSolar.day)}",
                yunStartYear = yun.startYear,
            )
        }

        private fun p2(i: Int): String = if (i < 10) "0$i" else i.toString()
    }
}