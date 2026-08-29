package com.smartwash.divination.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LLM 解读记录。一卦可多次解读，取最新展示。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_interpretation")
public class DivInterpretation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long userId;

    private Long promptVersionId;

    /** first/followup */
    private String kind;

    /** 追问时的子问题 */
    private String question;

    private String provider;

    private String model;

    /** platform/user */
    private String keySource;

    /** 模型配置 id（平台目录或用户 BYOK） */
    private Long configId;

    /** 解读正文 Markdown */
    private String contentMd;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer latencyMs;

    private Integer cacheHit;

    /** 0待审/1通过/2不一致 */
    private Integer auditStatus;

    /** 审计明细：引用字段 vs 盘面 diff */
    private String auditJson;

    private LocalDateTime createdAt;
}
