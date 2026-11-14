package com.smartwash.network.api

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * 游戏桥代理接口（H5 游戏经 SmartWashGameBridge 读写存档/成绩/关卡）。
 * 后端契约：WebGameDataController，路径前缀 /web/auth/game（需 ROLE_USER）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；每个方法带 @RequireAuthorization，由 RequestInterceptor 注入 Bearer token。
 *
 * 桥代理意义：token 只在原生层，H5 拿不到凭证，白名单 host 外桥不生效（fail-closed）。
 */
interface GameBridgeApi {

    /** 读存档：无存档时 data.stateJson 为 null */
    @GET("web/auth/game/state")
    @RequireAuthorization
    suspend fun getState(@Query("gameKey") gameKey: String): ApiResult<GameBridgeStateVo>

    /** 写存档：upsert，stateJson 游戏自定义 */
    @PUT("web/auth/game/state")
    @RequireAuthorization
    suspend fun saveState(@Body body: GameBridgeStateUpdateBody): ApiResult<GameBridgeStateVo>

    /** 上报成绩：取历史最佳（方向由 game_config.metric_order 决定） */
    @POST("web/auth/game/score")
    @RequireAuthorization
    suspend fun reportScore(@Body body: GameBridgeScoreReportBody): ApiResult<String>

    /** 排行榜 TOP N（含我的排名） */
    @GET("web/auth/game/leaderboard")
    @RequireAuthorization
    suspend fun getLeaderboard(@Query("gameKey") gameKey: String,
                               @Query("limit") limit: Int): ApiResult<GameBridgeLeaderboardVo>

    /** 关卡下发：启用中的关卡 + 版本号 */
    @GET("web/auth/game/levels")
    @RequireAuthorization
    suspend fun getLevels(@Query("gameKey") gameKey: String): ApiResult<GameBridgeLevelsVo>
}

// ==================== 请求体 ====================

/** 存档写入请求（对齐后端 GameStateUpdateFrom） */
@Keep
data class GameBridgeStateUpdateBody(
    val gameKey: String,
    val stateJson: String,
    val schemaVer: Int? = null,
)

/** 成绩上报请求（对齐后端 GameScoreReportFrom） */
@Keep
data class GameBridgeScoreReportBody(
    val gameKey: String,
    val metricValue: Long,
    val metricExtra: Long? = null,
    val metricJson: String? = null,
)

// ==================== 响应 VO ====================

/** 存档视图（对齐后端 GameStateVo） */
@Keep
data class GameBridgeStateVo(
    val gameKey: String = "",
    val stateJson: String? = null,
    val schemaVer: Int? = null,
    val updatedAt: String? = null,
)

/** 关卡下发视图（对齐后端 GameLevelsVo） */
@Keep
data class GameBridgeLevelsVo(
    val gameKey: String = "",
    val version: Int = 1,
    val levels: List<GameBridgeLevelVo> = emptyList(),
)

/** 单个关卡（对齐后端 GameLevelVo） */
@Keep
data class GameBridgeLevelVo(
    val levelNo: Int = 0,
    val name: String? = null,
    val dataJson: String = "",
    val difficulty: Int? = null,
    val sort: Int = 0,
)

/** 排行榜视图（对齐后端 GameLeaderboardVo） */
@Keep
data class GameBridgeLeaderboardVo(
    val gameKey: String = "",
    val metricLabel: String? = null,
    val metricOrder: String = "desc",
    val entries: List<GameBridgeLeaderboardEntryVo> = emptyList(),
    val my: GameBridgeMyRankVo? = null,
)

/** 榜内条目 */
@Keep
data class GameBridgeLeaderboardEntryVo(
    val rank: Int = 0,
    val userId: Long = 0,
    val maskedPhone: String? = null,
    val avatar: String? = null,
    val metricValue: Long = 0,
    val metricExtra: Long? = null,
)

/** 我的排名 */
@Keep
data class GameBridgeMyRankVo(
    val rank: Int? = null,
    val metricValue: Long? = null,
)
