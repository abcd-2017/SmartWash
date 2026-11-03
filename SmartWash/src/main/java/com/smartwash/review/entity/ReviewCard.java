package com.smartwash.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 复习卡（FSRS 状态列，字段名对齐 py-fsrs Card），对应表 review_card。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("review_card")
public class ReviewCard implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** study_material.id，NULL=独立卡 */
    private Long materialId;

    private String question;

    private String answer;

    /** 1=Learning 2=Review 3=Relearning（py-fsrs State） */
    private Integer state;

    /** learning/relearning 步序号，Review 态为 NULL */
    private Integer step;

    /** 稳定度 S（天） */
    private BigDecimal stability;

    /** 难度 D（1-10） */
    private BigDecimal difficulty;

    /** 下次到期时刻 */
    private LocalDateTime dueAt;

    /** NULL=从未复习的新卡 */
    private LocalDateTime lastReviewAt;

    /** 累计复习次数 */
    private Integer reps;

    /** Again 次数（遗忘次数） */
    private Integer lapses;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
