package com.smartwash.dining.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 吃啥-配料别名（异名归一到规范配料）。
 *
 * <p>三方菜谱 CSV 存在同物异写（食盐/食用盐/盐、番茄/西红柿），若各自注册会令配料库膨胀、
 * 买菜清单同物多行。别名表把「原始写法」映射到 {@link DiningIngredient} 的规范名：
 * 导入/建菜谱解析食材名时先按名精确命中，未命中再查本表归一到规范配料 ID，仍未命中才新建。</p>
 *
 * <p>数据由 {@code static/dining/cleaned/ingredient_alias_map.csv} 清洗产出、人工维护；
 * 别名指向的规范配料被删除时别名失效（匹配阶段按存在性过滤，不级联删别名）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_ingredient_alias")
public class DiningIngredientAlias implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 别名（原始写法，唯一 uk_alias_name） */
    private String aliasName;

    /** 规范配料 ID（dining_ingredient.id） */
    private Long ingredientId;

    private LocalDateTime createdAt;
}
