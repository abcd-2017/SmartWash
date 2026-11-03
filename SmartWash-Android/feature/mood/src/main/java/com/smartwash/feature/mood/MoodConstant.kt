package com.smartwash.feature.mood

import java.time.LocalDate
import java.time.YearMonth

/**
 * 情绪气象站模块内常量（非用户可见；用户可见文案一律进 res/values/strings.xml）。
 */
object MoodConstant {

    /** 情绪等级下界（1=暴雨），与后端 CHECK 约束一致 */
    const val LEVEL_MIN = 1

    /** 情绪等级上界（5=大晴），与后端 CHECK 约束一致 */
    const val LEVEL_MAX = 5

    /** 词最大长度（后端 word ≤20 字符，空白按 null 存） */
    const val WORD_MAX_LENGTH = 20

    /** 一句话备注最大长度（后端 note ≤200 字符，空白按 null 存） */
    const val NOTE_MAX_LENGTH = 200
}

/**
 * 月份参数工具（纯 JVM，可单测）：ym 字符串（yyyy-MM）与 YearMonth 互转 + 平移。
 * 后端 calendar/stats 的 ym 必填，非法格式返回业务失败（code=201），端上自行保证格式。
 */
object MoodMonth {

    /** 当月的 ym（yyyy-MM，按本地时区） */
    fun current(): String = format(YearMonth.now())

    /** YearMonth → yyyy-MM（补零，跨年平移安全） */
    fun format(ym: YearMonth): String = "%04d-%02d".format(ym.year, ym.monthValue)

    /** yyyy-MM → YearMonth；解析失败返回 null（调用方兜底回当前月） */
    fun parse(ym: String): YearMonth? = runCatching { YearMonth.parse(ym) }.getOrNull()

    /** ym 平移 months 个月（负数为往前） */
    fun shift(ym: String, months: Long): String = format(parse(ym)?.plusMonths(months) ?: YearMonth.now())

    /** LocalDate 所属月的 ym（今日记录页拉当月月历做回填用） */
    fun of(date: LocalDate): String = format(YearMonth.from(date))
}
