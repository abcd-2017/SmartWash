package com.smartwash.repository

import androidx.room.withTransaction
import com.smartwash.R
import com.smartwash.database.AppDatabase
import com.smartwash.database.dao.LaundryItemDao
import com.smartwash.database.entity.LaundryItemEntity
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.laundry.LaundryItem
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaundryRepository @Inject constructor(
    private val laundryItemsApi: LaundryItemsApi,
    private val laundryItemDao: LaundryItemDao,
    private val appDatabase: AppDatabase,
) {
    /**
     * 读取本地 Room 缓存，供页面做"缓存先行"展示；DAO 统一收回 Repository 内部，不对 VM 暴露
     */
    suspend fun getCachedLaundryItems(): List<LaundryItem> {
        return laundryItemDao.getAll().map { it.toVo() }
    }

    /**
     * Cache-first 策略：先读缓存，再请求网络更新缓存。
     * - 有缓存时：网络失败静默处理，返回缓存数据
     * - 无缓存时：网络成功返回数据，失败抛异常
     */
    suspend fun getLaundryItems(): List<LaundryItem> {
        val cached = laundryItemDao.getAll().map { it.toVo() }

        return try {
            // data 为 null 按失败处理，走下方 catch 的缓存降级，避免用空数据覆盖缓存
            val networkData = laundryItemsApi.getLaundryItems().data
                ?: throw NetworkException("洗衣项目数据为空", R.string.error_network_fail)
            // deleteAll + insertAll 包进事务，中途失败不会清空缓存
            appDatabase.withTransaction {
                laundryItemDao.deleteAll()
                laundryItemDao.insertAll(networkData.map { LaundryItemEntity.fromVo(it) })
            }
            networkData
        } catch (e: CancellationException) {
            // 取消不作为网络错误，直接上抛
            throw e
        } catch (e: Exception) {
            if (cached.isNotEmpty()) cached else throw e
        }
    }
}
