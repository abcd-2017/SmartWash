package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ResponseData
import com.smartwash.network.entity.order.OrderItemCountFrom
import com.smartwash.network.entity.order.OrderListFrom
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.network.vo.order.OrderItemCountVo
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface OrderApi {
    /**
     * 创建预约
     */
    @RequireAuthorization
    @POST("/web/auth/orders/reservation")
    suspend fun reservationLaundry(
        @Body reservationLaundry: ReservationLaundry
    ): ResponseData<Long>

    @RequireAuthorization
    @POST("/web/auth/orders/getOrderInfo/{orderId}")
    suspend fun getOrderInfo(
        @Path("orderId") orderId: Long
    ): ResponseData<OrderInfo>

    @RequireAuthorization
    @POST("/web/auth/orders/getOrderList")
    suspend fun getOrderList(
        @Body orderList: OrderListFrom
    ): ResponseData<List<OrderInfo>>

    @RequireAuthorization
    @POST("/web/auth/orders/getOrderItemCount")
    suspend fun getOrderItemCount(
        @Body orderItemCountFrom: OrderItemCountFrom
    ): ResponseData<OrderItemCountVo>
}