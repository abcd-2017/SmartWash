package com.smartwash.feature.order.api

import androidx.paging.PagingSource
import com.smartwash.feature.order.api.model.OrderBrief
import com.smartwash.feature.order.api.model.OrderInfo
import com.smartwash.feature.order.api.model.OrderItemCount

/**
 * 订单域对外服务契约（api/impl 样板的 api 侧，模式对齐 user-api 的 UserApi）。
 *
 * 消费方（app 壳留守的 detail/index/pickup 页、laundry:impl 的洗衣预约页、
 * payment:impl 的支付/支付成功页、user:impl 的订单计数）一律注入本接口，
 * 不得触碰 :feature:order:impl 的 OrderRepository / Retrofit 实现细节。
 * 实现：OrderApiImpl（T6.1 迁入 order-impl，Hilt `@Binds OrderApi → OrderApiImpl`）。
 *
 * 暴露面按消费方最小化（T6.1 调研结论）：
 * - 订单页自身的 getOrderGroup / getOrderList / cancelOrder 仅 order-impl 内部消费，不在此暴露；
 * - 各方法失败语义对齐原 OrderRepository：数据为空抛
 *   [com.smartwash.common.network.exception.NetworkException]，由调用方并入错误状态。
 */
interface OrderApi {

    /**
     * 预约下单（洗衣套餐 → 订单），返回新订单 id。
     *
     * 语义 = 原 OrderRepository.reservationLaundry：业务失败（data 为 null）返回 -1，
     * 调用方（洗衣预约页）按 -1 判定预约失败。
     */
    suspend fun reservationLaundry(itemsId: Long, totalPrice: Float): Long

    /**
     * 订单详情（含下单时的用户/学校/柜机/套餐快照）。
     *
     * 消费方：订单详情页、支付页（T6.2 后 payment-impl）、寄件取件页。
     */
    suspend fun getOrderInfo(orderId: Long): OrderInfo

    /**
     * 优惠券试算（支付页选券后重算应付价格，返回更新后的订单信息）。
     *
     * 消费方：支付页（T6.2 后 payment-impl）。
     */
    suspend fun calculationOrder(orderId: Long, userCouponId: Long): OrderInfo

    /**
     * 进行中订单（首页"进行中订单"卡片）。
     *
     * 消费方：首页（app 壳）。
     */
    suspend fun getWashingOrder(): List<OrderBrief>

    /**
     * 各状态订单计数（用户中心订单快捷入口）。
     *
     * 消费方：app 壳的 UserCenterOrderCounts seam（T6.3 起由 user-impl 直接消费本接口，
     * seam 删除）。状态参数取 [com.smartwash.feature.order.api.model.ShowOrderStatus]。
     */
    suspend fun getOrderItemCount(
        pendingPaymentStatus: String,
        processingStatus: String,
        pendingPickupStatus: String,
        shippedStatus: String,
    ): OrderItemCount

    /**
     * 寄件（待寄件订单 → 配送中）。
     *
     * 消费方：寄件取件页（app 壳）。返回 false 表示业务失败。
     */
    suspend fun shippingOrder(orderId: Long, pickupCode: String): Boolean

    /**
     * 取件（待取件订单 → 已完成，凭取件码）。
     *
     * 消费方：寄件取件页（app 壳）。返回 false 表示业务失败。
     */
    suspend fun pickupOrder(orderId: Long, pickupCode: String): Boolean

    /**
     * 按状态分页浏览订单的 [PagingSource] 工厂。
     *
     * 消费方：取件页（app 壳，经 pagingFlow 组装 debounce + flatMapLatest + cachedIn）。
     * 每次触发（状态变化）调用一次，返回全新数据源——对齐原 PickupViewModel 内
     * 直接构造 OrderPagingSource 的用法。
     */
    fun orderPagingSource(status: String): PagingSource<Int, OrderInfo>
}
