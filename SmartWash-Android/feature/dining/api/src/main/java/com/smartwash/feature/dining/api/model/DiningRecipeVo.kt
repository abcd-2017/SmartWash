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
 */
@Keep
data class DiningRecipeVo(
    /** 菜谱 ID（列表 item key / 详情路由参数） */
    val id: Long? = null,
    /** 所属分类 ID，null=未分类 */
    val categoryId: Long? = null,
    /** 所属分类名称（后端联查回填，未分类为 null） */
    val categoryName: String? = null,
    /** 菜名 */
    val name: String? = null,
    /** 封面对外直链，null=未上传（后端按对象键拼装，端上不存/不拼 URL） */
    val coverUrl: String? = null,
    /** 食材名数组，null/缺失按空列表兜底 */
    val ingredients: List<String>? = null,
    /** 做法步骤（纯文本，可含换行） */
    val steps: String? = null,
    /** 来源：ADMIN=运营录入，USER=用户新增（直上架） */
    val source: String? = null,
    /** 上下架：1=上架 0=下架（用户端恒为 1，仅防御性兜底） */
    val status: Int? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeName: String get() = name.orEmpty()
    val safeCategoryName: String get() = categoryName.orEmpty()
    val safeCoverUrl: String get() = coverUrl.orEmpty()
    val safeIngredients: List<String> get() = ingredients.orEmpty()
    val safeSteps: String get() = steps.orEmpty()
}
