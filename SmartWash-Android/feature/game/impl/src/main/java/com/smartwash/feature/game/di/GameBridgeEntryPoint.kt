package com.smartwash.feature.game.di

import com.smartwash.common.ui.webview.GameBridgeApiProvider
import com.smartwash.network.api.GameBridgeApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 游戏域 H5 数据桥入口：Composable 经 EntryPointAccessors.fromActivity(...) 取用。
 *
 * 契约继承 common:ui 的 [GameBridgeApiProvider]（下沉的公共能力）；@EntryPoint 实现在
 * feature 本地——Hilt 依赖图（GameBridgeApi 由 app 壳 GameBridgeModule @Provides）
 * 全组件共享，feature 声明取用方法即可，无需依赖 app 模块。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GameBridgeEntryPoint : GameBridgeApiProvider {
    override fun gameBridgeApi(): GameBridgeApi
}
