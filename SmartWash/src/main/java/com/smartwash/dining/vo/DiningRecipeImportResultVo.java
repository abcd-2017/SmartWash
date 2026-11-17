package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 菜谱批量导入结果报告（POST /admin/dining/recipes/import/confirm 响应）。
 * 整批事务语义：确认阶段逐行校验存在任一失败时<b>全批不落库</b>（rolledBack=true，
 * 全部行按失败回显），杜绝半批状态；全部通过才逐行写入（命中已有菜谱按 source_key/（菜名,分类）
 * 幂等更新，未命中新增）并返回逐行 recipeId 与新增/更新标注。
 */
@Data
@Accessors(chain = true)
public class DiningRecipeImportResultVo {

    private int total;

    /** 成功写入行数（整批回滚时恒为 0） */
    private int successCount;

    private int failedCount;

    /** 其中新增行数（命中库内已有菜谱的行不算） */
    private int createdCount;

    /** 其中更新行数（source_key/（菜名,分类）命中已有菜谱的幂等更新） */
    private int updatedCount;

    /** 是否整批回滚（任一行失败即全批不落库） */
    private boolean rolledBack;

    /** 逐行结果明细（按行号升序） */
    private List<ResultRow> rows;

    @Data
    @Accessors(chain = true)
    public static class ResultRow {

        private Integer rowNo;

        private String name;

        /** 该行是否成功写入 */
        private boolean success;

        /** 失败原因（成功行为 null） */
        private String error;

        /** 写入后的菜谱 ID（失败/回滚行为 null；更新行回显既有菜谱 ID） */
        private Long recipeId;

        /** 是否新增（true=新增 / false=更新既有菜谱；失败/回滚行为 null） */
        private Boolean isNew;

        /** 判重结果类型：CREATE=新增 / UPDATE=更新（对齐 DiningRecipeImportService 常量），失败/回滚行为 null */
        private String existsKind;

        /** 行级警告（不阻断导入：封面对象键存储缺失/分类自动创建等），按行回显 */
        private List<String> warnings;
    }
}
