package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ResponseData
import com.smartwash.network.entity.order.OrderItemCountFrom
import com.smartwash.network.entity.order.OrderListFrom
import com.smartwash.network.entity.order.OrderNextStatus
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.network.vo.order.OrderItemCountVo
import com.smartwash.network.vo.order.OrderVo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface OrderApi {
    /**
     * 创建预约
     */
    @RequireAuthorization
    @POST("/web/auth/orders/reservation")
    suspend fun reservationLaundry(
        @Body reservationLaundry: ReservationLaundry,
    ): ResponseData<Long>

    @RequireAuthorization
    @POST("/web/auth/orders/getOrderInfo/{orderId}")
    suspend fun getOrderInfo(
        @Path("orderId") orderId: Long,
    ): ResponseData<OrderInfo>

    @RequireAuthorization
    @POST("/web/auth/orders/getOrderList")
    suspend fun getOrderList(
        @Body orderList: OrderListFrom,
    ): ResponseData<List<OrderInfo>>

    @RequireAuthorization
    @POST("/web/auth/orders/getOrderItemCount")
    suspend fun getOrderItemCount(
        @Body orderItemCountFrom: OrderItemCountFrom,
    ): ResponseData<OrderItemCountVo>

    @RequireAuthorization
    @POST("/web/auth/orders/shipping")
    suspend fun shippingOrder(
        @Body orderNextStatus: OrderNextStatus,
    ): ResponseData<Boolean>

    @RequireAuthorization
    @POST("/web/auth/orders/pickup")
    suspend fun pickupOrder(
        @Body orderNextStatus: OrderNextStatus,
    ): ResponseData<Boolean>

    @RequireAuthorization
    @GET("/web/auth/orders/getWashingOrder")
    suspend fun getWashingOrder(): ResponseData<List<OrderVo>>

    @RequireAuthorization
    @POST("/web/auth/orders/cancelOrder/{orderId}")
    suspend fun cancelOrder(
        @Path("orderId") orderId: Long,
    ): ResponseData<Boolean>

    @RequireAuthorization
    @POST("/web/auth/orders/calculationOrder/{orderId}/{userCouponId}")
    suspend fun calculationOrder(
        @Path("orderId") orderId: Long,
        @Path("userCouponId") userCouponId: Long,
    ): ResponseData<OrderInfo>
}