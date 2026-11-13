package com.smartwash.dining.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 菜谱批量导入预览结果（POST /admin/dining/recipes/import 响应）。
 * 解析上传文件（xlsx/CSV）并逐行校验，<b>不落库</b>——运营在 Web 端确认预览结果后，
 * 把通过校验的行原样提交到确认接口（POST /import/confirm）才真正写入。
 * errorCount &gt; 0 时前端阻断提交并展示行级错误。
 */
@Data
@Accessors(chain = true)
public class DiningRecipeImportPreviewVo {

    /** 数据行总数（不含表头） */
    private int total;

    /** 通过校验、可提交导入的行数 */
    private int validCount;

    /** 校验失败行数 */
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

        /** 匹配到的分类 ID（分类不存在时为 null，errors 中给出行级错误） */
        private Long categoryId;

        /** 菜名 */
        private String name;

        /** 食材名数组（按模板分隔符切分、去空白后的归一结果） */
        private List<String> ingredients;

        /** 做法步骤 */
        private String steps;

        /** 封面链接原文（当前版本仅校验格式不落库，封面走 Web 端单独上传） */
        private String coverUrl;

        /** 行级错误（空列表 = 该行通过校验） */
        private List<String> errors;
    }
}
