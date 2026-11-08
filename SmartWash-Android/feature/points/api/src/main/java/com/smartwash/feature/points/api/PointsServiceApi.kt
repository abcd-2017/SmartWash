package com.smartwash.feature.points.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.points.api.model.PointsLeaderboardVo
import com.smartwash.feature.points.api.model.PointsOverviewVo
import com.smartwash.feature.points.api.model.SignInResultVo
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** 榜单口径：月榜（后端 PointsWebController 当前仅支持该值） */
const val POINTS_LEADERBOARD_SCOPE_MONTH = "month"

/**
 * 积分域网络接口（路径/参数/响应结构以后端 PointsWebController 为契约源，
 * 接口需 ROLE_USER——/web/auth 路径由 SecurityConfig 通配，@RequireAuthorization 注入 Bearer）。
 *
 * 放置说明（与 coupon 先例的差异）：本域把 Retrofit 接口与 Gson 载荷一起放 api 模块——
 * points 无跨模块消费方（不存在 CouponApi 式「他人 impl 注入本域服务」的需求），
 * VO 即领域载荷，impl 免去一层映射；依赖方向 api → common:network 合法（铁律 1）。
 */
interface PointsServiceApi {

    /** 我的积分概览：余额 + 累计获得 + 今日已签 + 连签进度 + 最近流水 */
    @GET("/web/auth/points/me")
    @RequireAuthorization
    suspend fun getOverview(): ApiResult<PointsOverviewVo>

    /** 每日签到（后端幂等：重复请求 alreadySigned=true 不重复加分） */
    @POST("/web/auth/points/sign-in")
    @RequireAuthorization
    suspend fun signIn(): ApiResult<SignInResultVo>

    /** 积分月榜：TOP 50（手机号脱敏）+ 我的排名；scope 当前仅 month */
    @GET("/web/auth/points/leaderboard")
    @RequireAuthorization
    suspend fun getLeaderboard(@Query("scope") scope: String): ApiResult<PointsLeaderboardVo>
}
