package com.smartwash.network.api

import com.smartwash.network.entity.ResponseData
import com.smartwash.network.vo.school.SchoolName
import retrofit2.http.GET
import retrofit2.http.Query

interface SchoolApi {
    /**
     * 获取所有学校名字
     */
    @GET("/web/schools/allName")
    suspend fun getAllSchool(
        @Query("schoolName") schoolName: String
    ): ResponseData<List<SchoolName>>
}