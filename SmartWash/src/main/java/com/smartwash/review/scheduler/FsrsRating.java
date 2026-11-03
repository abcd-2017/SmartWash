package com.smartwash.review.scheduler;

/**
 * 复习评分，对齐 py-fsrs Rating（open-spaced-repetition/py-fsrs, MIT）：
 * Again=1 / Hard=2 / Good=3 / Easy=4。编码直接落库 review_log.rating。
 */
public enum FsrsRating {

    /** 忘了 */
    AGAIN(1),

    /** 想起来了但很费劲 */
    HARD(2),

    /** 想起来了 */
    GOOD(3),

    /** 秒答 */
    EASY(4);

    private final int code;

    FsrsRating(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按编码还原枚举，1..4 之外一律拒绝（评分越界防御） */
    public static FsrsRating of(int code) {
        for (FsrsRating rating : values()) {
            if (rating.code == code) {
                return rating;
            }
        }
        throw new IllegalArgumentException("评分必须在 1..4 内(1=Again 2=Hard 3=Good 4=Easy)，收到: " + code);
    }
}
