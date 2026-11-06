package com.smartwash.feature.coupon.repository

import androidx.room.withTransaction
import com.smartwash.common.database.AppDatabase
import com.smartwash.common.database.dao.CouponVoDao
import com.smartwash.feature.coupon.network.api.CouponServiceApi
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import com.smartwash.feature.coupon.R
import com.smartwash.common.utils.AppLogger
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单组券数据的来源（回归 2026-11-06 A5）：降级不再伪装成空态。
 * NETWORK = 本次网络返回；CACHE = 网络失败回退的 Room 缓存（离线兜底）；FAILED = 失败且无缓存。
 */
enum class CouponSource { NETWORK, CACHE, FAILED }

/** 单组券数据：列表 + 来源标记 */
data class CouponSection<T>(
    val items: List<T>,
    val source: CouponSource,
)

/** 优惠券页三组聚合结果（可领取 / 已领取 / 历史），各组来源独立标记 */
data class CouponPageData(
    val available: CouponSection<CouponVo>,
    val claimed: CouponSection<UserCouponVo>,
    val historical: CouponSection<UserCouponVo>,
)

/**
 * 缓存降级归并（A5 锚点单测）：网络失败时三组「有啥显啥」——
 * 有缓存的组回退缓存（CACHE），无缓存的组标 FAILED（由 UI 显示错误/重试小态，而非「暂无」空态）。
 * internal 供 JVM 单测。
 */
internal fun buildDegradedCouponPageData(cachedAvailable: List<CouponVo>): CouponPageData = CouponPageData(
    available = if (cachedAvailable.isNotEmpty()) {
        CouponSection(cachedAvailable, CouponSource.CACHE)
    } else {
        CouponSection(emptyList(), CouponSource.FAILED)
    },
    claimed = CouponSection(emptyList(), CouponSource.FAILED),
    historical = CouponSection(emptyList(), CouponSource.FAILED),
)

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
     *
     * Cache-first 策略（回归 2026-11-06 A5 重构）：网络失败时**三组数据统一按缓存降级**
     * ——有缓存的组回退缓存（[CouponSource.CACHE]），无缓存的组标 [CouponSource.FAILED]，
     * 不再把降级伪装成空列表；仅当三组全部拿不到数据（缓存也空）时抛异常，由上层转整页 Error 态。
     */
    suspend fun getAllCoupons(): CouponPageData {
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
            CouponPageData(
                available = CouponSection(networkData.available, CouponSource.NETWORK),
                claimed = CouponSection(networkData.claimed, CouponSource.NETWORK),
                historical = CouponSection(networkData.historical, CouponSource.NETWORK),
            )
        } catch (e: CancellationException) {
            // 取消不作为网络错误，直接上抛
            throw e
        } catch (e: Exception) {
            if (cachedAvailable.isNotEmpty()) {
                // 降级：异常被吞、上层无感知，必须在此留痕（有啥显啥：可用券回退 Room 缓存，
                // 已领/历史无本地缓存表标 FAILED 由 UI 出错误小态）
                AppLogger.warn(
                    TAG,
                    "getAllCoupons 网络失败，可用券降级为 Room 缓存（${cachedAvailable.size} 条），已领/历史标 FAILED",
                    e,
                )
                buildDegradedCouponPageData(cachedAvailable)
            } else {
                // 无任何缓存上抛：上层 catch 会记 error，此处不重复记录
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
