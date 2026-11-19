package com.smartwash.feature.dining.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartwash.common.ui.theme.AppColors
import com.smartwash.common.ui.theme.AppDimens
import com.smartwash.common.utils.pressable
import com.smartwash.feature.dining.R
import com.smartwash.feature.dining.logic.DiningAllergenGuard
import com.smartwash.feature.dining.logic.DiningNutritionCalculator
import com.smartwash.feature.dining.logic.DiningNutritionCalculator.Nutrient
import com.smartwash.feature.dining.logic.DiningNutritionCalculator.NutrientStatus

/**
 * 吃啥营养口径共享小部件（发起点单 / 购买清单共用）：警示条（warningContainer /
 * dangerInk 语义容器，仅命中时出现，非阻断、估算口径）与营养项/忌口文案单位映射
 * （文案单一事实来源在 strings.xml，数值格式化统一走 [DiningNutritionCalculator] 纯函数）。
 *
 * 警示条为单行摘要 + 尾部 ⓘ 展开形态：行内只占一行高度，逐项明细收进点击展开的
 * 下拉浮层（渐进披露）——超标/忌口常多项，平铺逐项会把点单列表挤压出两屏。
 */

/** 营养超标警示条（契约 §5.2/§5.3）：单行「营养预估超标 N 项」+ ⓘ，明细浮层逐项展示 */
@Composable
internal fun DiningNutritionWarningBar(
    items: List<NutrientStatus>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var showDetail by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    WarningBarShell(
        modifier = modifier,
        containerColor = AppColors.colorScheme.warningContainer,
        contentColor = AppColors.colorScheme.onWarningContainer,
        summary = stringResource(R.string.dining_nutrition_warning_summary_format, items.size),
        detailTitle = stringResource(R.string.dining_nutrition_warning_title),
        expanded = showDetail,
        onToggle = { showDetail = !showDetail },
        onDismiss = { showDetail = false },
    ) {
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
 * 忌口警示条（契约 §三.1）：选中菜 allergens ∩ 用户忌口非空时出现，单行摘要 +
 * ⓘ 明细浮层逐条「含忌口：<忌口名>（<菜名>）」，红色系（异常/取消语义的粉容器 +
 * dangerInk 小字），与营养超标警示条同区并存、各占一行，非阻断。
 */
@Composable
internal fun DiningAllergenWarningBar(
    hits: List<DiningAllergenGuard.Hit>,
    modifier: Modifier = Modifier,
) {
    if (hits.isEmpty()) return
    var showDetail by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    WarningBarShell(
        modifier = modifier,
        containerColor = AppColors.colorScheme.iconContainerPink,
        contentColor = AppColors.colorScheme.dangerInk,
        summary = stringResource(R.string.dining_allergen_warning_summary_format, hits.size),
        detailTitle = stringResource(R.string.dining_allergen_warning_title),
        expanded = showDetail,
        onToggle = { showDetail = !showDetail },
        onDismiss = { showDetail = false },
    ) {
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

/**
 * 警示条共用单行外壳：摘要文案 + 尾部 ⓘ 热区（32dp 图形热区外扩至 48dp 触控），
 * 点 ⓘ 弹下拉浮层（M3 DropdownMenu 先例 ApiConfigPage）——浮层标题沿用原完整警示
 * 标题，正文为调用方逐项行。
 */
@Composable
private fun WarningBarShell(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    summary: String,
    detailTitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    detailContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.pagePadding, vertical = AppDimens.spaceXxs)
            .clip(RoundedCornerShape(AppDimens.radiusMd))
            .background(containerColor)
            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(contentAlignment = Alignment.Center) {
            // ⓘ 触控热区 48dp（规范 §3.2），图形 18dp 居中；行高内上下各溢出为纯热区
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressable(onClick = onToggle, debounce = false),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = detailTitle,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = AppDimens.spaceMd,
                        vertical = AppDimens.spaceSm,
                    ),
                ) {
                    Text(
                        text = detailTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.colorScheme.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.space2xs))
                    detailContent()
                }
            }
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
