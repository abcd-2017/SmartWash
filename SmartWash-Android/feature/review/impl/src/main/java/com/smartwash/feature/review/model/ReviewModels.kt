package com.smartwash.feature.review.model

import androidx.annotation.StringRes
import com.smartwash.feature.review.R

/**
 * 复习评分四档（Anki/py-fsrs 惯例，契约 docs/review-api.md §评分语义）：
 * 1=Again（想不起来）2=Hard（很费劲）3=Good（正常想起）4=Easy（秒答）。
 * 客户端只传 code，FSRS 调度全部在后端 FsrsScheduler 纯函数内完成。
 * [labelRes] 为 Anki 英文惯例名，[semanticRes] 为中文语义文案（评分按钮两行展示）。
 */
enum class ReviewRating(
    val code: Int,
    @StringRes val labelRes: Int,
    @StringRes val semanticRes: Int,
) {
    AGAIN(1, R.string.review_rating_again, R.string.review_rating_again_semantic),
    HARD(2, R.string.review_rating_hard, R.string.review_rating_hard_semantic),
    GOOD(3, R.string.review_rating_good, R.string.review_rating_good_semantic),
    EASY(4, R.string.review_rating_easy, R.string.review_rating_easy_semantic);

    companion object {
        fun fromCode(code: Int): ReviewRating? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 卡片状态（py-fsrs State，契约 docs/review-api.md §卡片状态）：
 * 1=Learning（分钟级短步长）2=Review（天级间隔）3=Relearning（遗忘后重学，分钟级）。
 */
enum class ReviewCardState(
    val code: Int,
    @StringRes val labelRes: Int,
) {
    LEARNING(1, R.string.review_state_learning),
    REVIEW(2, R.string.review_state_review),
    RELEARNING(3, R.string.review_state_relearning);

    companion object {
        fun fromCode(code: Int): ReviewCardState? = entries.firstOrNull { it.code == code }
    }
}
