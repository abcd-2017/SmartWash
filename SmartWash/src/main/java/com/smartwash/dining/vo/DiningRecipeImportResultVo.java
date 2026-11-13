package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 菜谱批量导入结果报告（POST /admin/dining/recipes/import/confirm 响应）。
 * 整批事务语义：确认阶段逐行校验存在任一失败时<b>全批不落库</b>（rolledBack=true，
 * 全部行按失败回显），杜绝半批状态；全部通过才逐行插入并返回逐行 recipeId。
 */
@Data
@Accessors(chain = true)
public class DiningRecipeImportResultVo {

    /** 本次提交行数 */
    private int total;

    /** 成功写入行数（整批回滚时恒为 0） */
    private int successCount;

    /** 失败行数 */
    private int failedCount;

    /** 是否整批回滚（任一行失败即全批不落库） */
    private boolean rolledBack;

    /** 逐行结果明细（按行号升序） */
    private List<ResultRow> rows;

    /** 单行结果条目 */
    @Data
    @Accessors(chain = true)
    public static class ResultRow {

        /** 原文件行号 */
        private Integer rowNo;

        /** 菜名 */
        private String name;

        /** 该行是否成功写入 */
        private boolean success;

        /** 失败原因（成功行为 null） */
        private String error;

        /** 写入后的菜谱 ID（失败/回滚行为 null） */
        private Long recipeId;
    }
}
