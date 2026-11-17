package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningRecipeVo
import java.util.Locale

/**
 * 吃啥营养计算内核（纯 Kotlin，无 Android/Compose 依赖，JVM 单测锚点）。
 *
 * 口径（契约 §二，四端一致写死）：
 * - **份热量** = caloriesTotal ?: caloriesPer100g × [PORTION_GRAMS] ÷ 100——HowToCook 整份口径
 *   优先；只有每 100g 口径（CookLikeHOC 营养表）时按 250g 份量假设换算；两者皆缺 = 热量未知
 *   （null），不参与热量聚合。250g 与后端 `DiningRecipeGate.PORTION_GRAMS`、Web 端为同一份量
 *   假设常量。
 * - **蛋白质/脂肪/碳水/钠**：库内为每 100g 口径，份估算 = 每 100g 值 × [PORTION_GRAMS] ÷ 100
 *   （与热量同一份量假设）。
 * - **一餐参考值**：《中国居民膳食指南》成人日参考值 ÷ 3——热量 2000 kcal ÷ 3 ≈ 666.7 kcal、
 *   蛋白质 60 g ÷ 3 = 20 g、脂肪 60 g ÷ 3 = 20 g、碳水 300 g ÷ 3 = 100 g、钠 2000 mg ÷ 3 ≈ 666.7 mg。
 *   聚合值超过参考值即「超一餐参考」（估算口径提醒，非阻断）。
 * - **人均** = 总热量 ÷ 人数（人数 1-20，见 [coercePeopleCount]）。
 *
 * 全部为纯函数：不持状态、不做 IO；页面把 VO/快照字段喂给 [dishNutrition] / [summarize]，
 * 渲染所需「哪项超标、当前值、参考值」由 [DiningNutritionReport] 承载。
 */
object DiningNutritionCalculator {

    /**
     * 份量假设常量（g）：一份菜按 250g 估算（契约 §二拍板，四端一致写死并注释）。
     * 每 100g 口径的营养参与估算时按 `值 × 250 ÷ 100` 折算到份。
     */
    const val PORTION_GRAMS = 250.0

    /** 人数 stepper 下限（购买清单页人数选择，契约 §5.4：1-20，本地态不落库） */
    const val PEOPLE_MIN = 1

    /** 人数 stepper 上限 */
    const val PEOPLE_MAX = 20

    /** 单菜份口径营养事实（由 [dishNutrition] 从 VO 派生；null = 该项未知） */
    data class DishNutrition(
        /** 份热量 Kcal（份热量口径见文件头 KDoc），null=热量未知 */
        val calories: Double?,
        /** 份蛋白质估算 g，null=未录入 */
        val proteinG: Double?,
        /** 份脂肪估算 g，null=未录入 */
        val fatG: Double?,
        /** 份碳水估算 g，null=未录入 */
        val carbsG: Double?,
        /** 份钠估算 mg，null=未录入 */
        val sodiumMg: Double?,
    )

    /** 营养项（一餐参考值 = 日参考 ÷ 3，见文件头 KDoc） */
    enum class Nutrient(val reference: Double) {
        /** 热量：日参考 2000 kcal ÷ 3 */
        CALORIES(666.7),
        /** 蛋白质：日参考 60 g ÷ 3 */
        PROTEIN(20.0),
        /** 脂肪：日参考 60 g ÷ 3 */
        FAT(20.0),
        /** 碳水化合物：日参考 300 g ÷ 3 */
        CARBS(100.0),
        /** 钠：日参考 2000 mg ÷ 3 */
        SODIUM(666.7),
    }

    /** 单营养项聚合状态（UI 渲染单元：哪项、当前值、参考值） */
    data class NutrientStatus(
        val nutrient: Nutrient,
        /** 全部已知菜聚合后的份口径总量；该项无一菜已知为 null（未知不误报超标） */
        val value: Double?,
        /** 一餐参考值（[Nutrient.reference]） */
        val reference: Double,
    ) {
        /** 是否超一餐参考（value 未知恒为 false） */
        val exceeded: Boolean get() = value != null && value > reference
    }

    /** 聚合报告：总热量 + 五项营养状态（页面据此渲染热量徽标/人均/超标警示条） */
    data class Report(
        /** 已知热量菜之和；一菜热量皆未知为 null（热量未知态） */
        val totalCalories: Double?,
        /** 热量已知菜数 */
        val calorieKnownCount: Int,
        /** 热量未知菜数（UI 提示「N 道菜热量未知，未计入」） */
        val calorieUnknownCount: Int,
        /** 五项营养状态（固定 CALORIES/PROTEIN/FAT/CARBS/SODIUM 顺序） */
        val nutrients: List<NutrientStatus>,
    ) {
        /** 超一餐参考的项（保 nutrients 顺序；空 = 无超标，不渲染警示条） */
        val exceededItems: List<NutrientStatus> get() = nutrients.filter { it.exceeded }
    }

    /** 份热量口径：caloriesTotal 优先；否则 caloriesPer100g × 250g ÷ 100；两者皆缺为 null */
    fun servingCalories(caloriesTotal: Double?, caloriesPer100g: Double?): Double? =
        caloriesTotal ?: caloriesPer100g?.let { it * PORTION_GRAMS / 100.0 }

    /** 每 100g 口径营养 → 份估算（× 250g ÷ 100）；null=未录入 */
    fun servingNutrient(per100g: Double?): Double? = per100g?.let { it * PORTION_GRAMS / 100.0 }

    /** 菜谱 VO → 份口径营养事实（映射与兜底集中在此，页面不触碰原始可空字段） */
    fun dishNutrition(recipe: DiningRecipeVo): DishNutrition = DishNutrition(
        calories = servingCalories(recipe.caloriesTotal, recipe.caloriesPer100g),
        proteinG = servingNutrient(recipe.proteinG),
        fatG = servingNutrient(recipe.fatG),
        carbsG = servingNutrient(recipe.carbsG),
        sodiumMg = servingNutrient(recipe.sodiumMg),
    )

    /** 聚合所选菜营养（空列表 → 全未知空报告；热量只统计已知菜，未知菜单独计数） */
    fun summarize(dishes: List<DishNutrition>): Report {
        if (dishes.isEmpty()) {
            return Report(
                totalCalories = null,
                calorieKnownCount = 0,
                calorieUnknownCount = 0,
                nutrients = Nutrient.entries.map { NutrientStatus(it, null, it.reference) },
            )
        }
        val totalCalories = dishes.map { it.calories }
            .filterNotNull()
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val protein = sumOrNull(dishes.map { it.proteinG })
        val fat = sumOrNull(dishes.map { it.fatG })
        val carbs = sumOrNull(dishes.map { it.carbsG })
        val sodium = sumOrNull(dishes.map { it.sodiumMg })
        return Report(
            totalCalories = totalCalories,
            calorieKnownCount = dishes.count { it.calories != null },
            calorieUnknownCount = dishes.count { it.calories == null },
            nutrients = listOf(
                NutrientStatus(Nutrient.CALORIES, totalCalories, Nutrient.CALORIES.reference),
                NutrientStatus(Nutrient.PROTEIN, protein, Nutrient.PROTEIN.reference),
                NutrientStatus(Nutrient.FAT, fat, Nutrient.FAT.reference),
                NutrientStatus(Nutrient.CARBS, carbs, Nutrient.CARBS.reference),
                NutrientStatus(Nutrient.SODIUM, sodium, Nutrient.SODIUM.reference),
            ),
        )
    }

    /** 人均热量 = 总热量 ÷ 人数（人数经 [coercePeopleCount] 收敛到 1-20）；总热量未知为 null */
    fun perCapita(totalCalories: Double?, peopleCount: Int): Double? {
        if (totalCalories == null) return null
        return totalCalories / coercePeopleCount(peopleCount)
    }

    /** 人数收敛到契约区间（1-20）：越界钳制、异常值兜底，页面 stepper 与人均共用 */
    fun coercePeopleCount(value: Int): Int = value.coerceIn(PEOPLE_MIN, PEOPLE_MAX)

    /**
     * 数量展示格式化（热量/人均/配料聚合总量共用）：整数去尾零（1830.0 → "1830"），
     * 非整数最多保留 1 位小数（666.66 → "666.7"）；null 返回 null（热量未知态由调用方渲染）。
     * 纯函数便于 JVM 单测锚定跨端一致格式。
     */
    fun formatAmount(value: Double?): String? {
        if (value == null) return null
        if (value.isNaN() || value.isInfinite()) return null
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.floor(rounded) && !rounded.isInfinite()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    /** 可空数值求和：全部未知为 null（区分「0」与「无一菜已知」，未知不误报） */
    private fun sumOrNull(values: List<Double?>): Double? =
        values.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
}
