package com.smartwash.network.api

import com.smartwash.network.entity.ResponseData
import com.smartwash.network.vo.laundry.LaundryItem
import retrofit2.http.GET

interface LaundryItemsApi {

    @GET("web/laundryItems/all")
    suspend fun getLaundryItems(): ResponseData<List<LaundryItem>>
}