package com.smartwash.feature.laundry.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 洗衣域类型安全路由（模块内单一事实来源）。
 *
 * 迁移前为 `sealed class LaundryRoute(val text: String)` 字符串常量；现改为
 * sealed interface + @Serializable 路由对象，@SerialName 固定路由串与迁移前
 * 完全一致（"Laundry"/"Service"），保证宿主 NavHost 注册与模块内跳转行为等价；
 * feature 不得反向依赖 app 壳层的 PageConstant。
 *
 * 参数形态：itemId 带默认值 -1L → query 参数（注册形态 "Laundry?itemId={itemId}"，
 * 与迁移前一致）；-1 表示不指定默认套餐。
 *
 * 消费方（均为导航诉求）：
 * - app 壳：NavHost 注册 + 首页服务网格「预约洗衣」跳转；
 * - laundry：域内页面（服务 tab 页 / 洗衣预约页）。
 */
sealed interface LaundryRoute : SmartWashRoute {

    /**
     * 洗衣预约页（首页服务网格「预约洗衣」跳入，选套餐下单）。
     *
     * [itemId] 可选参数：由服务页套餐行点击跳入时指定默认选中的套餐（-1 = 不指定）。
     */
    @Serializable
    @SerialName("Laundry")
    data class Laundry(val itemId: Long = -1L) : LaundryRoute

    /** 服务页（主页底部 tab「服务」，洗衣服务项目列表） */
    @Serializable
    @SerialName("Service")
    data object Service : LaundryRoute
}
