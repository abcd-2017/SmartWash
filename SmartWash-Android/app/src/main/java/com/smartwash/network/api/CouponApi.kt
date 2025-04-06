package com.smartwash.network.api

import com.smartwash.network.entity.ResponseData
import com.smartwash.network.vo.coupon.CouponVo
import retrofit2.http.GET

interface CouponApi {

    @GET("/web/coupon/allCoupon")
    suspend fun getAllCoupon(): ResponseData<List<CouponVo>>
}