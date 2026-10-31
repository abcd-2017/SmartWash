package com.smartwash.feature.game.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.game.network.GameApi
import com.smartwash.feature.game.network.GameVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 游戏数据实现：薄封装 GameApi，统一「data 为 null 即失败」语义
 * （业务失败已被 ResponseInterceptor 转译为 NetworkException 抛出，
 * 这里只兜住 code=200 但 data 缺失的分支），成功以 code=200 为准。
 * 无本地缓存（列表实时反映后端上下架开关，下发即生效）。
 */
@Singleton
class GameRepositoryImpl @Inject constructor(
    private val gameApi: GameApi,
) : GameRepository {

    override suspend fun getGameList(): List<GameVo> {
        return gameApi.getGameList().data
            ?: throw NetworkException("游戏列表为空", R.string.error_network_fail)
    }
}
