package com.smartwash.feature.payment.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 支付域类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class PaymentRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"Payment"/"PaySuccess"/...），保证宿主 NavHost 注册与模块内跳转
 * 行为等价；feature 不得反向依赖 app 壳层的 PageConstant。
 *
 * 参数形态（orderId 无默认值 → path，与迁移前注册形态一致）。
 *
 * 消费方（均为导航诉求，无数据依赖）：
 * - order-impl：订单页「去支付」跳 [Payment]；
 * - user-impl：用户中心「去充值」跳 [Recharge]；
 * - payment-impl：域内页面互跳（支付页→支付成功/充值页、充值页→充值记录页）；
 * - app 壳：NavHost 注册 + 首页/洗衣预约页跳转。
 */
sealed interface PaymentRoute : SmartWashRoute {

    /** 支付页（orderId 为 path 参数） */
    @Serializable
    @SerialName("Payment")
    data class Payment(val orderId: Long) : PaymentRoute

    /** 支付成功页（orderId 为 path 参数） */
    @Serializable
    @SerialName("PaySuccess")
    data class PaySuccess(val orderId: Long) : PaymentRoute

    /** 充值页 */
    @Serializable
    @SerialName("Recharge")
    data object Recharge : PaymentRoute

    /** 充值记录页 */
    @Serializable
    @SerialName("RechargeRecord")
    data object RechargeRecord : PaymentRoute
}
