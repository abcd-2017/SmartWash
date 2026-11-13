package com.smartwash.dining.from;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 菜谱批量导入确认请求（管理端 POST /admin/dining/recipes/import/confirm）。
 *
 * <p>两阶段导入的第二阶段：预览接口（POST /import）解析上传文件并逐行校验、不落库，
 * 返回归一后的行数据；运营在 Web 端确认预览结果后，把通过校验的行原样提交到本接口。
 * 服务端在整批事务内再次逐行校验（防绕过预览直接提交）：任一行失败则全批不落库，
 * 并返回逐行失败报告；全部通过才逐行插入并返回逐行成功报告。</p>
 */
@Data
@Accessors(chain = true)
public class DiningRecipeImportConfirmFrom {

    /** 待导入行（上限 500，与预览解析上限一致，防超大请求） */
    @Valid
    @NotEmpty(message = "导入行列表不能为空")
    @Size(max = 500, message = "单次最多导入 500 行")
    private List<Row> rows;

    /** 单行导入条目：字段与预览行对齐（封面不支持批量导入，Web 端导入后单独上传） */
    @Data
    @Accessors(chain = true)
    public static class Row {

        /** 原文件行号（表头记 1，首个数据行记 2；逐行报告按此回显定位） */
        @NotNull(message = "行号不能为空")
        private Integer rowNo;

        /** 分类名：精确匹配已有分类，空=未分类；不存在的分类整批拒绝（预览阶段即行级报错） */
        @Size(max = 32, message = "分类名过长（上限32字符）")
        private String categoryName;

        /** 菜名 */
        @NotBlank(message = "菜名不能为空")
        @Size(max = 64, message = "菜名过长（上限64字符）")
        private String name;

        /** 食材名数组（预览阶段已按分隔符切分归一） */
        @Size(max = 100, message = "食材数量过多（上限100项）")
        private List<@Size(max = 32, message = "单个食材名过长（上限32字符）") String> ingredients;

        /** 做法步骤（纯文本） */
        @Size(max = 20000, message = "步骤内容过长（上限20000字符）")
        private String steps;
    }
}
