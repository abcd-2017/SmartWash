package com.smartwash.review.scheduler;

import java.time.LocalDateTime;

/**
 * 单次复习日志值对象，对齐 py-fsrs ReviewLog
 * （open-spaced-repetition/py-fsrs, MIT）：card_id/rating/review_datetime/review_duration。
 * 持久化由 review_log 实体负责。
 */
public class FsrsReviewLog {

    private Long cardId;

    private FsrsRating rating;

    private LocalDateTime reviewedAt;

    /** 作答耗时毫秒，可空 */
    private Integer durationMs;

    public FsrsReviewLog(Long cardId, FsrsRating rating, LocalDateTime reviewedAt, Integer durationMs) {
        this.cardId = cardId;
        this.rating = rating;
        this.reviewedAt = reviewedAt;
        this.durationMs = durationMs;
    }

    public Long getCardId() {
        return cardId;
    }

    public FsrsRating getRating() {
        return rating;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public Integer getDurationMs() {
        return durationMs;
    }
}
