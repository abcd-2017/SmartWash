package com.smartwash.feature.game.repository

import com.smartwash.feature.game.network.GameVo

/**
 * 游戏数据契约（接口抽象，ViewModel 面向接口消费，
 * JVM 单测以 Fake 替换；实现见 [GameRepositoryImpl]）。
 * data 为 null 一律按失败抛 [com.smartwash.common.network.exception.NetworkException]，
 * 让上层能区分「无数据」与「请求失败」。
 */
interface GameRepository {

    /** 游戏库全量列表（enabled=1 按 sort 升序，<100 不分页）；空列表 = 全部未上架/未配置 */
    suspend fun getGameList(): List<GameVo>
}
