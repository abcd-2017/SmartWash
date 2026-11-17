package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 用户饮食偏好视图对象（契约 §2.1）。
 * 字段 null = 默认值语义（端上兜底 2000kcal / 2000mg）；allergens 空列表 = 无忌口。
 */
@Data
@Accessors(chain = true)
public class DietaryPreferenceVo {

    /** 忌口标签（SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY），无则空列表 */
    private List<String> allergens;

    /** 每日热量目标 kcal，null=默认 2000 */
    private Integer calorieTarget;

    /** 每日钠目标 mg，null=默认 2000 */
    private Integer sodiumTarget;
}
