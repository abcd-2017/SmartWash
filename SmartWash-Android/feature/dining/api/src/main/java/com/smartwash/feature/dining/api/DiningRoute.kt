package com.smartwash.feature.dining.api

import com.smartwash.common.utils.navigation.SmartWashRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 吃啥域类型安全路由（模块内单一事实来源，模式对齐 NoticeRoute/CouponRoute）。
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
    @SerialName("DiningRecipeDetail")
    data class RecipeDetail(val recipeId: Long) : DiningRoute

    /** 新增菜谱页（表单 + 封面上传，成功返回菜谱库刷新） */
    @Serializable
    @SerialName("DiningRecipeCreate")
    data object RecipeCreate : DiningRoute

    /**
     * 我的收藏页（契约 §三.4）：favorites 分页链路 + 复用菜谱库双列封面卡；
     * 入口在订单列表页分区。
     */
    @Serializable
    @SerialName("DiningRecipeFavorites")
    data object RecipeFavorites : DiningRoute

    /** 订单列表页（域首页；「我发起的/我参与的」分区 + FAB 创建；深链 host "dining" 落点） */
    @Serializable
    @SerialName("DiningOrderList")
    data object OrderList : DiningRoute

    /**
     * 创建订单页（标题 + 按分类分组勾选公共库菜品，提交成功进分享视图）。
     *
     * 草稿协同点单：常规进入为「惰性创建」——首次加菜或点邀请时才落 DRAFT 草稿；
     * [orderId] 非空时进入「草稿编辑模式」——加载既有 DRAFT（标题/菜单/version/凭证），
     * 加菜/移除走 dishes 接口、CTA 为 publish 定稿（详情页 DRAFT 菜单区「添加菜品」入口跳转）。
     *
     * 复用历史菜单入口（13 号票）：[fromOrderId] 非空时进入复用模式——本单菜单按该历史订单复制
     * （后端 fromOrderId 契约），页面只填标题、菜品区只读并回显源单菜单；[sourceTitle] 为入口顺带
     * 下发的源单标题（首帧提示文案，缺失时经详情接口自取）。均为 query 参数（SavedStateHandle 同源读取）。
     */
    @Serializable
    @SerialName("DiningOrderCreate")
    data class OrderCreate(
        val fromOrderId: Long? = null,
        val sourceTitle: String = "",
        val orderId: Long? = null,
    ) : DiningRoute

    /**
     * 点单加入入口页（12 号票）：站内信 dining 深链 `smartwash://dining/order/{orderId}?t={token}` 的落点。
     *
     * 成员经 brief 判重后直达 [OrderCreate]（编辑态，菜单即已选清单、全员共同编辑）；
     * 非成员展示标题预览 + 「加入」按钮，确认加入后再进编辑页。[token] 为加入凭证
     * （与后端 buildOrderDeepLink 同口径），缺失/无效时页面按「加入凭证无效或已重置」提示。
     */
    @Serializable
    @SerialName("DiningOrderJoin")
    data class OrderJoin(val orderId: Long, val token: String) : DiningRoute

    /**
     * 邀请 / 分享视图（订单基本信息 + 加入二维码）。
     *
     * 邀请与分享合一：创建页「邀请」（DRAFT 编辑中）与列表/详情入口（进行中）都落本页，
     * 标题按状态显示「邀请加入」/「订单分享」。[title] 由入口顺带下发避免首帧空窗；
     * [joinToken] 为可选入口值（创建响应 / 详情，仅发起者可见）——进来才有值即展示二维码。
     * 入口未携带时经详情接口自取：发起者详情全量响应会回传 token，参与者降级提示。
     */
    @Serializable
    @SerialName("DiningOrderShare")
    data class OrderShare(
        val orderId: Long,
        val title: String = "",
        val joinToken: String? = null,
    ) : DiningRoute

    /**
     * 购买清单结果页（发起者「提交」结束后进入；订单列表 FINISHED 卡直达）。
     *
     * 页面经详情接口拉取 shopping_list 快照，渲染「按食材买菜 / 按菜备菜」双视图；
     * [orderId] 为唯一入口参数（标题/快照不随路由传递，避免长 JSON 进返回栈）。
     */
    @Serializable
    @SerialName("DiningShoppingList")
    data class ShoppingList(val orderId: Long) : DiningRoute
}
