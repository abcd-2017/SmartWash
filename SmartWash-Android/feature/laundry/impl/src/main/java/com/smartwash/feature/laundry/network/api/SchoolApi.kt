package com.smartwash.feature.laundry.network.api

import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.laundry.network.vo.SchoolName
import retrofit2.http.GET
import retrofit2.http.Query

interface SchoolApi {
    /**
     * 获取所有学校名字
     */
    @GET("/web/schools/allName")
    suspend fun getAllSchool(
        @Query("schoolName") schoolName: String
    ): ApiResult<List<SchoolName>>
}
