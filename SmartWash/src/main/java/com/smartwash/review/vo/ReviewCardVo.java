package com.smartwash.review.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 复习卡视图（列表/队列/评分后返回）。
 */
@Data
public class ReviewCardVo {

    private Long id;

    private Long materialId;

    private String question;

    private String answer;

    /** 1=Learning 2=Review 3=Relearning（py-fsrs State） */
    private Integer state;

    /** learning/relearning 步序号，Review 态为 null */
    private Integer step;

    /** 稳定度 S（天） */
    private BigDecimal stability;

    /** 难度 D（1-10） */
    private BigDecimal difficulty;

    /** 下次到期时刻 */
    private LocalDateTime dueAt;

    /** NULL=从未复习的新卡 */
    private LocalDateTime lastReviewAt;

    private Integer reps;

    private Integer lapses;

    private LocalDateTime createdAt;
}
