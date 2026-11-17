package com.smartwash.dining.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 吃啥-菜谱配料关联。
 * 菜谱 ↔ 配料多对多关联（带数量/单位），买菜清单聚合（ShoppingListAssembler v2）的主数据源；
 * uk_recipe_ingredient 保证一菜一配料仅一行（导入时食材名列表与明细列表按名去重合并）。
 * amount_value/amount_unit 可空 = 来源未给出数量（如「适量/若干」），仅参与名并集、不参与数量求和。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_recipe_ingredient")
public class DiningRecipeIngredient implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 菜谱 ID（dining_recipe.id） */
    private Long recipeId;

    /** 配料 ID（dining_ingredient.id） */
    private Long ingredientId;

    /** 数量数值（如 575g → 575），NULL=来源未给出 */
    private BigDecimal amountValue;

    /** 单位（g/kg/ml/L/个/根/片/包/勺/瓣/条/块/只/盒/袋/斤/两），NULL=未给出 */
    private String amountUnit;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
