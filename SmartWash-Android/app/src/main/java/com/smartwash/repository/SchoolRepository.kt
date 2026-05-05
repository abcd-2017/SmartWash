package com.smartwash.repository

import com.smartwash.database.dao.SchoolNameDao
import com.smartwash.database.entity.SchoolNameEntity
import com.smartwash.network.api.SchoolApi
import com.smartwash.network.vo.school.SchoolName
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolRepository @Inject constructor(
    private val schoolApi: SchoolApi,
    private val schoolNameDao: SchoolNameDao,
) {
    private var allSchools: List<SchoolName> = emptyList()

    /**
     * 三级缓存策略：内存 → 数据库 → 网络。
     * 空搜索时会缓存完整列表并后台刷新。
     */
    suspend fun getAllSchools(keyword: String): List<SchoolName> {
        // 1. 内存缓存命中
        if (allSchools.isNotEmpty()) {
            return filterSchools(keyword)
        }

        // 2. 数据库缓存命中
        val cached = schoolNameDao.getAll().map { it.toVo() }
        if (cached.isNotEmpty()) {
            allSchools = cached
            refreshFromNetwork()
            return filterSchools(keyword)
        }

        // 3. 从网络加载
        val networkData = schoolApi.getAllSchool(keyword).data.orEmpty()
        if (keyword.isBlank()) {
            allSchools = networkData
            schoolNameDao.deleteAll()
            schoolNameDao.insertAll(networkData.map { SchoolNameEntity.fromVo(it) })
        }
        return networkData
    }

    private suspend fun refreshFromNetwork() {
        try {
            val networkData = schoolApi.getAllSchool("").data.orEmpty()
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
