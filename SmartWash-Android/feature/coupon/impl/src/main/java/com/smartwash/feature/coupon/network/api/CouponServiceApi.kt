package com.smartwash.feature.coupon.network.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.coupon.network.vo.coupon.AllCouponsVo
import com.smartwash.feature.coupon.network.vo.coupon.CouponVo
import com.smartwash.feature.coupon.network.vo.coupon.UserCouponVo
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 优惠券网络接口（T7.2 自 app 的 network/api/CouponApi 迁入并改名 CouponServiceApi，
 * 方法集不变——避免与 coupon-api 的 CouponApi 服务契约同名冲突，模式同 order-impl
 * 的 OrderServiceApi / user-impl 的 UserAccountApi）。
 */
interface CouponServiceApi {

    //查询所有优惠券页面
    @GET("/web/auth/coupon/allCoupon")
    @RequireAuthorization
    suspend fun getAllCoupon(): ApiResult<List<CouponVo>>

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

    @RequireAuthorization
    @GET("/web/auth/userCoupon/allCoupons")
    suspend fun getAllCoupons(): ApiResult<AllCouponsVo>
}
