package com.smartwash.deeplink

import com.smartwash.network.api.DeepLinkApi
import com.smartwash.network.api.DeeplinkRouteVo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 深链路由字典仓库（接口抽象便于单测替换 Fake；实现见 [DeepLinkRepositoryImpl]）。
 * 字典仅做未知 host 的元数据兜底（name/requireLogin 供后续升级引导），
 * 失败静默返回空列表，**不影响本地解析**。
 */
interface DeepLinkRepository {

    /**
     * 拉取路由字典（首次使用时拉取并内存缓存，后续走缓存）。
     * 请求失败静默返回空列表且**不缓存失败结果**——下次调用自动重试。
     */
    suspend fun getRoutes(): List<DeeplinkRouteVo>
}

@Singleton
class DeepLinkRepositoryImpl @Inject constructor(
    private val deepLinkApi: DeepLinkApi,
) : DeepLinkRepository {

    /** 内存缓存：null = 尚未拉取或历史失败（可重试），非 null = 已成功拉取（可能为空字典） */
    @Volatile
    private var cache: List<DeeplinkRouteVo>? = null

    /** 单飞锁：并发首次拉取只发一次请求 */
    private val mutex = Mutex()

    override suspend fun getRoutes(): List<DeeplinkRouteVo> {
        cache?.let { return it }
        return mutex.withLock {
            // 双检：等锁期间可能已被并发调用回填
            cache?.let { return it }
            try {
                deepLinkApi.getRoutes().data.orEmpty().also { cache = it }
            } catch (e: CancellationException) {
                // 协程取消必须原样 rethrow（取消不能被吞成「失败静默」）
                throw e
            } catch (e: Exception) {
                // 失败静默：本地解析不受影响；不缓存失败结果，下次调用可重试
                emptyList()
            }
        }
    }
}
