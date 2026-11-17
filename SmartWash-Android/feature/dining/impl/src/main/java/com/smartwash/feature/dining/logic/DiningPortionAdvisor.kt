package com.smartwash.feature.dining.logic

/**
 * 吃啥份量建议内核（纯 Kotlin，无 Android/Compose 依赖，JVM 单测锚点）。
 *
 * 口径（契约 §2.5）：
 * - 人均 = 本单总热量 ÷ 人数（人数 1-20，经 [DiningNutritionCalculator.coercePeopleCount] 收敛）；
 * - 参考 = 用户每日热量目标 ÷ 3（一餐）；目标缺省（≤0）时用默认 2000 ÷ 3 ≈ 666.7 kcal；
 * - 判定：人均 ≥ 参考 × 0.9 且 ≤ 参考 × 1.1 → [Verdict.ENOUGH]；> 参考 × 1.1 → [Verdict.MORE]；
 *   < 参考 × 0.9 → [Verdict.LESS]；
 * - 总热量未知（null）→ 返回 null（页面不渲染建议，不谎报）。
 *
 * 文案由页面按 [Verdict] 取 strings.xml（「N 人吃 · 人均 ≈X kcal，够一餐」等）。
 */
object DiningPortionAdvisor {

    /** 默认每日热量目标（kcal）：用户未设目标时的兜底（契约 §2.1） */
    const val DEFAULT_CALORIE_TARGET = 2000

    /** 每日餐次（参考值 = 日目标 ÷ 3，与营养超标警示同一口径） */
    const val MEALS_PER_DAY = 3.0

    /** 够一餐下限系数（人均 ≥ 参考 × 0.9） */
    const val LOWER_RATIO = 0.9

    /** 够一餐上限系数（人均 ≤ 参考 × 1.1） */
    const val UPPER_RATIO = 1.1

    /** 份量判定 */
    enum class Verdict {
        /** 人均在参考 0.9-1.1 倍区间：够一餐 */
        ENOUGH,
        /** 人均高于参考 1.1 倍：偏多 */
        MORE,
        /** 人均低于参考 0.9 倍：偏少 */
        LESS,
    }

    /** 建议结果（页面文案与本地态人数回显共用） */
    data class Advice(
        /** 收敛后的人数（1-20） */
        val peopleCount: Int,
        /** 人均热量（kcal） */
        val perCapita: Double,
        /** 一餐参考热量（kcal）= 目标 ÷ 3 */
        val reference: Double,
        val verdict: Verdict,
    )

    /**
     * 计算份量建议。
     *
     * @param totalCalories 本单已知热量菜之和（未知为 null → 返回 null）
     * @param peopleCount 人数（越界收敛到 1-20）
     * @param calorieTargetPerDay 用户每日热量目标（≤0 时用默认 2000）
     */
    fun advice(totalCalories: Double?, peopleCount: Int, calorieTargetPerDay: Int): Advice? {
        if (totalCalories == null) return null
        val people = DiningNutritionCalculator.coercePeopleCount(peopleCount)
        val target = if (calorieTargetPerDay > 0) calorieTargetPerDay else DEFAULT_CALORIE_TARGET
        val reference = target / MEALS_PER_DAY
        val perCapita = totalCalories / people
        val verdict = when {
            perCapita > reference * UPPER_RATIO -> Verdict.MORE
            perCapita < reference * LOWER_RATIO -> Verdict.LESS
            else -> Verdict.ENOUGH
        }
        return Advice(peopleCount = people, perCapita = perCapita, reference = reference, verdict = verdict)
    }
}
