package com.smartwash.divination.core.qimen

import com.nlf.calendar.Solar
import com.smartwash.divination.core.DiZhi
import com.smartwash.divination.core.GanZhi

/**
 * 奇门遁甲排盘（时家奇门 · 拆补定元 · 转盘排法）。
 * 蓝本：qfdk/qimen（lib/dipan、jiuxing、bamen、bashen、qimen.js），差分 oracle
 * 为 demo/qimen/verify_qimen.js，口径锁定"拆补 + 转盘"并在盘面声明，不混派。
 *
 * 三奇六仪顺序：戊己庚辛壬癸丁丙乙；旬首遁干：甲子戊 甲戌己 甲申庚 甲午辛 甲辰壬 甲寅癸。
 * 洛书飞宫顺序（不含中五宫）：1 8 3 4 9 2 7 6。
 */
data class QiMenChart(
    val siZhu: com.smartwash.divination.core.SiZhu,
    val jieQiName: String,          // 最近节气（定局依据）
    val yinYang: String,            // "阴遁" / "阳遁"
    val juNumber: Int,              // 局数 1..9
    val yuan: String,               // 上元 / 中元 / 下元
    val xunShouYi: String,          // 旬首六仪（时柱旬）
    val diPan: Map<Int, String>,    // 地盘干：宫 → 干
    val tianPan: Map<Int, String>,  // 天盘干：宫 → 干
    val jiuXing: Map<Int, String>,  // 九星（坤二宫寄宫记"禽芮"，中宫空）
    val baMen: Map<Int, String>,    // 八门（中宫无门）
    val baShen: Map<Int, String>,   // 八神（中宫无神）
    val anGan: Map<Int, String>,    // 暗干
    val zhiFuXing: String,          // 值符星
    val zhiFuLuoGong: Int,          // 值符落宫（转动后，即时干落宫）
    val zhiFuYuanGong: Int,         // 值符原宫（旬首六仪地盘宫）
    val zhiShiMen: String,          // 值使门
    val zhiShiGong: Int,            // 值使门落宫（显示宫，中五寄坤二）
    val zhiShiGongRaw: Int,         // 值使门真实落宫（含中五，供暗干起局）
    val kongWangZhi: List<String>,  // 时柱旬空地支
    val kongWangGong: List<Int>,    // 空亡宫
    val maZhi: String,              // 驿马地支
    val maGong: Int,                // 驿马宫
) {
    /** 口径声明行，如 "阴遁七局 · 下元 · 拆补转盘" */
    fun juDeclaration(cnNum: (Int) -> String): String = "$yinYang${cnNum(juNumber)}局 · $yuan · 拆补转盘"

    companion object {

    private val SAN_QI_LIU_YI = listOf("戊", "己", "庚", "辛", "壬", "癸", "丁", "丙", "乙")

    /** 洛书飞宫顺序（不含中宫5） */
    private val LUO_SHU_ORDER = listOf(1, 8, 3, 4, 9, 2, 7, 6)

    /** 节气 → (阴阳遁, 上中下元局数)。冬至后阳遁、夏至后阴遁。 */
    private val JIE_QI_JU: Map<String, Pair<String, String>> = mapOf(
        "冬至" to ("yang" to "174"), "惊蛰" to ("yang" to "174"),
        "小寒" to ("yang" to "285"), "大寒" to ("yang" to "396"),
        "春分" to ("yang" to "396"), "雨水" to ("yang" to "963"),
        "清明" to ("yang" to "417"), "立夏" to ("yang" to "417"),
        "立春" to ("yang" to "852"), "谷雨" to ("yang" to "528"),
        "小满" to ("yang" to "528"), "芒种" to ("yang" to "639"),
        "夏至" to ("yin" to "936"), "白露" to ("yin" to "936"),
        "小暑" to ("yin" to "825"), "大暑" to ("yin" to "714"),
        "秋分" to ("yin" to "714"), "立秋" to ("yin" to "258"),
        "寒露" to ("yin" to "693"), "立冬" to ("yin" to "693"),
        "处暑" to ("yin" to "147"), "霜降" to ("yin" to "582"),
        "小雪" to ("yin" to "582"), "大雪" to ("yin" to "471"),
    )

    /** 旬首 → 六仪 */
    private val XUN_SHOU_YI = mapOf(
        "甲子" to "戊", "甲戌" to "己", "甲申" to "庚",
        "甲午" to "辛", "甲辰" to "壬", "甲寅" to "癸",
    )

    /** 九星原位（含中宫天禽） */
    private val BASIC_XING = mapOf(
        1 to "天蓬", 8 to "天任", 3 to "天冲", 4 to "天辅",
        9 to "天英", 2 to "天芮", 7 to "天柱", 6 to "天心", 5 to "天禽",
    )

    /** 八门原位（中宫无门） */
    private val BASIC_MEN = mapOf(
        1 to "休门", 8 to "生门", 3 to "伤门", 4 to "杜门",
        9 to "景门", 2 to "死门", 7 to "惊门", 6 to "开门",
    )

    /** 八神顺序：值符 → 螣蛇 → 太阴 → 六合 → 白虎 → 玄武 → 九地 → 九天 */
    private val SHEN_ORDER = listOf("值符", "腾蛇", "太阴", "六合", "白虎", "玄武", "九地", "九天")

    /** 地支 → 九宫（奇门口径） */
    private val ZHI_TO_GONG = mapOf(
        "子" to 1, "丑" to 8, "寅" to 8, "卯" to 3, "辰" to 4, "巳" to 4,
        "午" to 9, "未" to 2, "申" to 2, "酉" to 7, "戌" to 6, "亥" to 6,
    )

    fun compile(solar: Solar): QiMenChart {
        val lunar = solar.lunar
        val siZhu = com.smartwash.divination.core.SiZhu(
            year = GanZhi.parse(lunar.yearInGanZhi),
            month = GanZhi.parse(lunar.monthInGanZhi),
            day = GanZhi.parse(lunar.dayInGanZhi),
            hour = GanZhi.parse(lunar.timeInGanZhi),
        )

        // 1. 定局：最近节气 + 日柱符头（甲/己日，五日一元）定上中下元
        val jieQiName = lunar.getPrevJieQi(true).getName()
        val (type, numbers) = JIE_QI_JU.getValue(jieQiName)
        val fuTouZhi = fuTouZhi(siZhu.day)
        val yuanIndex = when (fuTouZhi) {
            DiZhi.ZI, DiZhi.WU, DiZhi.MAO, DiZhi.YOU -> 0   // 上元
            DiZhi.YIN, DiZhi.SHEN, DiZhi.SI, DiZhi.HAI -> 1 // 中元
            else -> 2                                        // 下元
        }
        val yuanNames = listOf("上元", "中元", "下元")
        val juNumber = numbers[yuanIndex].toString().toInt()
        val isYang = type == "yang"

        // 2. 旬首六仪（时柱旬）
        val xunShouYi = XUN_SHOU_YI.getValue(siZhu.hour.xun.label)

        // 3. 地盘：戊从局数宫起，阳遁顺飞、阴遁逆飞
        val diPan = mutableMapOf<Int, String>()
        var gong = juNumber
        for (i in 0 until 9) {
            diPan[gong] = SAN_QI_LIU_YI[i]
            gong = if (isYang) (gong % 9) + 1 else (gong + 7) % 9 + 1
        }

        // 4. 天盘（转盘）：值符（旬首六仪所在宫）随时干落宫整体转动；中宫寄坤二
        val shiGan = siZhu.hour.gan.label
        val zhiFuYuanGong = diPan.entries.firstOrNull { it.value == xunShouYi }?.key ?: 2
        val shiGanGong = findGanGong(diPan, shiGan) ?: zhiFuYuanGong

        val zhiFuIdx = LUO_SHU_ORDER.indexOf(zhiFuYuanGong)
        val shiGanIdx = LUO_SHU_ORDER.indexOf(shiGanGong)
        // 阳遁：值符宫 → 时干落宫顺时针走 n 步，天盘整体顺转；阴遁逆转（等价于反向步数）
        val tianPanStep = if (isYang) steps(zhiFuIdx, shiGanIdx) else steps(shiGanIdx, zhiFuIdx)
        val tianPan = mutableMapOf<Int, String>()
        for (i in 0 until 8) {
            val diGong = LUO_SHU_ORDER[i]
            val newIdx = if (isYang) (i + tianPanStep) % 8 else (i - tianPanStep + 8) % 8
            tianPan[LUO_SHU_ORDER[newIdx]] = diPan.getValue(diGong)
        }
        tianPan[5] = diPan.getValue(5)
        val zhiFuLuoGong = shiGanGong

        // 5. 九星（转盘）：值符星随时干落宫，天禽寄坤二（禽芮合称）
        val zhiFuXing = BASIC_XING.getValue(zhiFuYuanGong)
        val jiuXing = mutableMapOf<Int, String>()
        val xingStep = steps(zhiFuIdx, shiGanIdx)
        for (i in 0 until 8) {
            val originGong = LUO_SHU_ORDER[i]
            val newGong = LUO_SHU_ORDER[(i + xingStep) % 8]
            jiuXing[newGong] = if (originGong == 2) "禽芮" else BASIC_XING.getValue(originGong)
        }
        jiuXing[5] = ""

        // 6. 八门（转盘）：值使门加时——从值符宫按宫数顺序阳顺阴逆走时柱旬内序数步
        val zhiShiMen = BASIC_MEN.getValue(zhiFuYuanGong)
        val step = siZhu.hour.jiaZiIndex % 10
        var g = zhiFuYuanGong
        repeat(step) {
            g = if (isYang) (g % 9) + 1 else (g + 7) % 9 + 1
        }
        val zhiShiGongRaw = g
        val zhiShiGong = if (g == 5) 2 else g
        val fromIdx = LUO_SHU_ORDER.indexOf(zhiFuYuanGong)
        val toIdx = LUO_SHU_ORDER.indexOf(zhiShiGong)
        val menStep = steps(fromIdx, toIdx)
        val baMen = mutableMapOf<Int, String>(5 to "")
        for (i in 0 until 8) {
            baMen[LUO_SHU_ORDER[(i + menStep) % 8]] = BASIC_MEN.getValue(LUO_SHU_ORDER[i])
        }

        // 7. 八神：值符落宫起，阳遁顺时针、阴遁逆时针
        val baShen = mutableMapOf<Int, String>()
        val gongOrder = if (isYang) LUO_SHU_ORDER else listOf(1, 6, 7, 2, 9, 4, 3, 8)
        val zhiFuGongIdx = gongOrder.indexOf(zhiFuLuoGong)
        baShen[5] = ""
        for (i in gongOrder.indices) {
            baShen[gongOrder[(zhiFuGongIdx + i) % 8]] = SHEN_ORDER[i]
        }

        // 8. 暗干：时干加值使落宫起，按三奇六仪顺序阳顺阴逆排满九宫；甲隐于旬首仪、寄中五
        val anGan = mutableMapOf<Int, String>()
        val isJia = shiGan == "甲"
        val shiYi = if (isJia) xunShouYi else shiGan
        val anchorGong = if (isJia) 5 else zhiShiGongRaw
        val startIdx = SAN_QI_LIU_YI.indexOf(shiYi)
        var ag = anchorGong
        for (i in 0 until 9) {
            anGan[ag] = SAN_QI_LIU_YI[(startIdx + i) % 9]
            ag = if (isYang) (ag % 9) + 1 else (ag + 7) % 9 + 1
        }

        // 9. 空亡与驿马（时柱口径）
        val kong = siZhu.hour.xunKong
        val kongWangZhi = listOf(kong.first.label, kong.second.label)
        val kongWangGong = kongWangZhi.mapNotNull { ZHI_TO_GONG[it] }.distinct()
        val maZhi = when (siZhu.hour.zhi) {
            DiZhi.YIN, DiZhi.WU, DiZhi.XU -> DiZhi.SHEN
            DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN -> DiZhi.YIN
            DiZhi.SI, DiZhi.YOU, DiZhi.CHOU -> DiZhi.HAI
            else -> DiZhi.SI
        }

        return QiMenChart(
            siZhu = siZhu,
            jieQiName = jieQiName,
            yinYang = if (isYang) "阳遁" else "阴遁",
            juNumber = juNumber,
            yuan = yuanNames[yuanIndex],
            xunShouYi = xunShouYi,
            diPan = diPan,
            tianPan = tianPan,
            jiuXing = jiuXing,
            baMen = baMen,
            baShen = baShen,
            anGan = anGan,
            zhiFuXing = zhiFuXing,
            zhiFuLuoGong = zhiFuLuoGong,
            zhiFuYuanGong = zhiFuYuanGong,
            zhiShiMen = zhiShiMen,
            zhiShiGong = zhiShiGong,
            zhiShiGongRaw = zhiShiGongRaw,
            kongWangZhi = kongWangZhi,
            kongWangGong = kongWangGong,
            maZhi = maZhi.label,
            maGong = ZHI_TO_GONG.getValue(maZhi.label),
        )
    }

    /** 天干在地盘所在宫（甲不现盘、中宫寄坤二；找不到返回 null 由调用方兜底） */
    private fun findGanGong(diPan: Map<Int, String>, gan: String): Int? {
        if (gan == "甲") return null
        diPan.entries.firstOrNull { it.value == gan && it.key != 5 }?.let { return it.key }
        return if (diPan[5] == gan) 2 else null
    }

    /** 顺时针序上的步数（from → to） */
    private fun steps(fromIdx: Int, toIdx: Int): Int = (toIdx - fromIdx + 8) % 8

    /** 符头地支：日柱往前取最近的甲/己日 */
    private fun fuTouZhi(day: GanZhi): DiZhi {
        val fuTou = day.jiaZiIndex - (day.jiaZiIndex % 5)
        return DiZhi.entries[fuTou % 12]
    }
}
}
