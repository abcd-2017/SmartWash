package com.smartwash.memo.from;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新速记请求 DTO（patch 语义：字段为 null 表示不更新该项）。
 */
@Data
public class UpdateMemoFrom {

    /** 手账正文；传了才更新，且 trim 后不得为空 */
    @Size(max = 2000, message = "内容过长（上限2000字符）")
    private String content;

    /** 标签数组；传了才更新（空数组 = 清空标签） */
    private List<String> tags;

    /** 置顶标记；传了才更新 */
    private Boolean pinned;
}
