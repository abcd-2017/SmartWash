// 吃啥（dining）域文案表 — 键名对齐 Android feature/dining/impl/src/main/res/values/strings.xml
// 的 dining_* 资源，便于跨端文案核对；取值走 resString（string.json / StringResources /
// StringFallback 三处同源维护）。
//
// 场景人称统一「同学」，显示名统一「吃啥」「点单」（不得出现「家庭点单」「家人」）。

import { resString } from '../../utils/ResourceString'

/** 占位格式化：%d / %s 各替换一次（文案表格式串专用，不做多参数位置映射） */
function fmt(template: string, value: string): string {
  return template.replace('%d', value).replace('%s', value)
}

export class DiningText {
  // 通用
  static get dining_action_retry(): string { return resString('dining_action_retry') }
  static get dining_load_more(): string { return resString('dining_load_more') }
  static get dining_load_all_loaded(): string { return resString('dining_load_all_loaded') }
  static get dining_copy_failed(): string { return resString('dining_copy_failed') }
  static get dining_selected_none(): string { return resString('dining_selected_none') }

  // 订单列表（域首页）
  static get dining_title(): string { return resString('dining_title') }
  static get dining_order_section_created(): string { return resString('dining_order_section_created') }
  static get dining_order_section_joined(): string { return resString('dining_order_section_joined') }
  static get dining_order_status_ongoing(): string { return resString('dining_order_status_ongoing') }
  static get dining_order_status_finished(): string { return resString('dining_order_status_finished') }
  static get dining_order_creator_badge(): string { return resString('dining_order_creator_badge') }
  static get dining_order_action_reuse(): string { return resString('dining_order_action_reuse') }
  static get dining_order_action_recipe_library(): string { return resString('dining_order_action_recipe_library') }
  static get dining_order_list_empty(): string { return resString('dining_order_list_empty') }
  static get dining_order_list_load_failed(): string { return resString('dining_order_list_load_failed') }

  // 创建订单
  static get dining_order_create_title(): string { return resString('dining_order_create_title') }
  static get dining_order_create_title_label(): string { return resString('dining_order_create_title_label') }
  static get dining_order_create_title_hint(): string { return resString('dining_order_create_title_hint') }
  static get dining_order_create_menu_label(): string { return resString('dining_order_create_menu_label') }
  static get dining_order_create_menu_uncategorized(): string { return resString('dining_order_create_menu_uncategorized') }
  static get dining_order_create_submit(): string { return resString('dining_order_create_submit') }
  static get dining_order_create_submit_success(): string { return resString('dining_order_create_submit_success') }
  static get dining_order_create_load_failed(): string { return resString('dining_order_create_load_failed') }
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

  // 分享视图
  static get dining_order_share_title(): string { return resString('dining_order_share_title') }
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

  // 点单页
  static get dining_order_detail_title(): string { return resString('dining_order_detail_title') }
  static get dining_order_detail_share(): string { return resString('dining_order_detail_share') }
  static get dining_order_detail_uncategorized(): string { return resString('dining_order_detail_uncategorized') }
  static get dining_order_detail_empty_menu(): string { return resString('dining_order_detail_empty_menu') }
  static get dining_order_detail_note_action(): string { return resString('dining_order_detail_note_action') }
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

  // 购买清单
  static get dining_shopping_list_title(): string { return resString('dining_shopping_list_title') }
  static get dining_shopping_list_tab_ingredients(): string { return resString('dining_shopping_list_tab_ingredients') }
  static get dining_shopping_list_tab_per_recipe(): string { return resString('dining_shopping_list_tab_per_recipe') }
  static get dining_shopping_list_ingredients_section(): string { return resString('dining_shopping_list_ingredients_section') }
  static get dining_shopping_list_empty_ingredients(): string { return resString('dining_shopping_list_empty_ingredients') }
  static get dining_shopping_list_empty_per_recipe(): string { return resString('dining_shopping_list_empty_per_recipe') }
  static get dining_shopping_list_load_failed(): string { return resString('dining_shopping_list_load_failed') }

  /** 选菜人备注「：%s」 */
  static selectorNote(note: string): string {
    return fmt(resString('dining_shopping_list_selector_note_format'), note)
  }

  // 加入入口
  static get dining_order_join_title(): string { return resString('dining_order_join_title') }
  static get dining_order_join_unknown_title(): string { return resString('dining_order_join_unknown_title') }
  static get dining_order_join_action(): string { return resString('dining_order_join_action') }
  static get dining_order_join_order_finished(): string { return resString('dining_order_join_order_finished') }
  static get dining_order_join_load_failed(): string { return resString('dining_order_join_load_failed') }
  static get dining_order_join_invalid_token(): string { return resString('dining_order_join_invalid_token') }
  static get dining_order_join_success(): string { return resString('dining_order_join_success') }
  static get dining_order_join_param_invalid(): string { return resString('dining_order_join_param_invalid') }

  /** 发起者：%s */
  static joinCreator(name: string): string {
    return fmt(resString('dining_order_join_creator_format'), name)
  }

  /** %d 道菜 */
  static joinRecipeCount(count: number): string {
    return fmt(resString('dining_order_join_recipe_count_format'), `${count}`)
  }

  // 菜谱三页
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
}
