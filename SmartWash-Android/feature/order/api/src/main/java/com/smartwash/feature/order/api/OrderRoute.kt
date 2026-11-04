package com.smartwash.feature.order.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 订单域类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class OrderRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"Order"/"OrderDetail"/"PickupDelivery"），保证宿主注册与模块内
 * 跳转行为等价；feature 不得反向依赖 app 壳层的 PageConstant。
 *
 * 参数形态（无默认值 → path，与迁移前注册形态一致）：
 * - [Order]："Order/{itemId}"（初始 tab）
 * - [OrderDetail]："OrderDetail/{orderId}"（页面留 app 壳，注册归壳层 shellGraph）
 * - [PickupDelivery]："PickupDelivery/{orderId}/{pickupType}"（页面留 app 壳）
 *
 * 归属说明（T6.1 调研 + T8.1 收敛）：订单页在 order-impl；订单详情与寄件取件
 * 页面仍留 app 壳，但路由常量语义归订单域，统一在本类维护。
 */
sealed interface OrderRoute : SmartWashRoute {

    /** 订单页（itemId = 初始 tab，无默认值 → path 参数） */
    @Serializable
    @SerialName("Order")
    data class Order(val itemId: Int) : OrderRoute

    /** 订单详情页（页面留 app 壳） */
    @Serializable
    @SerialName("OrderDetail")
    data class OrderDetail(val orderId: Long) : OrderRoute

    /** 寄件取件页（页面留 app 壳；orderId + pickupType 均为 path 参数） */
    @Serializable
    @SerialName("PickupDelivery")
    data class PickupDelivery(val orderId: Long, val pickupType: Int) : OrderRoute

    companion object {
        /** 取件类型参数值（PickupDelivery 的 pickupType；壳层 PickupDeliveryType 委托取值，单一事实来源） */
        const val PICKUP_TYPE_PICKUP = 0

        /** 寄件类型参数值（PickupDelivery 的 pickupType；壳层 PickupDeliveryType 委托取值，单一事实来源） */
        const val PICKUP_TYPE_DELIVERY = 1
    }
}
