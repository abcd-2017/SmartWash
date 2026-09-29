package com.smartwash.feature.order.impl.network.api

import com.smartwash.common.model.ApiResult
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.feature.order.impl.network.entity.order.OrderNextStatus
import com.smartwash.feature.order.impl.network.entity.order.ReservationLaundry
import com.smartwash.feature.order.impl.network.vo.order.OrderGroupVo
import com.smartwash.feature.order.impl.network.vo.order.OrderInfoVo
import com.smartwash.feature.order.impl.network.vo.order.OrderItemCountVo
import com.smartwash.feature.order.impl.network.vo.order.OrderVo
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 订单域 Retrofit 接口（原 app 的 network/api/OrderApi，T6.1 迁入并更名 OrderServiceApi——
 * 避免与 order-api 的服务契约 [com.smartwash.feature.order.api.OrderApi] 同名冲突，
 * 模式同 user-impl 的 UserAccountApi）。端点与参数零改动。
 */
interface OrderServiceApi {
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
    ): ApiResult<OrderInfoVo>

    @RequireAuthorization
    @GET("/web/auth/orders")
    suspend fun getOrderList(
        @Query("status") status: String,
        @Query("page") page: Int?,
        @Query("size") size: Int? = 10,
    ): ApiResult<List<OrderInfoVo>>

    @RequireAuthorization
    @GET("/web/auth/orders/summary")
    suspend fun getOrderGroup(
        @Query("size") size: Int? = 10,
    ): ApiResult<Map<String, OrderGroupVo>>

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
    ): ApiResult<OrderInfoVo>
}
