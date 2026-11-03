package com.smartwash.review.scheduler;

import java.time.LocalDateTime;

/**
 * FSRS 卡片内存态，字段对齐 py-fsrs Card 的 7 字段
 * （open-spaced-repetition/py-fsrs, MIT）：card_id/state/step/stability/difficulty/due/last_review。
 * 仅作调度纯函数的输入输出载体，持久化由 review_card 实体负责。
 * 时间统一用 LocalDateTime（与全库 DATETIME 一致，调度只依赖时刻差值，时区无关）。
 */
public class FsrsCard {

    /** 卡片 id（仅回填日志用，调度本身不依赖） */
    private Long cardId;

    private FsrsState state;

    /** learning/relearning 步序号；Review 态为 null */
    private Integer step;

    /** 稳定度 S（天），新卡未复习前为 null */
    private Double stability;

    /** 难度 D（1-10），新卡未复习前为 null */
    private Double difficulty;

    /** 下次到期时刻 */
    private LocalDateTime due;

    /** 最近一次复习时刻，新卡为 null */
    private LocalDateTime lastReview;

    public FsrsCard() {
    }

    public FsrsCard(Long cardId, FsrsState state, Integer step, Double stability,
                    Double difficulty, LocalDateTime due, LocalDateTime lastReview) {
        this.cardId = cardId;
        this.state = state;
        // 对齐 py-fsrs Card.__init__：Learning 态缺 step 时补 0（Review 态保持 null，注意勿用三元拆箱）
        this.step = step;
        if (state == FsrsState.LEARNING && step == null) {
            this.step = 0;
        }
        this.stability = stability;
        this.difficulty = difficulty;
        this.due = due;
        this.lastReview = lastReview;
    }

    /** 新建学习卡：state=Learning, step=0, due=now（与建卡接口语义一致） */
    public static FsrsCard newLearningCard(Long cardId, LocalDateTime now) {
        return new FsrsCard(cardId, FsrsState.LEARNING, 0, null, null, now, null);
    }

    /** 防御性拷贝（对齐 py-fsrs review_card 内 copy(card)，保证纯函数不污染入参） */
    public FsrsCard copy() {
        return new FsrsCard(cardId, state, step, stability, difficulty, due, lastReview);
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public FsrsState getState() {
        return state;
    }

    public void setState(FsrsState state) {
        this.state = state;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public Double getStability() {
        return stability;
    }

    public void setStability(Double stability) {
        this.stability = stability;
    }

    public Double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Double difficulty) {
        this.difficulty = difficulty;
    }

    public LocalDateTime getDue() {
        return due;
    }

    public void setDue(LocalDateTime due) {
        this.due = due;
    }

    public LocalDateTime getLastReview() {
        return lastReview;
    }

    public void setLastReview(LocalDateTime lastReview) {
        this.lastReview = lastReview;
    }
}
