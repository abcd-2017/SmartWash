package com.smartwash.dining.from;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜谱批量导入确认请求（管理端 POST /admin/dining/recipes/import/confirm）。
 *
 * <p>两阶段导入的第二阶段：预览接口（POST /import）解析上传文件并逐行校验、不落库，
 * 返回归一后的行数据；运营在 Web 端确认预览结果后，把通过校验的行原样提交到本接口。
 * 服务端在整批事务内再次逐行校验（防绕过预览直接提交）：任一行失败则全批不落库，
 * 并返回逐行失败报告；全部通过才逐行插入并返回逐行成功报告。</p>
 *
 * <p>v2 扩展（契约 §3.2，全部可选、向后兼容）：食材明细列表 / 封面对象键 / 营养 6 列；
 * 分类不存在时服务端自动创建（预览阶段按 warning 标注，不再是行级错误）。</p>
 */
@Data
@Accessors(chain = true)
public class DiningRecipeImportConfirmFrom {

    /** 待导入行（上限 1000，与预览解析上限一致，防超大请求） */
    @Valid
    @NotEmpty(message = "导入行列表不能为空")
    @Size(max = 1000, message = "单次最多导入 1000 行")
    private List<Row> rows;

    /** 单行导入条目：字段与预览行对齐（封面链接不支持批量导入，Web 端导入后单独上传） */
    @Data
    @Accessors(chain = true)
    public static class Row {

        /** 原文件行号（表头记 1，首个数据行记 2；逐行报告按此回显定位） */
        @NotNull(message = "行号不能为空")
        private Integer rowNo;

        /** 分类名：精确匹配，不存在时确认导入自动创建（sort 递增）；空=未分类 */
        @Size(max = 32, message = "分类名过长（上限32字符）")
        private String categoryName;

        @NotBlank(message = "菜名不能为空")
        @Size(max = 64, message = "菜名过长（上限64字符）")
        private String name;

        /** 食材名数组（预览阶段已按分隔符切分归一；保留双写，聚合主数据源为关联表） */
        @Size(max = 100, message = "食材数量过多（上限100项）")
        private List<@Size(max = 32, message = "单个食材名过长（上限32字符）") String> ingredients;

        /** 食材明细（预览阶段已按 `名称:数量单位` 解析归一；数量可空=仅名字关联），与 ingredients 按名去重合并写关联表 */
        @Valid
        @Size(max = 100, message = "食材明细数量过多（上限100项）")
        private List<IngredientDetail> ingredientDetails;

        /** 做法步骤（纯文本，可空——酱料配方/瓶装饮品无做法步骤，仅校验长度上限） */
        @Size(max = 20000, message = "步骤内容过长（上限20000字符）")
        private String steps;

        /** 封面对象键（MinIO dining/covers/ 前缀；存储中不存在时该行仅告警、封面不落库） */
        @Size(max = 255, message = "封面对象键过长（上限255字符）")
        private String coverObjectKey;

        /** 热量 Kcal/100g（可空） */
        private BigDecimal caloriesPer100g;

        /** 整份估算热量 Kcal（可空） */
        private BigDecimal caloriesTotal;

        /** 蛋白质 g/100g（可空） */
        private BigDecimal proteinG;

        /** 脂肪 g/100g（可空） */
        private BigDecimal fatG;

        /** 碳水化合物 g/100g（可空） */
        private BigDecimal carbsG;

        /** 钠 mg/100g（可空） */
        private BigDecimal sodiumMg;

        /** 唯一标识（可空，模板「唯一标识」列）：非空按 key 幂等判重（命中更新/未命中兜底匹配），
         * 同批重复 key 为行级错误；旧 CSV 无此列不受影响 */
        @Size(max = 64, message = "唯一标识过长（上限64字符）")
        private String sourceKey;
    }

    /** 配料明细条目：`名称:数量单位` 解析结果（数量可空 = 来源未给出，如「适量」） */
    @Data
    @Accessors(chain = true)
    public static class IngredientDetail {

        @NotBlank(message = "食材明细配料名不能为空")
        @Size(max = 32, message = "食材明细配料名过长（上限32字符）")
        private String name;

        /** 数量数值（如 575g → 575），可空 */
        @DecimalMin(value = "0", message = "食材明细数量不能为负数")
        private BigDecimal amountValue;

        /** 单位（g/kg/ml/L/个/根/片/包/勺/瓣/条/块/只/盒/袋/斤/两），可空 */
        @Size(max = 16, message = "食材明细单位过长（上限16字符）")
        private String amountUnit;
    }
}
