package com.smartwash.feature.coupon.repository

import androidx.room.withTransaction
import com.smartwash.common.database.AppDatabase
import com.smartwash.common.database.dao.CouponVoDao
import com.smartwash.feature.coupon.network.api.CouponServiceApi
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.coupon.network.vo.coupon.AllCouponsVo
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.feature.coupon.R
import com.smartwash.common.utils.AppLogger
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CouponRepository @Inject constructor(
    private val couponServiceApi: CouponServiceApi,
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
            val networkData = couponServiceApi.getAllCoupon().data
                ?: run {
                    // code=200 信封但 data 缺失：异常响应空态预警（ResponseInterceptor 不拦此场景）
                    AppLogger.warn(TAG, "getAllCoupon 响应 data 为空，按失败处理走缓存降级")
                    throw NetworkException("优惠券数据为空", R.string.error_network_fail)
                }
            // deleteAll + insertAll 包进事务，中途失败不会清空缓存
            appDatabase.withTransaction {
                couponVoDao.deleteAll()
                couponVoDao.insertAll(networkData.map { it.toEntity() })
            }
            networkData
        } catch (e: CancellationException) {
            // 取消不作为网络错误，直接上抛
            throw e
        } catch (e: Exception) {
            if (cached.isNotEmpty()) {
                // 降级：异常被吞、上层无感知，必须在此留痕（可用券回退 Room 缓存）
                AppLogger.warn(TAG, "getAllCoupon 网络失败，降级返回 Room 缓存（${cached.size} 条）", e)
                cached
            } else {
                // 无缓存上抛：上层 catch 会记 error，此处不重复记录
                throw e
            }
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
            val networkData = couponServiceApi.getAllCoupons().data
                ?: run {
                    // code=200 信封但 data 缺失：异常响应空态预警（ResponseInterceptor 不拦此场景）
                    AppLogger.warn(TAG, "getAllCoupons 响应 data 为空，按失败处理走缓存降级")
                    throw NetworkException("优惠券数据为空", R.string.error_network_fail)
                }
            // 仅可用券有本地缓存表；deleteAll + insertAll 包进事务，中途失败不会清空缓存
            appDatabase.withTransaction {
                couponVoDao.deleteAll()
                couponVoDao.insertAll(networkData.available.map { it.toEntity() })
            }
            networkData
        } catch (e: CancellationException) {
            // 取消不作为网络错误，直接上抛
            throw e
        } catch (e: Exception) {
            if (cachedAvailable.isNotEmpty()) {
                // 降级：异常被吞、上层无感知，必须在此留痕（可用券回退 Room 缓存，已领/历史无缓存置空）
                AppLogger.warn(
                    TAG,
                    "getAllCoupons 网络失败，可用券降级为 Room 缓存（${cachedAvailable.size} 条，已领/历史置空）",
                    e,
                )
                AllCouponsVo(
                    available = cachedAvailable,
                    claimed = emptyList(),
                    historical = emptyList(),
                )
            } else {
                // 无缓存上抛：上层 catch 会记 error，此处不重复记录
                throw e
            }
        }
    }

    suspend fun receiveCoupon(couponId: Long): Boolean {
        val received = couponServiceApi.receiveCoupon(couponId).data == true
        if (!received) {
            // data=false = 业务拒绝/幂等命中（code=200 信封但领取未成功），warn 留痕带业务键
            AppLogger.warn(TAG, "领取优惠券未成功（业务拒绝或幂等命中），couponId=$couponId")
        }
        return received
    }

    suspend fun getCanUseCoupon(orderId: Long): List<UserCouponVo> {
        // data 为 null 按失败处理，让 UI 能区分"无可用优惠券"与"请求失败"
        return couponServiceApi.getCanUseCoupon(orderId).data
            ?: run {
                // 异常响应空态预警；上抛后由消费方（payment-impl 支付页）catch 记 error
                AppLogger.warn(TAG, "getCanUseCoupon 响应 data 为空，按失败处理上抛（orderId=$orderId）")
                throw NetworkException("可用优惠券数据为空", R.string.error_network_fail)
            }
    }

    private companion object {
        /** 日志 TAG（"SmartWash/" 前缀由 AppLogger 统一拼接） */
        const val TAG = "CouponRepository"
    }
}
