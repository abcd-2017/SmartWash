package com.smartwash.feature.payment.impl.network.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.payment.impl.network.entity.recharge.UserRecharge
import com.smartwash.feature.payment.impl.network.vo.recharge.RechargeRecordVo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RechargeApi {

    @RequireAuthorization
    @POST("/web/auth/recharge/userRecharge")
    suspend fun userRecharge(
        @Body userRecharge: UserRecharge
    ): ApiResult<String>

    @RequireAuthorization
    @GET("/web/auth/recharge/list")
    suspend fun getRechargeRecordList(
        @Query("page") page: Int?,
        @Query("size") size: Int? = 10
    ): ApiResult<PageData<RechargeRecordVo>>
}
