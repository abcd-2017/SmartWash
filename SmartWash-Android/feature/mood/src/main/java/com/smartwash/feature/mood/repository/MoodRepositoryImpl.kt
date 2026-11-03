package com.smartwash.feature.mood.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.mood.network.MoodApi
import com.smartwash.feature.mood.network.MoodCalendarDayVo
import com.smartwash.feature.mood.network.MoodRecordVo
import com.smartwash.feature.mood.network.MoodStatsVo
import com.smartwash.feature.mood.network.SaveMoodRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 情绪数据实现：薄封装 MoodApi，统一「data 为 null 即失败」语义
 * （业务失败 code=201 已被 ResponseInterceptor 转译为 NetworkException 抛出，
 * 这里只兜住 code=200 但 data 缺失的分支），成功以 code=200 为准。
 */
@Singleton
class MoodRepositoryImpl @Inject constructor(
    private val moodApi: MoodApi,
) : MoodRepository {

    override suspend fun saveToday(request: SaveMoodRequest): MoodRecordVo {
        return moodApi.saveToday(request).data
            ?: throw NetworkException("心情记录为空", R.string.error_network_fail)
    }

    override suspend fun monthCalendar(ym: String): List<MoodCalendarDayVo> {
        // 空月 data=[] 是合法成功；data 缺失（null）才按失败处理
        return moodApi.monthCalendar(ym).data
            ?: throw NetworkException("月历数据为空", R.string.error_network_fail)
    }

    override suspend fun monthStats(ym: String): MoodStatsVo {
        return moodApi.monthStats(ym).data
            ?: throw NetworkException("趋势数据为空", R.string.error_network_fail)
    }
}
