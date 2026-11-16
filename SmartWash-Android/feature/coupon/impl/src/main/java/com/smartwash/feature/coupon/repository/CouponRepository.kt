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
import com.smartwash.common.utils.model.PageData
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单组券数据的来源：
 * NETWORK = 本次网络返回；CACHE = 网络失败回退的 Room 缓存（仅可用券有本地表）；FAILED = 失败且无缓存。
 */
enum class CouponSource { NETWORK, CACHE, FAILED }

/** 单组券数据：列表 + 来源标记 */
data class CouponSection<T>(
    val items: List<T>,
    val source: CouponSource,
)

@Singleton
class CouponRepository @Inject constructor(
    private val couponServiceApi: CouponServiceApi,
    private val couponVoDao: CouponVoDao,
    private val appDatabase: AppDatabase,
) {
    /**
     * 可领取券（运营目录，量小，不分页）——Cache-first 策略：先读缓存，再请求网络更新缓存。
     * - 有缓存时：网络失败静默降级返回缓存（[CouponSource.CACHE]）
     * - 无缓存时：网络成功返回数据（[CouponSource.NETWORK]），失败抛异常由上层标 FAILED
     */
    suspend fun getAvailableCoupons(): CouponSection<CouponVo> {
        val cached = couponVoDao.getAll().map { it.toVo() }

        return try {
            // data 为 null 按失败处理，走下方 catch 的缓存降级，避免用空数据覆盖缓存
            val networkData = couponServiceApi.getAllCoupon().data
                ?: run {
                    // code=200 信封但 data 缺失：异常响应空态预警（ResponseInterceptor 不拦此场景）
                    AppLogger.warn(TAG, "getAvailableCoupons 响应 data 为空，按失败处理走缓存降级")
                    throw NetworkException("优惠券数据为空", R.string.error_network_fail)
                }
            // deleteAll + insertAll 包进事务，中途失败不会清空缓存
            appDatabase.withTransaction {
                couponVoDao.deleteAll()
                couponVoDao.insertAll(networkData.map { it.toEntity() })
            }
            CouponSection(networkData, CouponSource.NETWORK)
        } catch (e: CancellationException) {
            // 取消不作为网络错误，直接上抛
            throw e
        } catch (e: Exception) {
            if (cached.isNotEmpty()) {
                // 降级：异常被吞、上层无感知，必须在此留痕（可用券回退 Room 缓存）
                AppLogger.warn(TAG, "getAvailableCoupons 网络失败，降级返回 Room 缓存（${cached.size} 条）", e)
                CouponSection(cached, CouponSource.CACHE)
            } else {
                // 无缓存上抛：上层 catch 会记 error，此处不重复记录
                throw e
            }
        }
    }

    /**
     * 用户优惠券分页（status=0 可用 / 1 已使用或过期，时间倒序）。
     * 已领取/历史无本地缓存表（:common:database 仅 CouponVo 一张券表），不做离线降级。
     * data 为 null 按失败处理上抛 [NetworkException]，由上层转该组错误小态。
     */
    suspend fun getUserCoupon(status: Int, page: Int, size: Int): PageData<UserCouponVo> =
        couponServiceApi.getUserCoupon(status, page, size).data
            ?: run {
                AppLogger.warn(TAG, "getUserCoupon 响应 data 为空，按失败处理上抛（status=$status, page=$page, size=$size）")
                throw NetworkException("用户优惠券数据为空", R.string.error_network_fail)
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
        const val TAG = "CouponRepository"
    }
}
