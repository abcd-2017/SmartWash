package com.smartwash.feature.dining.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 家庭点单域类型安全路由（模块内单一事实来源，模式对齐 NoticeRoute/CouponRoute）。
 *
 * 页面清单：菜谱三页（04 号票菜谱竖切：菜谱库/详情/新增）+ 订单四页（07 号票发起链路：
 * 订单列表 → 创建订单 → 分享视图；09 号票点单页 OrderDetail）。深链 host "dining" 落点为域首页订单列表
 * （[OrderList]，见 impl DiningDeepLinkModule——04 号票曾占位指向菜谱库，订单列表落位后重指向）。
 *
 * 消费方：
 * - app 壳：MainActivity NavHost 经 [com.smartwash.feature.dining.ui.diningGraph] 注册；
 * - dining-impl：深链 host "dining" 贡献者落点；
 * - 后续订单域票面：创建订单页按分类勾选菜品时跨域消费（impl → dining-api，铁律 2）。
 */
sealed interface DiningRoute : SmartWashRoute {

    /** 菜谱库页（分类筛选 + 上架列表；创建订单页按分类勾选菜品复用其读取面） */
    @Serializable
    @SerialName("DiningRecipeLibrary")
    data object RecipeLibrary : DiningRoute

    /** 菜谱详情页（食材名列表 + 步骤） */
    @Serializable
    @SerialName("DiningRecipeDetail/{recipeId}")
    data class RecipeDetail(val recipeId: Long) : DiningRoute

    /** 新增菜谱页（表单 + 封面上传，成功返回菜谱库刷新） */
    @Serializable
    @SerialName("DiningRecipeCreate")
    data object RecipeCreate : DiningRoute

    /** 订单列表页（域首页；「我发起的/我参与的」分区 + FAB 创建；深链 host "dining" 落点） */
    @Serializable
    @SerialName("DiningOrderList")
    data object OrderList : DiningRoute

    /**
     * 创建订单页（标题 + 按分类分组勾选公共库菜品，提交成功进分享视图）。
     *
     * 复用历史菜单入口（13 号票）：[fromOrderId] 非空时进入复用模式——本单菜单按该历史订单复制
     * （后端 fromOrderId 契约），页面只填标题、菜品区只读并回显源单菜单；[sourceTitle] 为入口顺带
     * 下发的源单标题（首帧提示文案，缺失时经详情接口自取）。二者为 query 参数（SavedStateHandle 同源读取）。
     */
    @Serializable
    @SerialName("DiningOrderCreate")
    data class OrderCreate(
        val fromOrderId: Long? = null,
        val sourceTitle: String = "",
    ) : DiningRoute

    /**
     * 扫码加入页（08 号票）：CameraX 预览 + ML Kit 识别，入口在订单列表页右上角。
     * 识别 join URL → 拉 brief → 确认 → join；加入成功后按已注册路由进 [OrderDetail]
     * （09 未落地时回退 [OrderList]）。
     */
    @Serializable
    @SerialName("DiningScan")
    data object Scan : DiningRoute

    /**
     * 点单页（美团式左分类右菜品 + 底部已选栏 + 参与者区 + 发起者结束入口，09 号票）。
     *
     * 从订单列表卡进入；页面自身经详情接口全量拉取（[joinToken] 仅发起者可见，供本页
     * 顶部分享入口带往 [OrderShare]）。
     */
    @Serializable
    @SerialName("DiningOrderDetail/{orderId}")
    data class OrderDetail(val orderId: Long) : DiningRoute

    /**
     * 点单加入入口页（12 号票）：站内信 dining 深链 `smartwash://dining/order/{orderId}?t={token}` 的落点。
     *
     * 成员经 brief 判重后直达 [OrderDetail]；非成员展示标题预览 + 「加入」按钮，
     * 确认加入后再进详情。[token] 为加入凭证（与后端 buildOrderDeepLink 同口径），
     * 缺失/无效时页面按「加入凭证无效或已重置」提示。
     */
    @Serializable
    @SerialName("DiningOrderJoin/{orderId}/{token}")
    data class OrderJoin(val orderId: Long, val token: String) : DiningRoute

    /**
     * 订单分享视图（订单基本信息 + 加入二维码）。
     * [title] 由入口顺带下发避免首帧空窗（列表页/创建页/点单页均已持有）；[joinToken] 为
     * 可选入口值（创建响应 / 点单页详情，仅发起者可见）——进来才有值即展示二维码。
     * 入口未携带（如旧入口直开）时分享页经详情接口自取：发起者详情全量响应会回传 token
     * （09 号票修补条），参与者仍降级提示「凭证仅发起者可得」。
     */
    @Serializable
    @SerialName("DiningOrderShare/{orderId}")
    data class OrderShare(
        val orderId: Long,
        val title: String = "",
        val joinToken: String? = null,
    ) : DiningRoute

    /**
     * 购买清单结果页（结束点单后进入；FINISHED 只读态点单页亦可经「查看购买清单」进入）。
     *
     * 页面经详情接口拉取 shopping_list 快照与参与者（选菜人标识），渲染「按食材买菜 / 按菜备菜」
     * 双视图；[orderId] 为唯一入口参数（标题/快照不随路由传递，避免长 JSON 进返回栈）。
     */
    @Serializable
    @SerialName("DiningShoppingList/{orderId}")
    data class ShoppingList(val orderId: Long) : DiningRoute
}
