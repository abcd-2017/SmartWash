package com.smartwash.dining.from;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新配料请求 DTO（管理端 PUT /admin/dining/ingredient/{id}，id 走路径变量）。
 * 字段不传（null）即不修改；改名与其他配料重名时拒绝（409）。
 */
@Data
public class UpdateDiningIngredientFrom {

    /** 配料名，不传即不修改 */
    @Size(max = 32, message = "配料名称过长（上限32字符）")
    private String name;

    /** 配料分类，不传即不修改 */
    @Pattern(regexp = "MAIN|AUX|SEASONING|OTHER", message = "配料分类仅支持 MAIN/AUX/SEASONING/OTHER")
    private String category;

    /**
     * 过敏原，不传（null）即不修改；传空串=清空为 NULL（「无」）。
     * 合法值：SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY/OTHER
     */
    @Pattern(regexp = "(SEAFOOD|NUT|GLUTEN|DAIRY|EGG|SOY|SPICY|OTHER)?",
            message = "过敏原仅支持 SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY/OTHER")
    private String allergen;
}
