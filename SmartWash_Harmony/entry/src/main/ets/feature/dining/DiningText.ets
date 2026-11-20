// 吃啥（dining）域文案表 — 键名对齐 Android feature/dining/impl/src/main/res/values/strings.xml
// 的 dining_* 资源，便于跨端文案核对；取值走 resString（string.json / StringResources /
// StringFallback 三处同源维护）。
//
// 场景人称统一「同学」，显示名统一「吃啥」「点单」（不得出现「家庭点单」「家人」）。

import { formatDiningAmount } from './DiningLogic'
import { resString } from '../../utils/ResourceString'

/** 占位格式化：%d / %s 各替换一次（文案表格式串专用，不做多参数位置映射） */
function fmt(template: string, value: string): string {
  return template.replace('%d', value).replace('%s', value)
}

export class DiningText {
  static get dining_action_retry(): string { return resString('dining_action_retry') }
  static get dining_load_more(): string { return resString('dining_load_more') }
  static get dining_load_all_loaded(): string { return resString('dining_load_all_loaded') }
  static get dining_copy_failed(): string { return resString('dining_copy_failed') }
  static get dining_selected_none(): string { return resString('dining_selected_none') }

  static get dining_title(): string { return resString('dining_title') }
  static get dining_order_section_created(): string { return resString('dining_order_section_created') }
  static get dining_order_section_joined(): string { return resString('dining_order_section_joined') }
  static get dining_order_status_draft(): string { return resString('dining_order_status_draft') }
  static get dining_order_status_ongoing(): string { return resString('dining_order_status_ongoing') }
  static get dining_order_status_finished(): string { return resString('dining_order_status_finished') }
  static get dining_order_status_cancelled(): string { return resString('dining_order_status_cancelled') }
  static get dining_order_creator_badge(): string { return resString('dining_order_creator_badge') }
  static get dining_order_action_reuse(): string { return resString('dining_order_action_reuse') }
  static get dining_order_action_cancel(): string { return resString('dining_order_action_cancel') }
  static get dining_order_cancel_confirm_title(): string { return resString('dining_order_cancel_confirm_title') }
  static get dining_order_cancel_confirm_text(): string { return resString('dining_order_cancel_confirm_text') }
  static get dining_order_cancel_success(): string { return resString('dining_order_cancel_success') }
  static get dining_order_cancelled_hint(): string { return resString('dining_order_cancelled_hint') }
  static get dining_order_action_recipe_library(): string { return resString('dining_order_action_recipe_library') }
  static get dining_order_list_empty(): string { return resString('dining_order_list_empty') }
  static get dining_order_list_load_failed(): string { return resString('dining_order_list_load_failed') }

  static get dining_order_create_title(): string { return resString('dining_order_create_title') }
  static get dining_order_create_title_label(): string { return resString('dining_order_create_title_label') }
  static get dining_order_create_title_hint(): string { return resString('dining_order_create_title_hint') }
  static get dining_order_create_menu_label(): string { return resString('dining_order_create_menu_label') }
  static get dining_order_create_menu_uncategorized(): string { return resString('dining_order_create_menu_uncategorized') }
  static get dining_order_create_submit(): string { return resString('dining_order_create_submit') }
  static get dining_order_create_submit_success(): string { return resString('dining_order_create_submit_success') }
  static get dining_order_create_invite(): string { return resString('dining_order_create_invite') }
  static get dining_order_invite_copy_done(): string { return resString('dining_order_invite_copy_done') }
  static get dining_order_invite_unavailable(): string { return resString('dining_order_invite_unavailable') }
  static get dining_order_create_write_failed(): string { return resString('dining_order_create_write_failed') }
  static get dining_order_create_load_failed(): string { return resString('dining_order_create_load_failed') }
  static get dining_order_cart_title(): string { return resString('dining_order_cart_title') }
  static get dining_order_cart_empty(): string { return resString('dining_order_cart_empty') }
  static get dining_order_cart_remove(): string { return resString('dining_order_cart_remove') }

  /** 已选 %d 道 · %s 人（底栏左侧信息区） */
  static cartSummary(count: number, people: number): string {
    return resString('dining_order_cart_summary_format')
      .replace('%d', `${count}`).replace('%s', `${people}`)
  }

  /** 列表卡摘要：%d 道菜 · %s（对齐 Android dining_order_list_summary_format；无摘要时仅出菜品数） */
  static orderListSummary(menuCount: number, ingredientSummary: string): string {
    const summary = ingredientSummary.trim()
    if (summary.length > 0) {
      return resString('dining_order_list_summary_format')
        .replace('%d', `${menuCount}`).replace('%s', summary)
    }
    if (menuCount <= 0) {
      return ''
    }
    return fmt(resString('dining_order_list_dish_count_format'), `${menuCount}`)
  }
  static get dining_order_error_title_required(): string { return resString('dining_order_error_title_required') }
  static get dining_order_error_title_too_long(): string { return resString('dining_order_error_title_too_long') }
  static get dining_order_error_menu_empty(): string { return resString('dining_order_error_menu_empty') }
  static get dining_order_error_menu_too_many(): string { return resString('dining_order_error_menu_too_many') }
  static get dining_order_create_result_invalid(): string { return resString('dining_order_create_result_invalid') }

  /** 已选 %d 道菜 */
  static selectedCount(count: number): string {
    return fmt(resString('dining_order_create_selected_count_format'), `${count}`)
  }

  /** 将复用历史订单「%s」的 %d 道菜（菜单按原单复制，不可修改） */
  static reuseHint(title: string, count: number): string {
    const template = resString('dining_order_create_reuse_hint_format')
    return template.replace('%s', title).replace('%d', `${count}`)
  }

  /** %d 种食材 */
  static ingredientsCount(count: number): string {
    return fmt(resString('dining_recipe_ingredients_count_format'), `${count}`)
  }

  static get dining_order_share_title(): string { return resString('dining_order_share_title') }
  static get dining_order_invite_title(): string { return resString('dining_order_invite_title') }
  static get dining_order_share_qr_section(): string { return resString('dining_order_share_qr_section') }
  static get dining_order_share_qr_hint(): string { return resString('dining_order_share_qr_hint') }
  static get dining_order_share_copy_link(): string { return resString('dining_order_share_copy_link') }
  static get dining_order_share_copy_done(): string { return resString('dining_order_share_copy_done') }
  static get dining_order_share_reset_action(): string { return resString('dining_order_share_reset_action') }
  static get dining_order_share_reset_confirm_message(): string { return resString('dining_order_share_reset_confirm_message') }
  static get dining_order_share_reset_confirm(): string { return resString('dining_order_share_reset_confirm') }
  static get dining_order_share_reset_done(): string { return resString('dining_order_share_reset_done') }
  static get dining_order_share_qr_unavailable(): string { return resString('dining_order_share_qr_unavailable') }
  static get dining_order_share_detail_failed(): string { return resString('dining_order_share_detail_failed') }
  static get dining_order_share_qr_failed(): string { return resString('dining_order_share_qr_failed') }

  /** %d 道菜 */
  static menuCount(count: number): string {
    return fmt(resString('dining_order_share_menu_count_format'), `${count}`)
  }

  /** %d 人参与 */
  static memberCount(count: number): string {
    return fmt(resString('dining_order_share_member_count_format'), `${count}`)
  }

  static get dining_order_detail_title(): string { return resString('dining_order_detail_title') }
  static get dining_order_detail_share(): string { return resString('dining_order_detail_share') }
  static get dining_order_detail_draft_badge(): string { return resString('dining_order_detail_draft_badge') }
  static get dining_order_detail_draft_hint(): string { return resString('dining_order_detail_draft_hint') }
  static get dining_order_detail_add_dish(): string { return resString('dining_order_detail_add_dish') }
  static get dining_order_detail_draft_empty_menu(): string { return resString('dining_order_detail_draft_empty_menu') }
  static get dining_order_detail_uncategorized(): string { return resString('dining_order_detail_uncategorized') }
  static get dining_order_detail_empty_menu(): string { return resString('dining_order_detail_empty_menu') }
  static get dining_order_detail_note_action(): string { return resString('dining_order_detail_note_action') }
  static get dining_order_detail_recipe_action(): string { return resString('dining_order_detail_recipe_action') }
  static get dining_order_detail_note_title(): string { return resString('dining_order_detail_note_title') }
  static get dining_order_detail_note_save(): string { return resString('dining_order_detail_note_save') }
  static get dining_order_detail_note_hint(): string { return resString('dining_order_detail_note_hint') }
  static get dining_order_detail_bottom_hint(): string { return resString('dining_order_detail_bottom_hint') }
  static get dining_order_detail_finish(): string { return resString('dining_order_detail_finish') }
  static get dining_order_detail_finish_confirm_message(): string { return resString('dining_order_detail_finish_confirm_message') }
  static get dining_order_detail_view_shopping_list(): string { return resString('dining_order_detail_view_shopping_list') }
  static get dining_order_detail_finished_locked(): string { return resString('dining_order_detail_finished_locked') }
  static get dining_order_detail_write_failed(): string { return resString('dining_order_detail_write_failed') }
  static get dining_order_detail_load_failed(): string { return resString('dining_order_detail_load_failed') }
  static get dining_order_detail_syncing(): string { return resString('dining_order_detail_syncing') }
  static get dining_order_conflict_refresh(): string { return resString('dining_order_conflict_refresh') }

  /** 菜单共 %d 道菜 */
  static draftMenuCount(count: number): string {
    return fmt(resString('dining_order_detail_draft_menu_count_format'), `${count}`)
  }

  /** 移除菜品 %s（无障碍标签） */
  static removeDishLabel(name: string): string {
    return fmt(resString('dining_order_detail_remove_dish'), name)
  }

  /** 参与者 %d 人 */
  static participants(count: number): string {
    return fmt(resString('dining_order_detail_participants_format'), `${count}`)
  }

  /** 我的备注：%s */
  static myNote(note: string): string {
    return fmt(resString('dining_order_detail_my_note_format'), note)
  }

  /** 已选 %d 道 */
  static detailSelectedCount(count: number): string {
    return fmt(resString('dining_order_detail_selected_count_format'), `${count}`)
  }

  static get dining_shopping_list_title(): string { return resString('dining_shopping_list_title') }
  static get dining_shopping_list_tab_ingredients(): string { return resString('dining_shopping_list_tab_ingredients') }
  static get dining_shopping_list_tab_per_recipe(): string { return resString('dining_shopping_list_tab_per_recipe') }
  static get dining_shopping_list_ingredients_section(): string { return resString('dining_shopping_list_ingredients_section') }
  static get dining_shopping_list_copy(): string { return resString('dining_shopping_list_copy') }
  static get dining_shopping_list_copy_done(): string { return resString('dining_shopping_list_copy_done') }

  /** 食材清单 · 已买 %d/%s */
  static shoppingCheckedProgress(checked: number, total: number): string {
    const template = resString('dining_shopping_list_ingredients_progress_format')
    return template.replace('%d', `${checked}`).replace('%s', `${total}`)
  }
  static get dining_shopping_list_empty_ingredients(): string { return resString('dining_shopping_list_empty_ingredients') }
  static get dining_shopping_list_empty_per_recipe(): string { return resString('dining_shopping_list_empty_per_recipe') }
  static get dining_shopping_list_load_failed(): string { return resString('dining_shopping_list_load_failed') }

  /** 选菜人备注「：%s」 */
  static selectorNote(note: string): string {
    return fmt(resString('dining_shopping_list_selector_note_format'), note)
  }

  static get dining_order_join_title(): string { return resString('dining_order_join_title') }
  static get dining_order_join_unknown_title(): string { return resString('dining_order_join_unknown_title') }
  static get dining_order_join_action(): string { return resString('dining_order_join_action') }
  static get dining_order_join_order_finished(): string { return resString('dining_order_join_order_finished') }
  static get dining_order_join_order_cancelled(): string { return resString('dining_order_join_order_cancelled') }
  static get dining_order_join_load_failed(): string { return resString('dining_order_join_load_failed') }
  static get dining_order_join_invalid_token(): string { return resString('dining_order_join_invalid_token') }
  static get dining_order_join_success(): string { return resString('dining_order_join_success') }
  static get dining_order_join_param_invalid(): string { return resString('dining_order_join_param_invalid') }
  static get dining_order_join_hint(): string { return resString('dining_order_join_hint') }
  static get dining_order_join_draft_hint(): string { return resString('dining_order_join_draft_hint') }

  /** 发起者：%s */
  static joinCreator(name: string): string {
    return fmt(resString('dining_order_join_creator_format'), name)
  }

  /** %d 道菜 */
  static joinRecipeCount(count: number): string {
    return fmt(resString('dining_order_join_recipe_count_format'), `${count}`)
  }

  static get dining_recipe_library_title(): string { return resString('dining_recipe_library_title') }
  static get dining_recipe_detail_title(): string { return resString('dining_recipe_detail_title') }
  static get dining_recipe_create_title(): string { return resString('dining_recipe_create_title') }
  static get dining_category_all(): string { return resString('dining_category_all') }
  static get dining_recipe_load_failed(): string { return resString('dining_recipe_load_failed') }
  static get dining_category_load_failed(): string { return resString('dining_category_load_failed') }
  static get dining_recipe_list_empty(): string { return resString('dining_recipe_list_empty') }
  static get dining_recipe_list_empty_filtered(): string { return resString('dining_recipe_list_empty_filtered') }
  static get dining_recipe_action_create(): string { return resString('dining_recipe_action_create') }
  static get dining_recipe_detail_ingredients_section(): string { return resString('dining_recipe_detail_ingredients_section') }
  static get dining_recipe_detail_steps_section(): string { return resString('dining_recipe_detail_steps_section') }
  static get dining_recipe_detail_steps_empty(): string { return resString('dining_recipe_detail_steps_empty') }
  static get dining_recipe_detail_ingredients_empty(): string { return resString('dining_recipe_detail_ingredients_empty') }
  static get dining_recipe_detail_load_failed(): string { return resString('dining_recipe_detail_load_failed') }
  static get dining_create_name_label(): string { return resString('dining_create_name_label') }
  static get dining_create_name_hint(): string { return resString('dining_create_name_hint') }
  static get dining_create_category_label(): string { return resString('dining_create_category_label') }
  static get dining_create_category_uncategorized(): string { return resString('dining_create_category_uncategorized') }
  static get dining_create_ingredients_label(): string { return resString('dining_create_ingredients_label') }
  static get dining_create_ingredients_hint(): string { return resString('dining_create_ingredients_hint') }
  static get dining_create_ingredient_add(): string { return resString('dining_create_ingredient_add') }
  static get dining_create_ingredients_empty(): string { return resString('dining_create_ingredients_empty') }
  static get dining_create_steps_label(): string { return resString('dining_create_steps_label') }
  static get dining_create_steps_hint(): string { return resString('dining_create_steps_hint') }
  static get dining_create_cover_label(): string { return resString('dining_create_cover_label') }
  static get dining_create_cover_pick(): string { return resString('dining_create_cover_pick') }
  static get dining_create_cover_replace(): string { return resString('dining_create_cover_replace') }
  static get dining_create_cover_failed(): string { return resString('dining_create_cover_failed') }
  static get dining_create_submit(): string { return resString('dining_create_submit') }
  static get dining_create_submit_success(): string { return resString('dining_create_submit_success') }
  static get dining_form_error_name_required(): string { return resString('dining_form_error_name_required') }
  static get dining_form_error_name_too_long(): string { return resString('dining_form_error_name_too_long') }
  static get dining_form_error_ingredient_empty(): string { return resString('dining_form_error_ingredient_empty') }
  static get dining_form_error_ingredient_too_long(): string { return resString('dining_form_error_ingredient_too_long') }
  static get dining_form_error_ingredient_too_many(): string { return resString('dining_form_error_ingredient_too_many') }
  static get dining_form_error_steps_too_long(): string { return resString('dining_form_error_steps_too_long') }

  // ---- 营养口径（契约 §二；键名对齐 Android strings.xml 营养/购买清单热量组，估算口径）----

  static get dining_nutrition_calorie_unknown(): string { return resString('dining_nutrition_calorie_unknown') }
  static get dining_unit_kcal(): string { return resString('dining_unit_kcal') }

  static get dining_shopping_list_calorie_card_title(): string {
    return resString('dining_shopping_list_calorie_card_title')
  }
  static get dining_shopping_list_total_calories_unknown(): string {
    return resString('dining_shopping_list_total_calories_unknown')
  }

  /** ≈%s kcal/份（一份热量徽标，份热量口径见 DiningLogic.diningServingCalories）；null=热量未知 */
  static calorieBadge(calories: number | null): string | null {
    const formatted = formatDiningAmount(calories)
    if (formatted === null) {
      return null
    }
    return fmt(resString('dining_nutrition_calorie_badge_format'), formatted)
  }

  /** ≈%s（购买清单总热量大字值，单位 kcal 由调用方单独渲染） */
  static totalCaloriesValue(formatted: string): string {
    return fmt(resString('dining_shopping_list_total_calories_value_format'), formatted)
  }

  /** %d 道菜热量未知，未计入 */
  static calorieUnknownHint(count: number): string {
    return fmt(resString('dining_shopping_list_calorie_unknown_hint_format'), `${count}`)
  }

  /** %d 道菜（配料聚合行徽标） */
  static ingredientDishCount(count: number): string {
    return fmt(resString('dining_shopping_list_ingredient_dishes_format'), `${count}`)
  }

  /** 今日剩余 ≈ %s kcal（目标 - 本单总热量；负值由页面用 danger 色渲染） */
  static remainingCaloriesValue(formatted: string): string {
    return fmt(resString('dining_shopping_list_remaining_calories_format'), formatted)
  }

  /** 人均 ≈ %s kcal */
  static perCapitaValue(formatted: string): string {
    return fmt(resString('dining_shopping_list_per_capita_format'), formatted)
  }

  // ---- 食材反查搜索（契约 §2.4/§三.6：搜索框 hint + 模式 chips）----

  static get dining_recipe_search_hint(): string { return resString('dining_recipe_search_hint') }
  static get dining_recipe_search_mode_name(): string { return resString('dining_recipe_search_mode_name') }
  static get dining_recipe_search_mode_ingredient(): string { return resString('dining_recipe_search_mode_ingredient') }

  // ---- 收藏（契约 §2.3/§三.4）----

  static get dining_order_action_favorites(): string { return resString('dining_order_action_favorites') }
  static get dining_recipe_favorite(): string { return resString('dining_recipe_favorite') }
  static get dining_recipe_unfavorite(): string { return resString('dining_recipe_unfavorite') }
  static get dining_recipe_favorite_failed(): string { return resString('dining_recipe_favorite_failed') }
  static get dining_recipe_favorites_title(): string { return resString('dining_recipe_favorites_title') }
  static get dining_recipe_favorites_empty(): string { return resString('dining_recipe_favorites_empty') }
  static get dining_recipe_favorites_load_failed(): string { return resString('dining_recipe_favorites_load_failed') }

  /** 收藏 %s（无障碍标签） */
  static favoriteAddLabel(name: string): string {
    return fmt(resString('dining_recipe_action_favorite_add_format'), name)
  }

  /** 取消收藏 %s（无障碍标签） */
  static favoriteRemoveLabel(name: string): string {
    return fmt(resString('dining_recipe_action_favorite_remove_format'), name)
  }

  // ---- 忌口警示（契约 §三.1：选中菜 allergens ∩ 用户忌口；文案注明按已登记过敏原）----

  static get dining_allergen_warning_title(): string { return resString('dining_allergen_warning_title') }

  /** 含忌口：%s（%s）（忌口名，菜名） */
  static allergenWarningItem(allergenLabel: string, recipeName: string): string {
    return resString('dining_allergen_warning_item_format').replace('%s', allergenLabel).replace('%s', recipeName)
  }

  /** 忌口标签展示名映射（白名单外原样回显，对齐 Android diningAllergenLabel） */
  static allergenLabel(code: string): string {
    if (code === 'SEAFOOD') {
      return resString('dining_allergen_seafood')
    }
    if (code === 'NUT') {
      return resString('dining_allergen_nut')
    }
    if (code === 'GLUTEN') {
      return resString('dining_allergen_gluten')
    }
    if (code === 'DAIRY') {
      return resString('dining_allergen_dairy')
    }
    if (code === 'EGG') {
      return resString('dining_allergen_egg')
    }
    if (code === 'SPICY') {
      return resString('dining_allergen_spicy')
    }
    if (code === 'SOY') {
      return resString('dining_allergen_soy')
    }
    return code
  }

  // ---- 份量建议（契约 §2.5/§三.5：人均 vs 目标÷3 → 够/偏多/偏少；人数 1-20 本地态）----

  static get dining_portion_people_label(): string { return resString('dining_portion_people_label') }
  static get dining_portion_people_increase(): string { return resString('dining_portion_people_increase') }
  static get dining_portion_people_decrease(): string { return resString('dining_portion_people_decrease') }

  /** %d 人吃 · 人均 ≈%s kcal，够一餐 */
  static portionAdviceEnough(peopleCount: number, perCapitaText: string): string {
    return resString('dining_portion_advice_enough_format').replace('%d', `${peopleCount}`).replace('%s', perCapitaText)
  }

  /** %d 人吃 · 人均 ≈%s kcal，偏多，可以减 1 道 */
  static portionAdviceMore(peopleCount: number, perCapitaText: string): string {
    return resString('dining_portion_advice_more_format').replace('%d', `${peopleCount}`).replace('%s', perCapitaText)
  }

  /** %d 人吃 · 人均 ≈%s kcal，偏少，建议再加 1-2 道 */
  static portionAdviceLess(peopleCount: number, perCapitaText: string): string {
    return resString('dining_portion_advice_less_format').replace('%d', `${peopleCount}`).replace('%s', perCapitaText)
  }

  // ---- 购买清单人数 / 人均 / 今日剩余（契约 §三.3）----

  static get dining_shopping_list_people_label(): string { return resString('dining_shopping_list_people_label') }

  // ---- 饮食偏好设置页（契约 §三.2）----

  static get dietary_preference_title(): string { return resString('dietary_preference_title') }
  static get dietary_preference_allergens_label(): string { return resString('dietary_preference_allergens_label') }
  static get dietary_preference_allergens_hint(): string { return resString('dietary_preference_allergens_hint') }
  static get dietary_preference_calorie_label(): string { return resString('dietary_preference_calorie_label') }
  static get dietary_preference_sodium_label(): string { return resString('dietary_preference_sodium_label') }
  static get dietary_preference_calorie_increase(): string { return resString('dietary_preference_calorie_increase') }
  static get dietary_preference_calorie_decrease(): string { return resString('dietary_preference_calorie_decrease') }
  static get dietary_preference_sodium_increase(): string { return resString('dietary_preference_sodium_increase') }
  static get dietary_preference_sodium_decrease(): string { return resString('dietary_preference_sodium_decrease') }
  static get dietary_unit_kcal(): string { return resString('dietary_unit_kcal') }
  static get dietary_unit_milligram(): string { return resString('dietary_unit_milligram') }
  static get dietary_preference_save(): string { return resString('dietary_preference_save') }
  static get dietary_preference_save_success(): string { return resString('dietary_preference_save_success') }
  static get dietary_preference_load_failed(): string { return resString('dietary_preference_load_failed') }
  static get dietary_preference_retry(): string { return resString('dietary_preference_retry') }

  /** 设置页忌口 chip 展示名（白名单外原样回显） */
  static dietaryAllergenLabel(code: string): string {
    return DiningText.allergenLabel(code)
  }
}
