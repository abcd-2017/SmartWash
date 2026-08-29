package com.smartwash.divination.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 卦例视图（卦历列表/详情返回）。
 */
@Data
public class RecordVo {

    private Long id;

    private String method;

    private String category;

    private String question;

    private String castMethod;

    private LocalDateTime castAt;

    private Integer tzOffset;

    private String source;

    /** 端上/服务端盘面一致 */
    private Integer chartVerified;

    /** 最近一次解读摘要（详情时填充） */
    private String latestInterpretation;

    private LocalDateTime createdAt;
}
