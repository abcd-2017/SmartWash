package com.smartwash.divination.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 卦例详情视图（含盘面 JSON 与最近一次解读）。
 */
@Data
public class RecordDetailVo {

    private Long id;

    private String method;

    private String category;

    private String question;

    private String castMethod;

    private LocalDateTime castAt;

    private Integer tzOffset;

    private String source;

    private String lines;

    private String clientChart;

    private String serverChart;

    private Integer chartVerified;

    /** 最近一次解读 */
    private InterpretationVo latestInterpretation;

    private LocalDateTime createdAt;
}
