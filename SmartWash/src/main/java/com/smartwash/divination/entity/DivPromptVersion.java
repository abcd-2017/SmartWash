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
 * Prompt 版本（system prompt + 领域方法 + 输出参数，热更新源）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_prompt_version")
public class DivPromptVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** liuyao/meihua/qimen/liuren */
    private String method;

    /** 如 liuyao-v1.2 */
    private String version;

    private String systemPrompt;

    /** 领域分析方法文本 */
    private String methodText;

    /** temperature/max_tokens 等 */
    private String outputConfig;

    /** 0草稿/1激活/2退役 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
