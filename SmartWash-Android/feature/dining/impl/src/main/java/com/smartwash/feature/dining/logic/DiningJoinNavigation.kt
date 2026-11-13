package com.smartwash.feature.dining.logic

/**
 * 扫码加入成功后的导航归一（纯 Kotlin，JVM 单测锚点）。
 *
 * 08 号票与 09 号票（点单页 OrderDetail）并行开发：本票不引用 [DiningRoute.OrderDetail]
 * 符号（避免与 T9 路由定义冲突、也保证本票独立可编译），改为按「已注册路由串」探测——
 * 09 落地并注册后按该路由进入点单页；未落地时回退订单列表 + 提示（端到端闭环由 T13 验收）。
 *
 * 路由串约定与 dining 域命名一致（`DiningOrderDetail/{orderId}`）；[resolveOrderDetailRoute]
 * 容忍参数名差异，只要求 `Dining` 前缀 + `OrderDetail` + 路径参数占位符。
 */
object DiningJoinNavigation {

    /** 点单页路由特征：dining 域（Dining 前缀）+ OrderDetail + 路径参数占位符 */
    private val ORDER_DETAIL_ROUTE = Regex("^Dining.*OrderDetail.*\\{([^}]+)}")

    /** 通用路径参数占位符（取首个，替换为订单 ID） */
    private val ROUTE_ARG_PLACEHOLDER = Regex("\\{[^}]+}")

    /**
     * 从已注册路由串解析点单页导航目标。
     *
     * @param registeredRoutes 当前 NavGraph 已注册的全部路由串
     * @param orderId 订单 ID
     * @return 可直接 `navController.navigate` 的路由串；未注册点单页时返回 null（调用方回退订单列表）
     */
    fun resolveOrderDetailRoute(registeredRoutes: Collection<String>, orderId: Long): String? {
        val route = registeredRoutes.firstOrNull { ORDER_DETAIL_ROUTE.containsMatchIn(it) } ?: return null
        return route.replaceFirst(ROUTE_ARG_PLACEHOLDER, orderId.toString())
    }
}
