package com.smartwash.repository

import com.smartwash.R
import com.smartwash.database.dao.SchoolNameDao
import com.smartwash.database.entity.SchoolNameEntity
import com.smartwash.network.api.SchoolApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.school.SchoolName
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
            schoolNameDao.insertAll(networkData.map { SchoolNameEntity.fromVo(it) })
        }
        networkData
    }

    // 仅在 getAllSchools 持锁期间调用，内部不再加锁（Mutex 不可重入）
    private suspend fun refreshFromNetwork() {
        try {
            val networkData = schoolApi.getAllSchool("").data ?: return
            allSchools = networkData
            schoolNameDao.deleteAll()
            schoolNameDao.insertAll(networkData.map { SchoolNameEntity.fromVo(it) })
        } catch (_: Exception) {
            // 后台刷新失败静默处理
        }
    }

    private fun filterSchools(keyword: String): List<SchoolName> {
        return if (keyword.isBlank()) {
            allSchools
        } else {
            allSchools.filter { it.schoolName.contains(keyword, ignoreCase = true) }
        }
    }
}
