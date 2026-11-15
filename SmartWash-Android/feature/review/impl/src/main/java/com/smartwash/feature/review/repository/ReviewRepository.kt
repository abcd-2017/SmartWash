package com.smartwash.feature.review.repository

import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.review.model.ReviewRating
import com.smartwash.feature.review.api.network.MaterialVo
import com.smartwash.feature.review.api.network.ReviewCardVo
import com.smartwash.feature.review.api.network.ReviewQueueVo
import com.smartwash.feature.review.api.network.ReviewStatsVo

/**
 * 复习数据契约（接口抽象，ViewModel 面向接口消费，JVM 单测以 Fake 替换；
 * 实现见 [ReviewRepositoryImpl]）。
 * data 为 null 一律按失败抛 [com.smartwash.common.network.exception.NetworkException]，
 * 让上层能区分「无数据」与「请求失败」。
 */
interface ReviewRepository {

    /** 创建资料，返回资料 id */
    suspend fun createMaterial(title: String, rawText: String?): Long

    suspend fun pageMaterials(page: Int, size: Int): PageData<MaterialVo>

    /** 在资料下建卡，返回卡片 id（后端初始 state=Learning, step=0, due=now） */
    suspend fun addCard(materialId: Long, question: String, answer: String): Long

    suspend fun pageCards(materialId: Long, page: Int, size: Int): PageData<ReviewCardVo>

    /** 今日队列：到期卡在前 + 新卡注入队尾 */
    suspend fun getQueue(): ReviewQueueVo

    /**
     * 复习评分：rating 1..4；durationMs 可空（作答耗时）。
     * 返回后端 FSRS 重算后的卡片（v1 页面不消费返回值，评分成功即本地移除该卡）。
     */
    suspend fun answer(cardId: Long, rating: ReviewRating, durationMs: Int?): ReviewCardVo

    /** 近 30 天复习统计（预留：v1 三页面未消费，统计页接入时直接使用） */
    suspend fun stats(): ReviewStatsVo
}
