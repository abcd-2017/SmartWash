package com.smartwash.feature.deeplink

/**
 * 深链路由字符串集中登记。
 *
 * 本模块零 feature 依赖（遵循「feature 间仅 impl → 他人 api」铁律与 toolbox/update/
 * divination 单模块零 feature 依赖先例），各域路由串为**字面量副本**；单一事实来源仍是
 * 各域 `*Route` 密封类，注释标明出处，**改动须两处同步**（模块 AGENTS.md 有对照表）。
 */
internal object DeepLinkRoutes {

    /** = UserRoute.Login.text（:feature:user:api） */
    const val LOGIN = "Login"

    /** = UserRoute.Register.text（:feature:user:api） */
    const val REGISTER = "Register"

    /** = UserRoute.Setting.text（:feature:user:api） */
    const val SETTING = "Setting"

    /** = PaymentRoute.Recharge.text（:feature:payment:api） */
    const val RECHARGE = "Recharge"

    /** = PaymentRoute.RechargeRecord.text（:feature:payment:api） */
    const val RECHARGE_RECORD = "RechargeRecord"

    /** = CouponRoute.Coupon.text（:feature:coupon:api） */
    const val COUPON = "Coupon"

    /** = OrderRoute.Order.text（:feature:order:api）；注册形态 "Order/{itemId}"，itemId 为初始 tab */
    const val ORDER = "Order"

    /** 订单初始 tab 上界 = ShowOrderStatus 5 档 - 1（不引 order 域，避免 feature 依赖） */
    const val ORDER_TAB_MAX = 4

    /** = PageConstant.Pickup.text（app 壳层留守页） */
    const val PICKUP = "Pickup"

    /** = LaundryRoute.Laundry.text（:feature:laundry:api） */
    const val LAUNDRY = "Laundry"

    /** = DivRoute.Home.text（:feature:divination） */
    const val DIVINATION = "DivHome"

    /** = DivRoute.History.text（:feature:divination） */
    const val DIVINATION_HISTORY = "DivHistory"

    /** = PageConstant.AiWork.text（app 壳层留守页） */
    const val AI_WORK = "AiWork"

    /** = ToolboxRoute.List.text（:feature:toolbox） */
    const val TOOLBOX_SHORT_CODE = "ToolboxShortCodeList"

    /** = ToolboxRoute.Detail.text（:feature:toolbox）；t/{code} 分支专用，仅 code 必填，其余字段走注册缺省值 */
    const val TOOLBOX_DETAIL = "ToolboxShortCodeDetail"
}

/**
 * 深链 host 清单（Parser 内建 web/t 之外的可导航 host）。
 * 工作台兜底列表等端上构造深链时统一从这里取值，避免 host 字面量散落。
 */
object DeepLinkHosts {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val SETTING = "setting"
    const val RECHARGE = "recharge"
    const val RECHARGE_RECORD = "recharge-record"
    const val COUPON = "coupon"
    const val ORDER = "order"
    const val PICKUP = "pickup"
    const val LAUNDRY = "laundry"
    const val DIVINATION = "divination"
    const val DIVINATION_HISTORY = "divination-history"
    const val AI_WORK = "ai-work"
    const val TOOLBOX_SHORT_CODE = "toolbox-short-code"
}

/**
 * 本地路由注册表：host → 由 query 参数构造导航路由字符串。
 * Parser 对未注册 host 返回 null，最终降级 [DeepLinkAction.Unknown]。
 *
 * 纯 Kotlin 接口（无 Android 依赖），JVM 单测可用 Fake 替换。
 */
interface DeepLinkRegistry {

    /**
     * @param host 已小写的 scheme host
     * @param params query 参数（value 已百分号解码）
     * @return 可直接 navigate 的路由字符串；未注册返回 null
     */
    fun routeFor(host: String, params: Map<String, String>): String?
}

/** 首批注册的默认注册表（host 清单见模块 AGENTS.md 对照表） */
class DefaultDeepLinkRegistry : DeepLinkRegistry {

    override fun routeFor(host: String, params: Map<String, String>): String? = when (host) {
        DeepLinkHosts.LOGIN -> DeepLinkRoutes.LOGIN
        DeepLinkHosts.REGISTER -> DeepLinkRoutes.REGISTER
        DeepLinkHosts.SETTING -> DeepLinkRoutes.SETTING
        DeepLinkHosts.RECHARGE -> DeepLinkRoutes.RECHARGE
        DeepLinkHosts.RECHARGE_RECORD -> DeepLinkRoutes.RECHARGE_RECORD
        DeepLinkHosts.COUPON -> DeepLinkRoutes.COUPON
        // query tab 为订单页初始 tab，缺省 0；防御夹取 0-4（ShowOrderStatus 五档）
        DeepLinkHosts.ORDER ->
            "${DeepLinkRoutes.ORDER}/${(params["tab"]?.trim()?.toIntOrNull() ?: 0).coerceIn(0, DeepLinkRoutes.ORDER_TAB_MAX)}"
        DeepLinkHosts.PICKUP -> DeepLinkRoutes.PICKUP
        DeepLinkHosts.LAUNDRY -> DeepLinkRoutes.LAUNDRY
        DeepLinkHosts.DIVINATION -> DeepLinkRoutes.DIVINATION
        DeepLinkHosts.DIVINATION_HISTORY -> DeepLinkRoutes.DIVINATION_HISTORY
        DeepLinkHosts.AI_WORK -> DeepLinkRoutes.AI_WORK
        DeepLinkHosts.TOOLBOX_SHORT_CODE -> DeepLinkRoutes.TOOLBOX_SHORT_CODE

        // web / t 由 DeepLinkParser 内建分支优先处理，不经注册表（解析顺序保证走不到这里）
        else -> null
    }
}
