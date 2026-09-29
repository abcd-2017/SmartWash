package com.smartwash.feature.order.api

/**
 * 订单域路由常量（模块内单一事实来源，模式仿 UserRoute / DivRoute）。
 *
 * 路由值对齐 app 壳层 NavHost 的现有注册，保证宿主注册与模块内跳转永不漂移；
 * feature 不得反向依赖 app 壳层的 PageConstant。
 *
 * 归属说明（T6.1 调研 + T8.1 收敛）：订单页在 order-impl；订单详情（OrderDetail）
 * 与寄件取件（PickupDelivery）页面仍留 app 壳（注册归壳层 shellGraph），但路由
 * 常量语义归订单域，统一在本类维护——order-impl / payment-impl 的跨域跳转与壳层
 * 注册同源取值，两 impl 原各自维护的过渡字面量副本已删（T8.1）。
 */
sealed class OrderRoute(val text: String) {
    /** 订单页（跳转时拼 /{itemId} 初始 tab 参数） */
    data object Order : OrderRoute("Order")

    /** 订单详情页（页面留 app 壳；跳转时拼 /{orderId} 参数） */
    data object OrderDetail : OrderRoute("OrderDetail")

    /** 寄件取件页（页面留 app 壳；跳转时拼 /{orderId}/{pickupType} 参数） */
    data object PickupDelivery : OrderRoute("PickupDelivery")

    companion object {
        /** 取件类型参数值（PickupDelivery 路由 {pickupType}；壳层 PickupDeliveryType 委托取值，单一事实来源） */
        const val PICKUP_TYPE_PICKUP = 0

        /** 寄件类型参数值（PickupDelivery 路由 {pickupType}；壳层 PickupDeliveryType 委托取值，单一事实来源） */
        const val PICKUP_TYPE_DELIVERY = 1
    }
}
