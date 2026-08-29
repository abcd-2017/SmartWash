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
 * 解读反馈（含应验回填，评测数据源）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_feedback")
public class DivFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long interpretationId;

    private Long userId;

    /** 1-5 */
    private Integer rating;

    /** 0未回填/1应验/2未验/3难说 */
    private Integer outcome;

    private String outcomeNote;

    private LocalDateTime createdAt;
}
