package com.smartwash.memo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 速记视图对象（列表/单条返回体）。
 * tags 出参为标签数组（存储层为逗号分隔字符串，拆分后返回）。
 */
@Data
@Accessors(chain = true)
public class MemoVo {

    private Long id;

    /** 手账正文 */
    private String content;

    /** 标签数组（存储层逗号分隔拆分而来，无标签为空数组） */
    private List<String> tags;

    /** 是否置顶 */
    private Boolean pinned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
