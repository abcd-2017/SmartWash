package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 菜谱视图对象（GET /web/auth/dining/recipes/all 与 /{id} 载荷，与后端 dining/vo/DiningRecipeVo 对应）。
 *
 * 后端语义：列表与详情均只返回上架菜谱（status=1 服务端固定过滤，端上无 status 入参）；
 * ingredients 为结构化食材名数组（后端服务层由 DB JSON 反序列化）；coverUrl 为封面直链
 * （MinIO dining/covers/ 白名单匿名读，后端由对象键拼装）。
 *
 * Gson 容错约定：字段全部可空 + 默认 null，消费方经 safe 系兜底；coverUrl 缺失按无封面渲染占位。
 * 营养 6 字段（契约 §三 3.4）可空即语义：null = 未录入（热量未知），不参与热量聚合，无需 safe 非
 * 空兜底——「未知」本身是要渲染的状态。
 */
@Keep
data class DiningRecipeVo(
    /** 菜谱 ID（列表 item key / 详情路由参数） */
    val id: Long? = null,
    /** 所属分类 ID，null=未分类 */
    val categoryId: Long? = null,
    /** 所属分类名称（后端联查回填，未分类为 null） */
    val categoryName: String? = null,
    val name: String? = null,
    /** 封面对外直链，null=未上传（后端按对象键拼装，端上不存/不拼 URL） */
    val coverUrl: String? = null,
    /** 食材名数组，null/缺失按空列表兜底 */
    val ingredients: List<String>? = null,
    /** 做法步骤（纯文本，可含换行） */
    val steps: String? = null,
    /** 热量 Kcal/100g（CookLikeHOC 营养表口径），null=未录入 */
    val caloriesPer100g: Double? = null,
    /**
     * 整份估算热量 Kcal（HowToCook 预估卡路里口径），null=未录入。
     * 展示口径（契约 §二，四端一致）：一份热量 = caloriesTotal ?: caloriesPer100g × 250g
     * （250g 为份量假设常量），换算与聚合见 impl 侧 DiningNutritionCalculator。
     */
    val caloriesTotal: Double? = null,
    /** 蛋白质 g/100g，null=未录入 */
    val proteinG: Double? = null,
    /** 脂肪 g/100g，null=未录入 */
    val fatG: Double? = null,
    /** 碳水化合物 g/100g，null=未录入 */
    val carbsG: Double? = null,
    /** 钠 mg/100g，null=未录入 */
    val sodiumMg: Double? = null,
    /** 来源：ADMIN=运营录入，USER=用户新增（直上架） */
    val source: String? = null,
    /** 上下架：1=上架 0=下架（用户端恒为 1，仅防御性兜底） */
    val status: Int? = null,
    /**
     * 当前用户是否已收藏（契约 §2.3）：列表由后端按 user_id 批量填充，详情恒有值；
     * null=未填充（老响应/管理端），端上按未收藏兜底。
     */
    val favorited: Boolean? = null,
    /**
     * 该菜全部配料的过敏原去重集合（契约 §2.2，仅用户端详情接口拼装，按 ALLERGEN_VALUES 顺序）；
     * 列表接口不带（避免放大 payload），null/缺失按空列表兜底。
     */
    val allergens: List<String>? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeName: String get() = name.orEmpty()
    val safeCategoryName: String get() = categoryName.orEmpty()
    val safeCoverUrl: String get() = coverUrl.orEmpty()
    val safeIngredients: List<String> get() = ingredients.orEmpty()
    val safeSteps: String get() = steps.orEmpty()
    val safeAllergens: List<String> get() = allergens.orEmpty()
}
