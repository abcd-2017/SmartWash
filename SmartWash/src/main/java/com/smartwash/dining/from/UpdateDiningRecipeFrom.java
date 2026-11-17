package com.smartwash.dining.from;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端修改菜谱请求 DTO（全量字段语义：除 id 外的字段不传即不修改，与 MyBatis-Plus
 * updateById 跳过 null 列的默认行为对齐；上下架走专用状态接口，不在本接口表达）。
 */
@Data
public class UpdateDiningRecipeFrom {

    @NotNull(message = "菜谱ID不能为空")
    private Long id;

    /** 菜名，null=不修改 */
    @Size(max = 64, message = "菜名过长（上限64字符）")
    private String name;

    /** 所属分类 ID：null=不修改；0=改回未分类（归一为 null 落库）；&gt;0=改到指定分类（后端校验存在） */
    private Long categoryId;

    /** 食材名数组，null=不修改 */
    @Size(max = 100, message = "食材数量过多（上限100项）")
    private List<@Size(max = 32, message = "单个食材名过长（上限32字符）") String> ingredients;

    /** 做法步骤，null=不修改 */
    @Size(max = 20000, message = "步骤内容过长（上限20000字符）")
    private String steps;

    /** 封面对象键（封面上传接口返回的新键），null=不修改 */
    @Size(max = 255, message = "封面对象键过长")
    private String coverObjectKey;

    /**
     * 营养成分（契约 §3.2，与导入器 NUTRITION_MAX_* 上限对齐；null=不修改，
     * 对齐本 DTO 全量字段语义——清空营养值暂不支持，需通过批量导入覆盖）。
     */
    /** 热量 Kcal/100g，null=不修改 */
    @DecimalMin(value = "0", message = "热量不能为负数")
    @DecimalMax(value = "999999.99", message = "热量数值过大")
    private BigDecimal caloriesPer100g;

    /** 整份估算热量 Kcal，null=不修改 */
    @DecimalMin(value = "0", message = "整份热量不能为负数")
    @DecimalMax(value = "99999999.99", message = "整份热量数值过大")
    private BigDecimal caloriesTotal;

    /** 蛋白质 g/100g，null=不修改 */
    @DecimalMin(value = "0", message = "蛋白质不能为负数")
    @DecimalMax(value = "9999.99", message = "蛋白质数值过大")
    private BigDecimal proteinG;

    /** 脂肪 g/100g，null=不修改 */
    @DecimalMin(value = "0", message = "脂肪不能为负数")
    @DecimalMax(value = "9999.99", message = "脂肪数值过大")
    private BigDecimal fatG;

    /** 碳水化合物 g/100g，null=不修改 */
    @DecimalMin(value = "0", message = "碳水化合物不能为负数")
    @DecimalMax(value = "9999.99", message = "碳水化合物数值过大")
    private BigDecimal carbsG;

    /** 钠 mg/100g，null=不修改 */
    @DecimalMin(value = "0", message = "钠不能为负数")
    @DecimalMax(value = "999999.99", message = "钠数值过大")
    private BigDecimal sodiumMg;

    /**
     * 来源唯一标识（可选），null=不修改；按 sourceKey 幂等保护：
     * 传值时若同 key 已挂在其他菜谱（不同 id）上则报冲突，本接口不支持清空标识。
     */
    @Size(max = 64, message = "唯一标识过长（上限64字符）")
    private String sourceKey;
}
