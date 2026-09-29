package com.smartwash.feature.laundry.network.api

import com.smartwash.common.model.ApiResult
import com.smartwash.feature.laundry.network.vo.LaundryItem
import retrofit2.http.GET

interface LaundryItemsApi {

    @GET("/web/laundryItems/all")
    suspend fun getLaundryItems(): ApiResult<List<LaundryItem>>
}
