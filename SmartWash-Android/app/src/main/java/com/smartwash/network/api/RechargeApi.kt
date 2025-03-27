package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ResponseData
import com.smartwash.network.entity.recharge.UserRecharge
import retrofit2.http.Body
import retrofit2.http.POST

interface RechargeApi {

    @RequireAuthorization
    @POST("/web/auth/recharge/userRecharge")
    suspend fun userRecharge(
        @Body userRecharge: UserRecharge
    ): ResponseData<String>
}