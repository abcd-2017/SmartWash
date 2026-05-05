package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ApiResult
import com.smartwash.network.vo.coupon.CouponVo
import com.smartwash.network.vo.coupon.UserCouponVo
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CouponApi {

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
    @GET("/web/auth/userCoupon/getUserCoupon")
    suspend fun getUserCoupon(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 10,
    ): ApiResult<List<UserCouponVo>>

    @RequireAuthorization
    @GET("/web/auth/userCoupon/available/{orderId}")
    suspend fun getCanUseCoupon(
        @Path("orderId") orderId: Long,
    ): ApiResult<List<UserCouponVo>>
}