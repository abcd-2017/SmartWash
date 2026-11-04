package com.smartwash.feature.review.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.review.model.ReviewRating
import com.smartwash.feature.review.network.AddCardRequest
import com.smartwash.feature.review.network.AddMaterialRequest
import com.smartwash.feature.review.network.AnswerRequest
import com.smartwash.feature.review.network.MaterialVo
import com.smartwash.feature.review.network.ReviewApi
import com.smartwash.feature.review.network.ReviewCardVo
import com.smartwash.feature.review.network.ReviewQueueVo
import com.smartwash.feature.review.network.ReviewStatsVo
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 复习数据实现：薄封装 ReviewApi，统一「data 为 null 即失败」语义
 * （业务失败已被 ResponseInterceptor 转译为 NetworkException 抛出，
 * 这里只兜住 code=200 但 data 缺失的分支），成功以 code=200 为准。
 */
@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val reviewApi: ReviewApi,
) : ReviewRepository {

    override suspend fun createMaterial(title: String, rawText: String?): Long {
        return reviewApi.createMaterial(AddMaterialRequest(title = title, rawText = rawText)).data
            ?: run {
                AppLogger.warn(TAG, "createMaterial: 建资料响应 data 为空，按失败上抛")
                throw NetworkException("资料数据为空", R.string.error_network_fail)
            }
    }

    override suspend fun pageMaterials(page: Int, size: Int): PageData<MaterialVo> {
        return reviewApi.pageMaterials(page = page, size = size).data ?: run {
            AppLogger.warn(TAG, "pageMaterials: 资料列表响应 data 为空（page=$page），按失败上抛")
            throw NetworkException("资料列表为空", R.string.error_network_fail)
        }
    }

    override suspend fun addCard(materialId: Long, question: String, answer: String): Long {
        return reviewApi.addCard(materialId, AddCardRequest(question = question, answer = answer)).data
            ?: run {
                AppLogger.warn(TAG, "addCard: 建卡响应 data 为空（materialId=$materialId），按失败上抛")
                throw NetworkException("卡片数据为空", R.string.error_network_fail)
            }
    }

    override suspend fun pageCards(materialId: Long, page: Int, size: Int): PageData<ReviewCardVo> {
        return reviewApi.pageCards(materialId = materialId, page = page, size = size).data ?: run {
            AppLogger.warn(TAG, "pageCards: 卡片列表响应 data 为空（materialId=$materialId page=$page），按失败上抛")
            throw NetworkException("卡片列表为空", R.string.error_network_fail)
        }
    }

    override suspend fun getQueue(): ReviewQueueVo {
        return reviewApi.getQueue().data ?: run {
            AppLogger.warn(TAG, "getQueue: 复习队列响应 data 为空，按失败上抛")
            throw NetworkException("复习队列为空", R.string.error_network_fail)
        }
    }

    override suspend fun answer(cardId: Long, rating: ReviewRating, durationMs: Int?): ReviewCardVo {
        return reviewApi.answer(
            AnswerRequest(cardId = cardId, rating = rating.code, durationMs = durationMs),
        ).data ?: run {
            AppLogger.warn(TAG, "answer: 评分响应 data 为空（cardId=$cardId rating=${rating.code}），按失败上抛")
            throw NetworkException("评分结果为空", R.string.error_network_fail)
        }
    }

    override suspend fun stats(): ReviewStatsVo {
        return reviewApi.stats().data ?: run {
            AppLogger.warn(TAG, "stats: 统计响应 data 为空，按失败上抛")
            throw NetworkException("统计数据为空", R.string.error_network_fail)
        }
    }

    private companion object {
        const val TAG = "ReviewRepository"
    }
}
