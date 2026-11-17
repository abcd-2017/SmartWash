package com.smartwash.dining.support;

import com.smartwash.dining.entity.DiningIngredient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 配料过敏原启发预判（契约 §2.2）。
 *
 * <p>按名关键词表预判，命中即返回对应标签，未命中返回 null（NULL=无）。
 * 规则按契约顺序优先级排列（海鲜 → 坚果 → 麸质 → 乳制品 → 蛋 → 大豆 → 辛辣），
 * 一名多命中时取靠前者（如「海鲜酱」命中 SEAFOOD 而非豆酱类）。</p>
 *
 * <p>放置位置决策：放在 dining/support 支撑侧而非导入服务内部——
 * ① CSV 导入自动注册配料、管理端新增配料两条注册链路共用同一张命中表；
 * ② 纯函数无依赖，单测直接断言命中表，导入/服务侧只验证「调用并落库」。</p>
 *
 * <p>已知局限（契约 §五.3）：生僻配料（如「天贝」「纳豆」以外的发酵品）可能未命中返回 NULL，
 * UI 文案需注明「按已登记过敏原」，后台可人工补录。</p>
 */
public final class IngredientAllergenResolver {

    /** 规则表：标签 → 关键词正则（按契约顺序，先命中先返回） */
    private static final Map<String, Pattern> RULES = new LinkedHashMap<>();

    static {
        RULES.put(DiningIngredient.ALLERGEN_SEAFOOD, Pattern.compile("鱼|虾|蟹|贝|海鲜|鱿"));
        RULES.put(DiningIngredient.ALLERGEN_NUT, Pattern.compile("花生|坚果|杏仁|核桃|腰果|榛"));
        RULES.put(DiningIngredient.ALLERGEN_GLUTEN, Pattern.compile("面|麸|面包"));
        RULES.put(DiningIngredient.ALLERGEN_DAIRY, Pattern.compile("奶|乳|酪|芝士"));
        RULES.put(DiningIngredient.ALLERGEN_EGG, Pattern.compile("蛋"));
        RULES.put(DiningIngredient.ALLERGEN_SOY, Pattern.compile("豆|酱油|豉|味噌"));
        RULES.put(DiningIngredient.ALLERGEN_SPICY, Pattern.compile("辣|椒|芥末"));
    }

    private IngredientAllergenResolver() {
    }

    /**
     * 按配料名预判过敏原。
     *
     * @param name 配料名，null/空白返回 null
     * @return 过敏原标签（{@link DiningIngredient#ALLERGEN_VALUES} 之一），未命中返回 null
     */
    public static String resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Map.Entry<String, Pattern> rule : RULES.entrySet()) {
            if (rule.getValue().matcher(name).find()) {
                return rule.getKey();
            }
        }
        return null;
    }
}
