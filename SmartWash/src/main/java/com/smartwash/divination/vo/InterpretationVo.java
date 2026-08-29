package com.smartwash.divination.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 解读记录视图。
 */
@Data
public class InterpretationVo {

    private Long id;

    private Long recordId;

    private String kind;

    private String question;

    private String provider;

    private String model;

    private String keySource;

    private String contentMd;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer latencyMs;

    private Integer cacheHit;

    /** 0待审/1通过/2不一致 */
    private Integer auditStatus;

    private LocalDateTime createdAt;
}
