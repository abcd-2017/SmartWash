package com.smartwash.repository

import com.smartwash.database.dao.LaundryItemDao
import com.smartwash.database.entity.LaundryItemEntity
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.vo.laundry.LaundryItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaundryRepository @Inject constructor(
    private val laundryItemsApi: LaundryItemsApi,
    private val laundryItemDao: LaundryItemDao,
) {
    /**
     * Cache-first 策略：先读缓存，再请求网络更新缓存。
     * - 有缓存时：网络失败静默处理，返回缓存数据
     * - 无缓存时：网络成功返回数据，失败抛异常
     */
    suspend fun getLaundryItems(): List<LaundryItem> {
        val cached = laundryItemDao.getAll().map { it.toVo() }

        return try {
            val networkData = laundryItemsApi.getLaundryItems().data ?: emptyList()
            laundryItemDao.deleteAll()
            laundryItemDao.insertAll(networkData.map { LaundryItemEntity.fromVo(it) })
            networkData
        } catch (e: Exception) {
            if (cached.isNotEmpty()) cached else throw e
        }
    }
}
