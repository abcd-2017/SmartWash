package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningCategoryVo
import com.smartwash.feature.dining.api.model.DiningOrderBriefVo
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningOrderListVo
import com.smartwash.feature.dining.api.model.DiningOrderStatus
import com.smartwash.feature.dining.api.model.DiningRecipeVo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 菜谱库 UI 模型（api 模块 DiningRecipeVo → 页面渲染模型）：
 * 在映射层一次性消化 safe 兜底与展示派生值，页面不再触碰可空原始字段（对齐 notice 的
 * 「safe 系在消费方兜底」约定，映射集中便于 JVM 单测锚定）。
 */
data class DiningRecipeItem(
    /** 菜谱 ID（列表 key / 详情路由参数；id 缺失按 0 兜底，列表项点击守卫不跳转） */
    val id: Long,
    /** 菜名 */
    val name: String,
    /** 分类名（未分类/缺失为空串，列表隐藏分类角标） */
    val categoryName: String,
    /** 封面直链（未上传为空串，列表渲染占位底） */
    val coverUrl: String,
    /** 食材名数组（详情/列表角标用） */
    val ingredients: List<String>,
) {
    /** 食材数角标文案参数（0 时列表隐藏角标） */
    val ingredientCount: Int get() = ingredients.size
}

/** 分类筛选 chip 模型：[id] 为 null 表示「全部」伪分类（不携带筛选参数） */
data class DiningCategoryChip(
    val id: Long?,
    val name: String,
)

/** 我的订单列表项 UI 模型（api 模块 DiningOrderListVo → 订单列表页渲染模型） */
data class DiningOrderListItem(
    /** 订单 ID（分享视图路由参数；id 缺失按 0 兜底，列表项点击守卫不跳转） */
    val id: Long,
    /** 订单标题 */
    val title: String,
    /** 订单状态（白名单外/缺失保守降级为 FINISHED——展示与交互均按不可写处理） */
    val status: DiningOrderStatus,
    /** 是否我发起的（列表页分区/角色角标依据） */
    val isCreator: Boolean,
    /** 创建时间展示文案（ISO 原文格式化，畸形原文原样返回；空串隐藏时间行） */
    val displayTime: String,
)

/** 加入前预览 UI 模型（api 模块 DiningOrderBriefVo → 扫码确认弹窗渲染模型） */
data class DiningOrderBriefUi(
    /** 订单 ID（0=后端异常响应兜底，确认动作守卫不发起） */
    val orderId: Long,
    /** 订单标题（缺失空串，弹窗降级展示） */
    val title: String,
    /** 订单状态（白名单外/缺失保守降级 FINISHED——已结束不引导加入） */
    val status: DiningOrderStatus,
    /** 发起者标识（脱敏手机号，缺失空串） */
    val creatorName: String,
    /** 本单菜单菜品数（缺失兜底 0） */
    val recipeCount: Int,
    /** 当前用户是否已在单中（已加入时跳过确认弹窗直接进详情） */
    val joined: Boolean,
)

/** 订单分享视图 UI 模型（api 模块 DiningOrderDetailVo → 分享页渲染模型） */
data class DiningOrderShareDetail(
    /** 订单 ID（0=详情拉取失败降级） */
    val id: Long,
    /** 订单标题 */
    val title: String,
    /** 订单状态（白名单外/缺失保守降级为 FINISHED） */
    val status: DiningOrderStatus,
    /** 本单菜单菜品数（轻量响应/字段缺失兜底 0） */
    val menuCount: Int,
    /** 参与者人数 */
    val memberCount: Int,
    /** 加入凭证（仅发起者详情下发；空串=不可分享，页面降级提示） */
    val joinToken: String,
)

/** DiningRecipeVo → 菜谱库列表项（safe 兜底集中在映射层） */
fun DiningRecipeVo.toRecipeItem(): DiningRecipeItem = DiningRecipeItem(
    id = safeId,
    name = safeName,
    categoryName = safeCategoryName,
    coverUrl = safeCoverUrl,
    ingredients = safeIngredients,
)

/**
 * 分类列表 → 筛选 chips（头部固定追加「全部」伪分类；后端已按 sort/id 升序返回，保序渲染）。
 * [allLabel] 由调用方传 strings.xml 文案——纯逻辑不持有文案（对齐 noticeTimeParts 先例）。
 */
fun List<DiningCategoryVo>.toCategoryChips(allLabel: String): List<DiningCategoryChip> =
    listOf(DiningCategoryChip(id = null, name = allLabel)) + map { DiningCategoryChip(id = it.safeId, name = it.safeName) }

/** DiningOrderListVo → 我的订单列表项（safe 兜底 + 状态保守降级集中在映射层） */
fun DiningOrderListVo.toOrderListItem(): DiningOrderListItem = DiningOrderListItem(
    id = safeId,
    title = safeTitle,
    status = diningOrderStatusOf(safeStatus),
    isCreator = safeIsCreator,
    displayTime = formatDiningTime(createdAt),
)

/** DiningOrderBriefVo → 加入确认模型（safe 兜底 + 状态保守降级集中在映射层） */
fun DiningOrderBriefVo.toBriefUi(): DiningOrderBriefUi = DiningOrderBriefUi(
    orderId = safeOrderId,
    title = safeTitle,
    status = diningOrderStatusOf(safeStatus),
    creatorName = safeCreatorName,
    recipeCount = safeRecipeCount,
    joined = safeJoined,
)

/** DiningOrderDetailVo → 订单分享视图模型（safe 兜底 + 状态保守降级集中在映射层） */
fun DiningOrderDetailVo.toOrderShareDetail(): DiningOrderShareDetail = DiningOrderShareDetail(
    id = safeId,
    title = safeTitle,
    status = diningOrderStatusOf(safeStatus),
    menuCount = safeMenu.size,
    memberCount = safeMembers.size,
    joinToken = safeJoinToken,
)

/**
 * 状态码 → 枚举：白名单外/空白脏数据保守降级为 FINISHED（未知状态按「不可写」处理，
 * 不引导用户对未知态下单；后端 DiningOrderStatus.isValid 已保证入库白名单，此处为
 * Gson 容错契约的最后一道）。
 */
fun diningOrderStatusOf(code: String?): DiningOrderStatus =
    DiningOrderStatus.fromCode(code) ?: DiningOrderStatus.FINISHED

/**
 * 后端 ISO 时间串 → 展示文案（yyyy-MM-dd HH:mm）。
 * 容忍毫秒（.123）与时区尾巴（Z / +08:00）——后端 LocalDateTime 输出为裸 ISO，
 * 尾巴形态为防御性兼容；剥离时区尾巴不换算时区（后端无时区语义）。
 * 解析失败原样返回便于排查，null/空白返回空串（列表隐藏时间行）。
 */
fun formatDiningTime(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ""
    val normalized = value
        .removeSuffix("Z")
        .replace(Regex("[+-]\\d{2}:?\\d{2}$"), "")
    val parsed = runCatching { LocalDateTime.parse(normalized) }.getOrNull() ?: return value
    return parsed.format(DINING_TIME_FORMATTER)
}

/** 时间展示格式（列表/详情共用；数量与取件码场景零依赖，仅时间锚点） */
private val DINING_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
