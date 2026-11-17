package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜谱批量导入预览结果（POST /admin/dining/recipes/import 响应）。
 * 解析上传文件（xlsx/CSV）并逐行校验，<b>不落库</b>——运营在 Web 端确认预览结果后，
 * 把通过校验的行原样提交到确认接口（POST /import/confirm）才真正写入。
 * errorCount &gt; 0 时前端阻断提交并展示行级错误。
 *
 * <p>v2 扩展（契约 §3.2）：PreviewRow 增加配料明细/封面对象键/营养 6 列的解析结果，
 * 以及行级 warnings（分类自动创建、封面对象键存储缺失、明细解析失败等——不阻断导入）。</p>
 */
@Data
@Accessors(chain = true)
public class DiningRecipeImportPreviewVo {

    /** 数据行总数（不含表头） */
    private int total;

    /** 通过校验、可提交导入的行数 */
    private int validCount;

    private int errorCount;

    /** 逐行预览明细（按行号升序，含失败行的错误原因） */
    private List<PreviewRow> rows;

    /** 单行预览条目：字段为归一后的结构化数据，确认接口按此契约回传 */
    @Data
    @Accessors(chain = true)
    public static class PreviewRow {

        /** 原文件行号（表头记 1，首个数据行记 2；与 Excel 打开看到的行号一致，便于定位） */
        private Integer rowNo;

        /** 分类名原文（空=未分类） */
        private String categoryName;

        /** 匹配到的分类 ID（分类不存在时为 null——确认导入时自动创建，warnings 中标注） */
        private Long categoryId;

        private String name;

        /** 食材名数组（按模板分隔符切分、去空白后的归一结果；保留双写，聚合主数据源为关联表） */
        private List<String> ingredients;

        /** 食材明细解析结果（`名称:数量单位` 顿号分隔；数量可空=仅名字关联；解析失败项进 warnings） */
        private List<IngredientDetail> ingredientDetails;

        private String steps;

        /** 封面链接原文（当前版本仅校验格式不落库，封面走 Web 端单独上传） */
        private String coverUrl;

        /** 封面对象键原文（dining/covers/ 前缀；存储校验缺失时进 warnings 且确认导入不落库） */
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

        /** 行级错误（空列表 = 该行通过校验；任一错误确认导入即全批回滚） */
        private List<String> errors;

        /** 行级警告（不阻断导入：分类自动创建/封面对象键存储缺失/明细解析失败等） */
        private List<String> warnings;
    }

    /** 配料明细条目（预览与确认行契约同形，字段名对齐 DiningRecipeImportConfirmFrom.IngredientDetail） */
    @Data
    @Accessors(chain = true)
    public static class IngredientDetail {

        private String name;

        /** 数量数值（如 575g → 575），可空 */
        private BigDecimal amountValue;

        /** 单位（g/kg/ml/L/个/根/…），可空 */
        private String amountUnit;
    }
}
