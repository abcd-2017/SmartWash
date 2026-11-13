package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 点单订单详情 VO（GET /web/auth/dining/orders/{id} 载荷，与后端 dining/vo/DiningOrderDetailVo
 * 对应，轮询主接口）。
 *
 * version 轮询协议（后端契约）：客户端带 knownVersion 与服务端 version 一致 →
 * unchanged=true 轻量响应（仅 id/version/unchanged，其余字段 null）；不一致或未带 →
 * unchanged=false 全量响应（本单菜单 + 选菜记录 + 参与者 + version）。
 * 本票（发起链路）分享视图只做首次全量拉取（knownVersion 不传）；带 version 轮询与
 * 写冲突重放为点单页后续票消费。
 *
 * 注意：joinToken 仅发起者的全量响应下发（09 号票修补条）——发起者从列表/详情可再次
 * 生成二维码；参与者与轻量响应不下发，端上沿用首次全量拿到的值。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底；
 * 时间字段为后端 ISO 字符串原文（对齐洗衣订单先例）。
 */
@Keep
data class DiningOrderDetailVo(
    /** 订单 ID */
    val id: Long? = null,
    /** 服务端当前 version（轮询协议基准） */
    val version: Long? = null,
    /** 轮询结果标志：true=与已知 version 一致（轻量响应，其余字段 null）；null 按 false 兜底 */
    val unchanged: Boolean? = null,
    /** 订单标题 */
    val title: String? = null,
    /** 订单状态：ONGOING / FINISHED */
    val status: String? = null,
    /** 发起者用户 ID */
    val creatorUserId: Long? = null,
    /** 本单菜单（发起者编排的选菜范围，按加入菜单顺序；端上按分类组织左侧分类栏） */
    val menu: List<MenuRecipe>? = null,
    /** 全部选菜记录（共享购物车明细，按勾选顺序） */
    val items: List<OrderItem>? = null,
    /** 参与者列表 */
    val members: List<OrderMember>? = null,
    /** 结束时间（进行中为 null，ISO 字符串原文） */
    val finishedAt: String? = null,
    /** 购买清单快照 JSON 文本（结束时写入，进行中为 null；结束结果页为后续票消费） */
    val shoppingList: String? = null,
    /**
     * 加入凭证（join_token）：仅发起者的全量响应下发，供发起者从列表/详情再次生成二维码；
     * 参与者响应与轻量（unchanged=true）响应为 null（端上沿用首次全量拿到的值）。
     */
    val joinToken: String? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeVersion: Long get() = version ?: 0L
    val safeUnchanged: Boolean get() = unchanged == true
    val safeTitle: String get() = title.orEmpty()
    val safeStatus: String get() = status.orEmpty()
    val safeCreatorUserId: Long get() = creatorUserId ?: 0L
    val safeMenu: List<MenuRecipe> get() = menu.orEmpty()
    val safeItems: List<OrderItem> get() = items.orEmpty()
    val safeMembers: List<OrderMember> get() = members.orEmpty()
    val safeJoinToken: String get() = joinToken.orEmpty()
    /** 结束时间展示原文（进行中为空串；ISO 原文，格式化由映射层处理） */
    val safeFinishedAt: String get() = finishedAt.orEmpty()
    /** 购买清单快照 JSON 文本（FINISHED 后写入；进行中/缺失为空串） */
    val safeShoppingList: String get() = shoppingList.orEmpty()

    /**
     * 本单菜单条目：菜谱 ID / 名称 / 分类（端上按分类组织左侧分类栏）。
     */
    @Keep
    data class MenuRecipe(
        /** 菜谱 ID（点单页选菜操作参数） */
        val recipeId: Long? = null,
        /** 菜名 */
        val name: String? = null,
        /** 分类 ID，null=未分类 */
        val categoryId: Long? = null,
    ) {
        val safeRecipeId: Long get() = recipeId ?: 0L
        val safeName: String get() = name.orEmpty()
    }

    /** 选菜记录条目：谁选了哪道菜 + 备注 */
    @Keep
    data class OrderItem(
        val recipeId: Long? = null,
        /** 选菜人用户 ID */
        val userId: Long? = null,
        /** 个人备注（如 不要辣），可空 */
        val note: String? = null,
    ) {
        val safeRecipeId: Long get() = recipeId ?: 0L
        val safeUserId: Long get() = userId ?: 0L
        val safeNote: String get() = note.orEmpty()
    }

    /** 参与者条目 */
    @Keep
    data class OrderMember(
        val userId: Long? = null,
        /** 是否发起者 */
        val isCreator: Boolean? = null,
        /** 脱敏手机号（如 139****9000），端上作选菜人标识 */
        val phoneMasked: String? = null,
    ) {
        val safeUserId: Long get() = userId ?: 0L
        val safeIsCreator: Boolean get() = isCreator == true
        val safePhoneMasked: String get() = phoneMasked.orEmpty()
    }
}
