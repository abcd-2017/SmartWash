package com.smartwash.repository

import com.smartwash.database.dao.CouponVoDao
import com.smartwash.database.entity.CouponVoEntity
import com.smartwash.network.api.CouponApi
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.network.vo.coupon.UserCouponVo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CouponRepository @Inject constructor(
    private val couponApi: CouponApi,
    private val couponVoDao: CouponVoDao,
) {
    /**
     * Cache-first 策略：先读缓存，再请求网络更新缓存。
     * - 有缓存时：网络失败静默处理，返回缓存数据
     * - 无缓存时：网络成功返回数据，失败抛异常
     */
    suspend fun getAllCoupon(): List<CouponVo> {
        val cached = couponVoDao.getAll().map { it.toVo() }

        return try {
            val networkData = couponApi.getAllCoupon().data ?: emptyList()
            couponVoDao.deleteAll()
            couponVoDao.insertAll(networkData.map { CouponVoEntity.fromVo(it) })
            networkData
        } catch (e: Exception) {
            if (cached.isNotEmpty()) cached else throw e
        }
    }

    suspend fun receiveCoupon(couponId: Long): Boolean {
        return couponApi.receiveCoupon(couponId).data == true
    }

    suspend fun getCanUseCoupon(orderId: Long): List<UserCouponVo> {
        return couponApi.getCanUseCoupon(orderId).data ?: emptyList()
    }
}
