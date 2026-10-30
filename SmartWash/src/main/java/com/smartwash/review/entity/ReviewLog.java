package com.smartwash.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 复习日志（重放重算与统计依赖，不可省），对应表 review_log。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("review_log")
public class ReviewLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long cardId;

    /** 1=Again 2=Hard 3=Good 4=Easy（py-fsrs Rating） */
    private Integer rating;

    private LocalDateTime reviewedAt;

    /** 作答耗时毫秒，可空 */
    private Integer durationMs;
}
