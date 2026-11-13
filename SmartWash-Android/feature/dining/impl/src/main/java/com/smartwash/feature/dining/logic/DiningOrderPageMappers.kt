package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderStatus

/** 点单页参与者模型（参与者区渲染单元） */
data class DiningParticipant(
    /** 用户 ID */
    val userId: Long,
    /** 标识（脱敏手机号；缺失兜底为「用户 {id}」） */
    val label: String,
    /** 是否发起者 */
    val isCreator: Boolean,
    /** 是否当前用户 */
    val isMe: Boolean,
)

/**
 * 点单页详情 UI 模型（api 模块 [DiningOrderDetailVo] → 点单页渲染模型）：
 * 在映射层一次性消化 safe 兜底、分类分段、选菜人标识与「我的选菜」识别。
 */
data class DiningOrderPageDetail(
    /** 订单 ID */
    val id: Long,
    /** 订单标题 */
    val title: String,
    /** 订单状态（白名单外/缺失保守降级 FINISHED） */
    val status: DiningOrderStatus,
    /** 服务端 version（写接口乐观并发闸门；本地写失败留待 10 号票重放） */
    val version: Long,
    /** 是否进行中（可勾选/改备注） */
    val isOngoing: Boolean,
    /** 当前用户是否发起者（结束点单入口可见性） */
    val isCreator: Boolean,
    /** 加入凭证（仅发起者详情下发；空串=不可分享） */
    val joinToken: String,
    /** 购买清单快照 JSON（FINISHED 后写入；空串=进行中/无清单，只读态据此展示「查看清单」入口） */
    val shoppingList: String,
    /** 当前用户标识（未取到为 null——不识别「我的选菜」） */
    val myUserId: Long?,
    /** 分类分段（左栏 + 右列表共用顺序） */
    val sections: List<DiningMenuSection>,
    /** 参与者（参与者区） */
    val members: List<DiningParticipant>,
)

/**
 * 手机号脱敏（镜像后端 DiningOrderServiceImpl.maskPhone 规则，纯函数便于单测）：
 * 11 位按 3+4+4 掩中间；长度 ≤5 原样返回；其余保留前 3 后 2；空值返回空串。
 */
fun maskDiningPhone(phone: String?): String {
    val value = phone?.trim().orEmpty()
    if (value.isEmpty()) return ""
    if (value.length == 11) return value.substring(0, 3) + "****" + value.substring(7)
    if (value.length <= 5) return value
    return value.substring(0, 3) + "****" + value.substring(value.length - 2)
}

/**
 * 由当前登录手机号在参与者中定位当前用户 ID（端上无 userId 契约，详情成员仅回传脱敏手机号，
 * 故按同源脱敏规则比对；手机号缺失/未命中返回 null——降级为不识别「我的选菜」，不误判）。
 */
fun resolveMyUserId(
    members: List<DiningOrderDetailVo.OrderMember>,
    currentPhone: String?,
): Long? {
    val masked = maskDiningPhone(currentPhone)
    if (masked.isEmpty()) return null
    return members.firstOrNull { it.safePhoneMasked == masked }?.safeUserId
}

/**
 * 本单菜单按分类分段（纯函数）：
 * - 分类 ID 为 null/0 视为「未分类」段（收尾渲染）；
 * - 段顺序 = 菜单中分类首次出现顺序（保持发起者编排顺序）；
 * - 段名取分类目录 [categoryNames]，目录缺失时按「分类 {id}」兜底、未分类用 [uncategorizedLabel]；
 * - 段内菜品保持菜单顺序。
 */
fun groupMenuByCategory(
    menu: List<DiningOrderDetailVo.MenuRecipe>,
    categoryNames: Map<Long, String>,
    uncategorizedLabel: String,
): List<DiningMenuSection> {
    if (menu.isEmpty()) return emptyList()
    val order = mutableListOf<Long?>()
    val grouped = LinkedHashMap<Long?, MutableList<DiningOrderDetailVo.MenuRecipe>>()
    for (recipe in menu) {
        val key = recipe.categoryId?.takeIf { it > 0L }
        if (grouped[key] == null) {
            grouped[key] = mutableListOf()
            order += key
        }
        grouped[key]!!.add(recipe)
    }
    return order.map { categoryId ->
        val dishes = grouped[categoryId].orEmpty().map { recipe ->
            // 占位转换在 toOrderPageDetail 内合并选菜信息；此处仅承载菜名/分类
            DiningMenuItem(
                recipeId = recipe.safeRecipeId,
                name = recipe.safeName,
                coverUrl = "",
                categoryId = categoryId,
                selectedByMe = false,
                myNote = "",
                selectors = emptyList(),
            )
        }
        DiningMenuSection(
            categoryId = categoryId,
            categoryName = categoryNameOf(categoryId, categoryNames, uncategorizedLabel),
            dishes = dishes,
        )
    }
}

/** 分类名兜底：未分类用调用方文案；目录缺失按「分类 {id}」暴露而非静默并入未分类 */
internal fun categoryNameOf(
    categoryId: Long?,
    categoryNames: Map<Long, String>,
    uncategorizedLabel: String,
): String = when {
    categoryId == null -> uncategorizedLabel
    else -> categoryNames[categoryId] ?: "分类 $categoryId"
}

/**
 * [DiningOrderDetailVo] → 点单页详情模型（safe 兜底 + 分类分段 + 选菜人标识集中在此层）。
 *
 * @param myUserId 当前用户 ID（[resolveMyUserId] 产物，null=不识别我的选菜）
 * @param categoryNames 分类 ID → 名称（分类目录接口；缺失按「分类 {id}」兜底）
 * @param coverUrls 菜谱 ID → 封面直链（上架菜谱接口；缺失渲染占位底）
 * @param uncategorizedLabel 未分类段文案（调用方传 strings.xml）
 */
fun DiningOrderDetailVo.toOrderPageDetail(
    myUserId: Long?,
    categoryNames: Map<Long, String>,
    coverUrls: Map<Long, String>,
    uncategorizedLabel: String,
): DiningOrderPageDetail {
    val itemsByRecipe = safeItems.groupBy { it.safeRecipeId }
    val memberLabels = safeMembers.associate { it.safeUserId to it.safePhoneMasked }

    val sections = groupMenuByCategory(safeMenu, categoryNames, uncategorizedLabel).map { section ->
        section.copy(
            dishes = section.dishes.map { dish ->
                val recipeItems = itemsByRecipe[dish.recipeId].orEmpty()
                val myItem = recipeItems.firstOrNull { it.safeUserId == myUserId }
                DiningMenuItem(
                    recipeId = dish.recipeId,
                    name = dish.name,
                    coverUrl = coverUrls[dish.recipeId].orEmpty(),
                    categoryId = dish.categoryId,
                    selectedByMe = myItem != null,
                    myNote = myItem?.safeNote.orEmpty(),
                    selectors = recipeItems.map { item ->
                        DiningSelector(
                            userId = item.safeUserId,
                            label = memberLabels[item.safeUserId]?.takeIf { it.isNotBlank() }
                                ?: "用户 ${item.safeUserId}",
                            note = item.safeNote,
                            isMe = myUserId != null && item.safeUserId == myUserId,
                        )
                    },
                )
            },
        )
    }

    return DiningOrderPageDetail(
        id = safeId,
        title = safeTitle,
        status = diningOrderStatusOf(safeStatus),
        version = safeVersion,
        isOngoing = diningOrderStatusOf(safeStatus) == DiningOrderStatus.ONGOING,
        isCreator = myUserId != null && safeCreatorUserId == myUserId,
        joinToken = safeJoinToken,
        shoppingList = safeShoppingList,
        myUserId = myUserId,
        sections = sections,
        members = safeMembers.map { member ->
            DiningParticipant(
                userId = member.safeUserId,
                label = member.safePhoneMasked.takeIf { it.isNotBlank() } ?: "用户 ${member.safeUserId}",
                isCreator = member.safeIsCreator,
                isMe = myUserId != null && member.safeUserId == myUserId,
            )
        },
    )
}
