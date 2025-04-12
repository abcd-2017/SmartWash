package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ResponseData
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
    suspend fun getAllCoupon(): ResponseData<List<CouponVo>>

    @POST("/web/auth/userCoupon/receiveCoupon/{couponId}")
    @RequireAuthorization
    suspend fun receiveCoupon(
        @Path("couponId") couponId: Long,
    ): ResponseData<Boolean>

    @RequireAuthorization
    @GET("/web/auth/userCoupon/getUserCoupon")
    suspend fun getUserCoupon(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int = 10,
    ): ResponseData<List<UserCouponVo>>

    @RequireAuthorization
    @POST("/web/auth/userCoupon/getCanUseCoupon/{orderId}")
    suspend fun getCanUseCoupon(
        @Path("orderId") orderId: Long,
    ): ResponseData<List<UserCouponVo>>
}