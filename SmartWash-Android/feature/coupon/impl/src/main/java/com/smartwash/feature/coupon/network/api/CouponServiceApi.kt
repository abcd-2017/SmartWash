package com.smartwash.feature.coupon.network.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 优惠券网络接口（T7.2 自 app 的 network/api/CouponApi 迁入并改名 CouponServiceApi，
 * 避免与 coupon-api 的 CouponApi 服务契约同名冲突，模式同 order-impl 的
 * OrderServiceApi / user-impl 的 UserAccountApi）。
 *
 * 分页改造：已领取/历史改由 [getUserCoupon] 分页（status=0 可用 / 1 已用或过期）；
 * 原聚合接口 allCoupons 不再消费（可用券走 [getAllCoupon] 的 Room cache-first 链路）。
 */
interface CouponServiceApi {

    /** 可领取优惠券（运营目录，量小，不分页；Room cache-first 降级） */
    @GET("/web/auth/coupon/allCoupon")
    @RequireAuthorization
    suspend fun getAllCoupon(): ApiResult<List<CouponVo>>

    /** 用户优惠券分页：status=0 可用（已领取 tab）/ 1 已使用或过期（历史 tab）；时间倒序 */
    @GET("/web/auth/userCoupon/getUserCoupon")
    @RequireAuthorization
    suspend fun getUserCoupon(
        @Query("status") status: Int,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<UserCouponVo>>

    @POST("/web/auth/userCoupon/receiveCoupon/{couponId}")
    @RequireAuthorization
    suspend fun receiveCoupon(
        @Path("couponId") couponId: Long,
    ): ApiResult<Boolean>

    @RequireAuthorization
    @GET("/web/auth/userCoupon/available/{orderId}")
    suspend fun getCanUseCoupon(
        @Path("orderId") orderId: Long,
    ): ApiResult<List<UserCouponVo>>
}
