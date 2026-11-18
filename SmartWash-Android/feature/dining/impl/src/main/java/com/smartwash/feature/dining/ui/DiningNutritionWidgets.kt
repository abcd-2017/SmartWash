package com.smartwash.feature.dining.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningAllergenGuard
import com.smartwash.feature.dining.logic.DiningNutritionCalculator
import com.smartwash.feature.dining.logic.DiningNutritionCalculator.Nutrient
import com.smartwash.feature.dining.logic.DiningNutritionCalculator.NutrientStatus

/**
 * 吃啥营养口径共享小部件（发起点单 / 购买清单共用）：营养超标警示条
 * （warningContainer 语义容器，仅超标时出现，非阻断、估算口径）与营养项文案/单位映射
 * （文案单一事实来源在 strings.xml，数值格式化统一走 [DiningNutritionCalculator] 纯函数）。
 */

/** 营养超标警示条（契约 §5.2/§5.3）：逐项「钠预计 2.4mg，超一餐参考 666.7mg」形态，非阻断 */
@Composable
internal fun DiningNutritionWarningBar(
    items: List<NutrientStatus>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXxs)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(AppColors.colorScheme.warningContainer)
            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm),
    ) {
        Text(
            text = stringResource(R.string.dining_nutrition_warning_title),
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.colorScheme.onWarningContainer,
        )
        Spacer(modifier = Modifier.height(AppDimens.space2xs))
        items.forEach { status ->
            DiningWarningItemLine(status = status)
        }
    }
}

/** 单项超标行：「钠预计 2.4mg，超一餐参考 666.7mg」（单位随营养项，估算口径） */
@Composable
private fun DiningWarningItemLine(status: NutrientStatus) {
    val unit = diningNutrientUnit(status.nutrient)
    Text(
        text = stringResource(
            R.string.dining_nutrition_warning_item_format,
            diningNutrientLabel(status.nutrient),
            DiningNutritionCalculator.formatAmount(status.value).orEmpty() + unit,
            DiningNutritionCalculator.formatAmount(status.reference).orEmpty() + unit,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.colorScheme.onWarningContainer,
    )
}

/**
 * 忌口警示条（契约 §三.1）：选中菜 allergens ∩ 用户忌口非空时出现，逐条
 * 「含忌口：<忌口名>（<菜名>）」，红色系（异常/取消语义的粉容器 + dangerInk 小字），
 * 与营养超标警示条同区并存、各占一条，非阻断。
 */
@Composable
internal fun DiningAllergenWarningBar(
    hits: List<DiningAllergenGuard.Hit>,
    modifier: Modifier = Modifier,
) {
    if (hits.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXxs)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(AppColors.colorScheme.iconContainerPink)
            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceSm),
    ) {
        Text(
            text = stringResource(R.string.dining_allergen_warning_title),
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.colorScheme.dangerInk,
        )
        Spacer(modifier = Modifier.height(AppDimens.space2xs))
        hits.forEach { hit ->
            Text(
                text = stringResource(
                    R.string.dining_allergen_warning_item_format,
                    diningAllergenLabel(hit.allergen),
                    hit.recipeName,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.colorScheme.dangerInk,
            )
        }
    }
}

/** 忌口标签展示名映射（SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SPICY/SOY；白名单外原样回显） */
@Composable
internal fun diningAllergenLabel(code: String): String {
    val res = when (code) {
        "SEAFOOD" -> R.string.dining_allergen_seafood
        "NUT" -> R.string.dining_allergen_nut
        "GLUTEN" -> R.string.dining_allergen_gluten
        "DAIRY" -> R.string.dining_allergen_dairy
        "EGG" -> R.string.dining_allergen_egg
        "SPICY" -> R.string.dining_allergen_spicy
        "SOY" -> R.string.dining_allergen_soy
        else -> null
    }
    return res?.let { stringResource(it) } ?: code
}

/** 营养项展示名映射（热量/蛋白质/脂肪/碳水/钠） */
@Composable
internal fun diningNutrientLabel(nutrient: Nutrient): String = stringResource(
    when (nutrient) {
        Nutrient.CALORIES -> R.string.dining_nutrient_calories
        Nutrient.PROTEIN -> R.string.dining_nutrient_protein
        Nutrient.FAT -> R.string.dining_nutrient_fat
        Nutrient.CARBS -> R.string.dining_nutrient_carbs
        Nutrient.SODIUM -> R.string.dining_nutrient_sodium
    },
)

/** 营养项单位映射（热量 kcal / 钠 mg / 其余 g，契约 §二参考值口径） */
@Composable
internal fun diningNutrientUnit(nutrient: Nutrient): String = stringResource(
    when (nutrient) {
        Nutrient.CALORIES -> R.string.dining_unit_kcal
        Nutrient.SODIUM -> R.string.dining_unit_milligram
        else -> R.string.dining_unit_gram
    },
)
