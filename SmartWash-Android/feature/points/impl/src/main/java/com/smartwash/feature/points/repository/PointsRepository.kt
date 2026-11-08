package com.smartwash.feature.points.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.points.api.POINTS_LEADERBOARD_SCOPE_MONTH
import com.smartwash.feature.points.api.PointsServiceApi
import com.smartwash.feature.points.api.model.PointsLeaderboardVo
import com.smartwash.feature.points.api.model.PointsOverviewVo
import com.smartwash.feature.points.api.model.SignInResultVo
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 积分仓库：积分网络链路的唯一出口（ViewModel 不直接持有 API，MVVM 硬规则）。
 *
 * data 为 null 统一按失败处理上抛 [NetworkException]（对齐 coupon 仓库模式）——
 * code=200 信封但 data 缺失是异常响应，让上层能区分「无数据」与「请求失败」
 * （ResponseInterceptor 不拦此场景）。本域 v1 无本地缓存表（流水/榜单无离线诉求），
 * 失败显式 Error 态 + 重试。
 */
@Singleton
class PointsRepository @Inject constructor(
    private val pointsServiceApi: PointsServiceApi,
) {

    /** 我的积分概览：余额 + 累计获得 + 今日已签 + 连签 + 最近流水 */
    suspend fun getOverview(): PointsOverviewVo =
        pointsServiceApi.getOverview().data
            ?: run {
                AppLogger.warn(TAG, "getOverview 响应 data 为空，按失败处理上抛")
                throw NetworkException("积分概览数据为空", R.string.error_network_fail)
            }

    /**
     * 每日签到（后端幂等：当日重复请求 alreadySigned=true 且不重复加分）。
     * 幂等结果原样上抛，成功/已签的反馈区分由 ViewModel 处理。
     */
    suspend fun signIn(): SignInResultVo =
        pointsServiceApi.signIn().data
            ?: run {
                AppLogger.warn(TAG, "signIn 响应 data 为空，按失败处理上抛")
                throw NetworkException("签到结果数据为空", R.string.error_network_fail)
            }

    /** 积分月榜（scope 固定 month，后端当前仅支持该口径） */
    suspend fun getMonthLeaderboard(): PointsLeaderboardVo =
        pointsServiceApi.getLeaderboard(POINTS_LEADERBOARD_SCOPE_MONTH).data
            ?: run {
                AppLogger.warn(TAG, "getMonthLeaderboard 响应 data 为空，按失败处理上抛")
                throw NetworkException("积分月榜数据为空", R.string.error_network_fail)
            }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "PointsRepository"
    }
}
