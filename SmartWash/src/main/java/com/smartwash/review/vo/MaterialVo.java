package com.smartwash.review.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 复习资料视图（资料列表返回）。
 */
@Data
public class MaterialVo {

    private Long id;

    private String title;

    /** 原文摘要（列表截断，详情不含此字段区分） */
    private String rawText;

    private LocalDateTime createdAt;
}
