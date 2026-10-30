package com.smartwash.review.scheduler;

/**
 * 卡片学习状态，对齐 py-fsrs State（open-spaced-repetition/py-fsrs, MIT）：
 * Learning=1 / Review=2 / Relearning=3。编码直接落库 review_card.state。
 */
public enum FsrsState {

    /** 学习中（分钟级短步长） */
    LEARNING(1),

    /** 复习中（天级间隔） */
    REVIEW(2),

    /** 重学中（Again 遗忘后降级） */
    RELEARNING(3);

    private final int code;

    FsrsState(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按库中编码还原枚举，未知编码抛出异常 */
    public static FsrsState of(int code) {
        for (FsrsState state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("未知的卡片状态: " + code);
    }
}
