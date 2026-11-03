package com.smartwash.feature.mood.repository

import com.smartwash.feature.mood.network.MoodCalendarDayVo
import com.smartwash.feature.mood.network.MoodRecordVo
import com.smartwash.feature.mood.network.MoodStatsVo
import com.smartwash.feature.mood.network.SaveMoodRequest

/**
 * 情绪数据契约（接口抽象，ViewModel 面向接口消费，JVM 单测以 Fake 替换；
 * 实现见 [MoodRepositoryImpl]）。data 为 null 一律按失败抛 NetworkException，
 * 让上层能区分「无数据」与「请求失败」。
 */
interface MoodRepository {

    /** 记录/覆盖今日情绪（upsert），返回保存后的当日记录 */
    suspend fun saveToday(request: SaveMoodRequest): MoodRecordVo

    /** 月历热图数据（yyyy-MM），只含当月有记录的日期，空月为空列表 */
    suspend fun monthCalendar(ym: String): List<MoodCalendarDayVo>

    /** 月度趋势统计（yyyy-MM），空月 recordedDays=0、averageLevel=null */
    suspend fun monthStats(ym: String): MoodStatsVo
}
