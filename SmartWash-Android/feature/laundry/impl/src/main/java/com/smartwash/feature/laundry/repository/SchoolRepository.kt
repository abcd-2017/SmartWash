package com.smartwash.feature.laundry.repository

import com.smartwash.common.database.dao.SchoolNameDao
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.laundry.R
import com.smartwash.feature.laundry.network.api.SchoolApi
import com.smartwash.feature.laundry.network.vo.SchoolName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolRepository @Inject constructor(
    private val schoolApi: SchoolApi,
    private val schoolNameDao: SchoolNameDao,
) {
    // 内存缓存仅允许在 mutex 锁内读写，避免多协程并发读写不一致
    private val mutex = Mutex()
    private var allSchools: List<SchoolName> = emptyList()

    /**
     * 三级缓存策略：内存 → 数据库 → 网络。
     * 空搜索时会缓存完整列表并后台刷新。
     * 整个加载链路持有 mutex：冷启动并发请求会串行等待，随后直接命中内存缓存（single-flight）。
     */
    suspend fun getAllSchools(keyword: String): List<SchoolName> = mutex.withLock {
        // 1. 内存缓存命中
        if (allSchools.isNotEmpty()) {
            return@withLock filterSchools(keyword)
        }

        // 2. 数据库缓存命中
        val cached = schoolNameDao.getAll().map { it.toVo() }
        if (cached.isNotEmpty()) {
            // 降级留痕：内存未命中（冷启动首个请求才会走到），回落 Room 缓存并后台刷新网络；
            // 之后内存缓存已建立，本 warn 每次进程生命周期至多出现一次
            AppLogger.warn(TAG, "getAllSchools: 内存缓存未命中，降级读 Room 缓存（内存→Room, ${cached.size} 条），随后后台刷新网络")
            allSchools = cached
            refreshFromNetwork()
            return@withLock filterSchools(keyword)
        }

        // 3. 从网络加载；data 为 null 按失败处理，让 UI 能区分"无学校"与"请求失败"
        val networkData = schoolApi.getAllSchool(keyword).data
            ?: throw NetworkException("学校数据为空", R.string.error_network_fail)
        if (keyword.isBlank()) {
            allSchools = networkData
            schoolNameDao.deleteAll()
            schoolNameDao.insertAll(networkData.map { it.toEntity() })
            // 冷启动首次全量加载（缓存建立时刻）：低频，但搜索链路整体属高频路径，只打 debug
            AppLogger.debug(TAG, "getAllSchools: 学校数据网络加载成功并入库, size=${networkData.size}")
        }
        networkData
    }

    // 仅在 getAllSchools 持锁期间调用，内部不再加锁（Mutex 不可重入）
    private suspend fun refreshFromNetwork() {
        try {
            val networkData = schoolApi.getAllSchool("").data ?: return
            allSchools = networkData
            schoolNameDao.deleteAll()
            schoolNameDao.insertAll(networkData.map { it.toEntity() })
        } catch (e: Exception) {
            // 后台刷新失败静默处理（用户已拿到 Room 数据，无感知）：
            // 属降级留痕而非用户可感知的失败，打 warn 不打 error
            AppLogger.warn(TAG, "refreshFromNetwork: 后台刷新学校数据失败，继续使用 Room 缓存: ${e.message}", e)
        }
    }

    private fun filterSchools(keyword: String): List<SchoolName> {
        return if (keyword.isBlank()) {
            allSchools
        } else {
            allSchools.filter { it.schoolName.contains(keyword, ignoreCase = true) }
        }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "SchoolRepository"
    }
}
