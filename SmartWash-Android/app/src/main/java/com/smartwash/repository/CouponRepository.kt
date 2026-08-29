package com.smartwash.repository

import androidx.room.withTransaction
import com.smartwash.R
import com.smartwash.database.AppDatabase
import com.smartwash.database.dao.CouponVoDao
import com.smartwash.database.entity.CouponVoEntity
import com.smartwash.network.api.CouponApi
import com.smartwash.network.exception.NetworkException
import com.smartwash.network.vo.coupon.AllCouponsVo
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.network.vo.coupon.UserCouponVo
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CouponRepository @Inject constructor(
    private val couponApi: CouponApi,
    private val couponVoDao: CouponVoDao,
    private val appDatabase: AppDatabase,
) {
    /**
     * Cache-first 策略：先读缓存，再请求网络更新缓存。
     * - 有缓存时：网络失败静默处理，返回缓存数据
     * - 无缓存时：网络成功返回数据，失败抛异常
     */
    suspend fun getAllCoupon(): List<CouponVo> {
        val cached = couponVoDao.getAll().map { it.toVo() }

        return try {
            // data 为 null 按失败处理，走下方 catch 的缓存降级，避免用空数据覆盖缓存
            val networkData = couponApi.getAllCoupon().data
                ?: throw NetworkException("优惠券数据为空", R.string.error_network_fail)
            // deleteAll + insertAll 包进事务，中途失败不会清空缓存
            appDatabase.withTransaction {
                couponVoDao.deleteAll()
                couponVoDao.insertAll(networkData.map { CouponVoEntity.fromVo(it) })
            }
            networkData
        } catch (e: CancellationException) {
            // 取消不作为网络错误，直接上抛
            throw e
        } catch (e: Exception) {
            if (cached.isNotEmpty()) cached else throw e
        }
    }

    /**
     * 优惠券页聚合数据（可领取 / 已领取 / 历史三类，一次请求）。
     * Cache-first 策略：网络失败时可用券降级为 Room 缓存（已领/历史无本地缓存表，置空）；
     * 无任何缓存时抛异常，由上层转 Error 态。
     */
    suspend fun getAllCoupons(): AllCouponsVo {
        val cachedAvailable = couponVoDao.getAll().map { it.toVo() }

        return try {
            // data 为 null 按失败处理，让 UI 能区分"无数据"与"请求失败"
            val networkData = couponApi.getAllCoupons().data
                ?: throw NetworkException("优惠券数据为空", R.string.error_network_fail)
            // 仅可用券有本地缓存表；deleteAll + insertAll 包进事务，中途失败不会清空缓存
            appDatabase.withTransaction {
                couponVoDao.deleteAll()
                couponVoDao.insertAll(networkData.available.map { CouponVoEntity.fromVo(it) })
            }
            networkData
        } catch (e: CancellationException) {
            // 取消不作为网络错误，直接上抛
            throw e
        } catch (e: Exception) {
            if (cachedAvailable.isNotEmpty()) {
                AllCouponsVo(
                    available = cachedAvailable,
                    claimed = emptyList(),
                    historical = emptyList(),
                )
            } else {
                throw e
            }
        }
    }

    suspend fun receiveCoupon(couponId: Long): Boolean {
        return couponApi.receiveCoupon(couponId).data == true
    }

    suspend fun getCanUseCoupon(orderId: Long): List<UserCouponVo> {
        // data 为 null 按失败处理，让 UI 能区分"无可用优惠券"与"请求失败"
        return couponApi.getCanUseCoupon(orderId).data
            ?: throw NetworkException("可用优惠券数据为空", R.string.error_network_fail)
    }
}
