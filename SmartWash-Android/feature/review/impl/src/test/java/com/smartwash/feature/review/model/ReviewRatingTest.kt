package com.smartwash.feature.review.model

import com.smartwash.feature.review.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 复习评分四档与卡片状态枚举映射 JVM 单测（Anki/py-fsrs 惯例）。
 *
 * 契约锚点（docs/review-api.md）：评分 1=Again 2=Hard 3=Good 4=Easy；
 * 卡片状态 1=Learning 2=Review 3=Relearning；fromCode 对未知码一律 null（不抛异常、
 * 不静默归档），由 UI 回退安全渲染。
 */
class ReviewRatingTest {

    // ==================== ReviewRating ====================

    @Test
    fun `评分码 1到4 映射对应档位`() {
        assertEquals(ReviewRating.AGAIN, ReviewRating.fromCode(1))
        assertEquals(ReviewRating.HARD, ReviewRating.fromCode(2))
        assertEquals(ReviewRating.GOOD, ReviewRating.fromCode(3))
        assertEquals(ReviewRating.EASY, ReviewRating.fromCode(4))
    }

    @Test
    fun `评分码越界返回 null`() {
        assertNull(ReviewRating.fromCode(0))
        assertNull(ReviewRating.fromCode(5))
        assertNull(ReviewRating.fromCode(-1))
        assertNull(ReviewRating.fromCode(100))
    }

    @Test
    fun `评分四档码连续且唯一（1到4 各占一档）`() {
        assertEquals((1..4).toList(), ReviewRating.entries.map { it.code }.sorted())
        assertEquals(4, ReviewRating.entries.distinctBy { it.code }.size)
    }

    @Test
    fun `评分档位文案资源均已声明`() {
        assertEquals(R.string.review_rating_again, ReviewRating.AGAIN.labelRes)
        assertEquals(R.string.review_rating_hard, ReviewRating.HARD.labelRes)
        assertEquals(R.string.review_rating_good, ReviewRating.GOOD.labelRes)
        assertEquals(R.string.review_rating_easy, ReviewRating.EASY.labelRes)
        // 中文语义文案（评分按钮两行展示）非 0，防「资源漏配显示空」回归
        ReviewRating.entries.forEach { entry ->
            assert(entry.semanticRes != 0) { "${entry.name} semanticRes 未配置" }
        }
    }

    // ==================== ReviewCardState ====================

    @Test
    fun `卡片状态码 1到3 映射对应状态`() {
        assertEquals(ReviewCardState.LEARNING, ReviewCardState.fromCode(1))
        assertEquals(ReviewCardState.REVIEW, ReviewCardState.fromCode(2))
        assertEquals(ReviewCardState.RELEARNING, ReviewCardState.fromCode(3))
    }

    @Test
    fun `卡片状态码越界返回 null`() {
        assertNull(ReviewCardState.fromCode(0))
        assertNull(ReviewCardState.fromCode(4))
        assertNull(ReviewCardState.fromCode(-1))
    }

    @Test
    fun `卡片状态三档码连续且唯一`() {
        assertEquals(listOf(1, 2, 3), ReviewCardState.entries.map { it.code })
    }
}
