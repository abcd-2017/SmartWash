package com.smartwash.feature.update.event

import com.smartwash.feature.update.model.AppVersionVo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 热更新事件（feature:update → 壳层广播，模式仿 SessionEventBus）。
 *
 * 「发现新版本」由 UpdateInitTask（启动静默检查）发出，壳层 MainActivity 收集后
 * 转弹窗状态；是否强制更新看 [UpdateAvailable.version] 的 forceUpdate 字段，
 * 壳层沿用既有 4 态弹窗分流。
 */
sealed class UpdateEvent {
    /** 发现新版本（version.forceUpdate = true 时为强制更新） */
    data class UpdateAvailable(val version: AppVersionVo) : UpdateEvent()
}

/**
 * 更新事件总线：feature:update 检查出新版本后广播给壳层，
 * 壳层不感知检查时机（启动 InitTask / 后续任意入口）只消费事件。
 *
 * replay = 1：启动检查在 App.onCreate 即发起（InitEngine 非阻塞任务），可能早于
 * MainActivity 订阅——无重放则事件被丢弃、本次启动不弹更新；重放带来的重复打扰
 * 由 UpdateViewModel 的「已关闭版本」防重挡下。
 */
@Singleton
class UpdateEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<UpdateEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<UpdateEvent> = _events.asSharedFlow()

    /** 广播「发现新版本」 */
    fun notifyUpdateAvailable(version: AppVersionVo) {
        _events.tryEmit(UpdateEvent.UpdateAvailable(version))
    }
}
