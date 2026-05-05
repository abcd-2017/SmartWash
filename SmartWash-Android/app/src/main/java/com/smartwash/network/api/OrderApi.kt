package com.smartwash.network.api

import com.smartwash.network.annotation.RequireAuthorization
import com.smartwash.network.entity.ApiResult
import com.smartwash.network.vo.order.OrderItemCountVo
import com.smartwash.network.entity.order.OrderNextStatus
import com.smartwash.network.entity.order.ReservationLaundry
import com.smartwash.network.vo.order.OrderInfo
import com.smartwash.network.vo.order.OrderVo
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OrderApi {
    /**
     * 创建预约
     */
    @RequireAuthorization
    @POST("/web/auth/orders/reservation")
    suspend fun reservationLaundry(
        @Body reservationLaundry: ReservationLaundry,
    ): ApiResult<Long>

    @RequireAuthorization
    @GET("/web/auth/orders/{orderId}")
    suspend fun getOrderInfo(
        @Path("orderId") orderId: Long,
    ): ApiResult<OrderInfo>

    @RequireAuthorization
    @GET("/web/auth/orders")
    suspend fun getOrderList(
        @Query("status") status: String,
        @Query("page") page: Int?,
        @Query("size") size: Int? = 10,
    ): ApiResult<List<OrderInfo>>

    @RequireAuthorization
    @GET("/web/auth/orders/itemCount")
    suspend fun getOrderItemCount(
        @Query("pendingPaymentStatus") pendingPaymentStatus: String,
        @Query("processingStatus") processingStatus: String,
        @Query("pendingPickupStatus") pendingPickupStatus: String,
        @Query("shippedStatus") shippedStatus: String,
    ): ApiResult<OrderItemCountVo>

    @RequireAuthorization
    @POST("/web/auth/orders/shipping")
    suspend fun shippingOrder(
        @Body orderNextStatus: OrderNextStatus,
    ): ApiResult<Boolean>

    @RequireAuthorization
    @POST("/web/auth/orders/pickup")
    suspend fun pickupOrder(
        @Body orderNextStatus: OrderNextStatus,
    ): ApiResult<Boolean>

    @RequireAuthorization
    @GET("/web/auth/orders/getWashingOrder")
    suspend fun getWashingOrder(): ApiResult<List<OrderVo>>

    @RequireAuthorization
    @DELETE("/web/auth/orders/{orderId}")
    suspend fun cancelOrder(
        @Path("orderId") orderId: Long,
    ): ApiResult<Boolean>

    @RequireAuthorization
    @GET("/web/auth/orders/{orderId}/calculation")
    suspend fun calculationOrder(
        @Path("orderId") orderId: Long,
        @Query("userCouponId") userCouponId: Long,
    ): ApiResult<OrderInfo>
}
