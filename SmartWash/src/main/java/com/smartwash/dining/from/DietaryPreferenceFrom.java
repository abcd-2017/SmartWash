package com.smartwash.dining.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户饮食偏好保存请求（PUT /web/auth/dining/dietary-preference，契约 §2.1）。
 * 三字段全部可选：null = 恢复默认（allergens null=清空忌口，目标 null=恢复默认值）。
 * 目标取值范围与 UI 契约 §三.2 的 stepper 区间一致（后端兜底，防脏数据）。
 */
@Data
public class DietaryPreferenceFrom {

    /** 忌口标签集合（SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY），null/空=无忌口 */
    @Size(max = 7, message = "忌口标签最多 7 项")
    private List<@NotBlank(message = "忌口标签不能为空")
            @Pattern(regexp = "SEAFOOD|NUT|GLUTEN|DAIRY|EGG|SOY|SPICY",
                    message = "忌口标签仅支持 SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY")
            String> allergens;

    /** 每日热量目标 kcal，null=恢复默认（1200-4000，与 UI stepper 一致） */
    @Min(value = 1200, message = "热量目标需在 1200-4000 kcal 之间")
    @Max(value = 4000, message = "热量目标需在 1200-4000 kcal 之间")
    private Integer calorieTarget;

    /** 每日钠目标 mg，null=恢复默认（1000-4000，与 UI stepper 一致） */
    @Min(value = 1000, message = "钠目标需在 1000-4000 mg 之间")
    @Max(value = 4000, message = "钠目标需在 1000-4000 mg 之间")
    private Integer sodiumTarget;
}
