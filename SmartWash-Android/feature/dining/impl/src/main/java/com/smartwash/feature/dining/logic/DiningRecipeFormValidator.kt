package com.smartwash.feature.dining.logic

/**
 * 新增菜谱表单校验（纯 Kotlin，无 Android 依赖，JVM 单测锚点）。
 *
 * 规则与后端 from/AddDiningRecipeFrom 的 Bean Validation 一致（端上先拦给友好提示，
 * 后端校验是最终兜底）：菜名必填 ≤64 字符；食材每项 trim 后非空 ≤32 字符、总数 ≤100；
 * 步骤可空 ≤20000 字符。分类不做端上校验（0/null=未分类，>0 存在性由后端裁决）。
 */
object DiningRecipeFormValidator {

    /** 菜名长度上限（对齐后端 @Size(max = 64)） */
    const val NAME_MAX_LENGTH = 64

    /** 单个食材名长度上限（对齐后端 @Size(max = 32)） */
    const val INGREDIENT_MAX_LENGTH = 32

    /** 食材数量上限（对齐后端 @Size(max = 100)） */
    const val INGREDIENT_MAX_COUNT = 100

    /** 步骤长度上限（对齐后端 @Size(max = 20000)） */
    const val STEPS_MAX_LENGTH = 20000

    /**
     * 校验错误枚举（纯逻辑不持有文案）：文案由 UI 经 strings.xml 映射（dining_form_error_*）。
     * 字段独立校验、逐项短路返回首个错误——页面按字段就近提示。
     */
    enum class DiningRecipeFormError {
        /** 菜名必填 */
        NAME_REQUIRED,

        /** 菜名超长 */
        NAME_TOO_LONG,

        /** 存在空白食材项 */
        INGREDIENT_EMPTY,

        /** 单个食材名超长 */
        INGREDIENT_TOO_LONG,

        /** 食材数量超限 */
        INGREDIENT_TOO_MANY,

        /** 步骤超长 */
        STEPS_TOO_LONG,
    }

    /**
     * 菜名校验：trim 后非空且不超长，合法返回 trim 后文本（提交统一用 trim 值）。
     */
    fun validateName(raw: String): NameValidation {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameValidation(valid = false, error = DiningRecipeFormError.NAME_REQUIRED)
            name.length > NAME_MAX_LENGTH -> NameValidation(valid = false, error = DiningRecipeFormError.NAME_TOO_LONG)
            else -> NameValidation(valid = true, normalized = name)
        }
    }

    /**
     * 食材列表校验：逐项 trim，发现空白/超长/超量即返回错误；
     * 合法返回去空白后的非空食材列表（输入框残留空白项在提交前清洗）。
     */
    fun validateIngredients(raw: List<String>): IngredientsValidation {
        if (raw.size > INGREDIENT_MAX_COUNT) {
            return IngredientsValidation(valid = false, error = DiningRecipeFormError.INGREDIENT_TOO_MANY)
        }
        val normalized = mutableListOf<String>()
        for (item in raw) {
            val trimmed = item.trim()
            if (trimmed.isEmpty()) {
                return IngredientsValidation(valid = false, error = DiningRecipeFormError.INGREDIENT_EMPTY)
            }
            if (trimmed.length > INGREDIENT_MAX_LENGTH) {
                return IngredientsValidation(valid = false, error = DiningRecipeFormError.INGREDIENT_TOO_LONG)
            }
            normalized += trimmed
        }
        return IngredientsValidation(valid = true, normalized = normalized)
    }

    /**
     * 步骤校验：可空/空白按「未填写」放行（null 提交，与后端 null=暂不填写语义一致），
     * 非空时超长拒绝；合法返回保留原始换行的文本（或 null）。
     */
    fun validateSteps(raw: String?): StepsValidation {
        val steps = raw?.trim()
        return when {
            steps.isNullOrEmpty() -> StepsValidation(valid = true, normalized = null)
            steps.length > STEPS_MAX_LENGTH -> StepsValidation(valid = false, error = DiningRecipeFormError.STEPS_TOO_LONG)
            else -> StepsValidation(valid = true, normalized = steps)
        }
    }

    /** 菜名校验结果：[normalized] 仅为合法时携带的 trim 值 */
    data class NameValidation(
        val valid: Boolean,
        val normalized: String = "",
        val error: DiningRecipeFormError? = null,
    )

    /** 食材校验结果：[normalized] 仅为合法时携带的清洗后列表 */
    data class IngredientsValidation(
        val valid: Boolean,
        val normalized: List<String> = emptyList(),
        val error: DiningRecipeFormError? = null,
    )

    /** 步骤校验结果：[normalized] 为合法时的提交值（未填写=null） */
    data class StepsValidation(
        val valid: Boolean,
        val normalized: String? = null,
        val error: DiningRecipeFormError? = null,
    )
}
