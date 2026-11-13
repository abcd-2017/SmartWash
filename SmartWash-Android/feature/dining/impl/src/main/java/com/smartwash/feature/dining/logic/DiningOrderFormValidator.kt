package com.smartwash.feature.dining.logic

/**
 * 创建订单表单校验（纯 Kotlin，无 Android 依赖，JVM 单测锚点）。
 *
 * 规则与后端 from/CreateDiningOrderFrom 的 Bean Validation + service 二选一裁决一致
 * （端上先拦给友好提示，后端校验是最终兜底）：标题必填 ≤64 字符；本单菜单至少一道、
 * 上限 100 道（后端 @Size(max=100) + 「本单菜单不能为空」service 校验）。
 * 复用历史菜单入口（13 号票）走 [validateReuse]——菜单由后端按 fromOrderId 整份复制，
 * 端上只校验标题、不做菜单非空校验（显式 recipeIds 与 fromOrderId 唯一链路）。
 */
object DiningOrderFormValidator {

    /** 标题长度上限（对齐后端 @Size(max = 64)） */
    const val TITLE_MAX_LENGTH = 64

    /** 本单菜单菜品数上限（对齐后端 @Size(max = 100)） */
    const val MENU_MAX_COUNT = 100

    /**
     * 校验错误枚举（纯逻辑不持有文案）：文案由 UI 经 strings.xml 映射（dining_order_error_*）。
     * 字段独立校验、逐项短路返回首个错误——页面按字段就近提示。
     */
    enum class DiningOrderFormError {
        /** 标题必填 */
        TITLE_REQUIRED,

        /** 标题超长 */
        TITLE_TOO_LONG,

        /** 至少选一道菜 */
        MENU_EMPTY,

        /** 菜品数量超限 */
        MENU_TOO_MANY,
    }

    /**
     * 标题校验：trim 后非空且不超长，合法返回 trim 后文本（提交统一用 trim 值）。
     */
    fun validateTitle(raw: String): TitleValidation {
        val title = raw.trim()
        return when {
            title.isEmpty() -> TitleValidation(valid = false, error = DiningOrderFormError.TITLE_REQUIRED)
            title.length > TITLE_MAX_LENGTH -> TitleValidation(valid = false, error = DiningOrderFormError.TITLE_TOO_LONG)
            else -> TitleValidation(valid = true, normalized = title)
        }
    }

    /**
     * 本单菜单校验：至少一道（发起一场点单必须有菜品范围）、不超上限。
     * 菜谱 ID 来自公共库列表（后端服务层再做去重保序与上架存在性把关）。
     */
    fun validateMenu(recipeIds: List<Long>): MenuValidation {
        return when {
            recipeIds.isEmpty() -> MenuValidation(valid = false, error = DiningOrderFormError.MENU_EMPTY)
            recipeIds.size > MENU_MAX_COUNT -> MenuValidation(valid = false, error = DiningOrderFormError.MENU_TOO_MANY)
            else -> MenuValidation(valid = true, normalized = recipeIds)
        }
    }

    /**
     * 组合校验（提交入口）：标题先行、菜单随后，逐项短路返回首个错误。
     */
    fun validate(title: String, recipeIds: List<Long>): FormValidation {
        val titleValidation = validateTitle(title)
        if (!titleValidation.valid) {
            return FormValidation(valid = false, error = titleValidation.error)
        }
        val menuValidation = validateMenu(recipeIds)
        if (!menuValidation.valid) {
            return FormValidation(valid = false, error = menuValidation.error)
        }
        return FormValidation(
            valid = true,
            normalizedTitle = titleValidation.normalized,
            normalizedRecipeIds = menuValidation.normalized,
        )
    }

    /**
     * 复用历史菜单模式的提交校验：只校验标题（菜单由后端按 fromOrderId 整份复制，
     * 页面只读回显、不参与提交），故不做「至少一道菜」校验。
     */
    fun validateReuse(title: String): TitleValidation = validateTitle(title)

    /** 标题校验结果：[normalized] 仅为合法时携带的 trim 值 */
    data class TitleValidation(
        val valid: Boolean,
        val normalized: String = "",
        val error: DiningOrderFormError? = null,
    )

    /** 本单菜单校验结果：[normalized] 仅为合法时携带的菜谱 ID 列表 */
    data class MenuValidation(
        val valid: Boolean,
        val normalized: List<Long> = emptyList(),
        val error: DiningOrderFormError? = null,
    )

    /** 组合校验结果：归一化字段仅为合法时携带 */
    data class FormValidation(
        val valid: Boolean,
        val normalizedTitle: String = "",
        val normalizedRecipeIds: List<Long> = emptyList(),
        val error: DiningOrderFormError? = null,
    )
}
