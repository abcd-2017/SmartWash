package com.smartwash.dining.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户饮食偏好（忌口 + 每日目标，契约 §2.1）。
 * 一人一行：user_id 为主键（非自增，取当前登录用户 ID）。
 * 字段 NULL = 默认值语义（端上兜底）：热量默认 2000kcal、钠默认 2000mg、allergens 空 = 无忌口。
 * 契约 §五.1 决策：表按 dining 域落地（避免跨域改 user 模块建表纪律），user_id 关联 users.user_id。
 *
 * <p>updateStrategy=IGNORED：目标恢复默认（null）与忌口清空（null）必须把 NULL 写回库，
 * 不被全局 update-strategy=not_empty 跳过。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_dietary_preference")
public class DietaryPreference implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 每日热量默认目标（kcal，端上兜底口径） */
    public static final Integer DEFAULT_CALORIE_TARGET = 2000;

    /** 每日钠默认目标（mg，端上兜底口径） */
    public static final Integer DEFAULT_SODIUM_TARGET = 2000;

    /** 用户可选忌口标签合法值（契约 §2.1，不含配料库专用的 OTHER） */
    public static final List<String> ALLERGEN_VALUES = List.of(
            DiningIngredient.ALLERGEN_SEAFOOD, DiningIngredient.ALLERGEN_NUT,
            DiningIngredient.ALLERGEN_GLUTEN, DiningIngredient.ALLERGEN_DAIRY,
            DiningIngredient.ALLERGEN_EGG, DiningIngredient.ALLERGEN_SOY,
            DiningIngredient.ALLERGEN_SPICY);

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 忌口标签 JSON 数组文本（如 ["NUT","SEAFOOD"]），NULL=无忌口 */
    @TableField(value = "allergens", updateStrategy = FieldStrategy.IGNORED)
    private String allergens;

    /** 每日热量目标 kcal，NULL=默认 2000 */
    @TableField(value = "calorie_target", updateStrategy = FieldStrategy.IGNORED)
    private Integer calorieTarget;

    /** 每日钠目标 mg，NULL=默认 2000 */
    @TableField(value = "sodium_target", updateStrategy = FieldStrategy.IGNORED)
    private Integer sodiumTarget;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
