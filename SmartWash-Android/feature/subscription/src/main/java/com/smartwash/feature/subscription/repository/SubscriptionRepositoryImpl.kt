package com.smartwash.feature.subscription.repository

import com.smartwash.common.network.R
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.subscription.network.AddUsageRequest
import com.smartwash.feature.subscription.network.ReminderVo
import com.smartwash.feature.subscription.network.SaveSubscriptionRequest
import com.smartwash.feature.subscription.network.SubscriptionApi
import com.smartwash.feature.subscription.network.SubscriptionStatsVo
import com.smartwash.feature.subscription.network.SubscriptionVo
import com.smartwash.feature.subscription.network.UpdateSubscriptionRequest
import com.smartwash.feature.subscription.network.UsageLogVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订阅数据实现：薄封装 SubscriptionApi，统一「data 为 null 即失败」语义
 * （业务失败已被 ResponseInterceptor 转译为 NetworkException 抛出，
 * 这里只兜住 code=200 但 data 缺失的分支）；动作类端点成功以 code=200 为准。
 */
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val subscriptionApi: SubscriptionApi,
) : SubscriptionRepository {

    override suspend fun createSubscription(request: SaveSubscriptionRequest): SubscriptionVo {
        return subscriptionApi.createSubscription(request).data ?: run {
            AppLogger.warn(TAG, "createSubscription: 新建订阅响应 data 为空，按失败上抛")
            throw NetworkException("订阅数据为空", R.string.error_network_fail)
        }
    }

    override suspend fun updateSubscription(id: Long, request: UpdateSubscriptionRequest): SubscriptionVo {
        return subscriptionApi.updateSubscription(id, request).data ?: run {
            AppLogger.warn(TAG, "updateSubscription: 更新订阅响应 data 为空（id=$id），按失败上抛")
            throw NetworkException("订阅数据为空", R.string.error_network_fail)
        }
    }

    override suspend fun deleteSubscription(id: Long) {
        // 后端 Result.ok(null)：成功以 code=200 为准（拦截器已把非 200 转译为异常），data 不消费
        subscriptionApi.deleteSubscription(id)
    }

    override suspend fun pageSubscriptions(page: Int, size: Int, status: String?): PageData<SubscriptionVo> {
        return subscriptionApi.pageSubscriptions(page = page, size = size, status = status).data ?: run {
            AppLogger.warn(TAG, "pageSubscriptions: 订阅列表响应 data 为空（page=$page status=$status），按失败上抛")
            throw NetworkException("订阅列表为空", R.string.error_network_fail)
        }
    }

    override suspend fun getSubscription(id: Long): SubscriptionVo {
        return subscriptionApi.getSubscription(id).data ?: run {
            AppLogger.warn(TAG, "getSubscription: 订阅详情响应 data 为空（id=$id），按失败上抛")
            throw NetworkException("订阅详情为空", R.string.error_network_fail)
        }
    }

    override suspend fun pauseSubscription(id: Long) {
        subscriptionApi.pauseSubscription(id)
    }

    override suspend fun resumeSubscription(id: Long) {
        subscriptionApi.resumeSubscription(id)
    }

    override suspend fun cancelSubscription(id: Long) {
        subscriptionApi.cancelSubscription(id)
    }

    override suspend fun finishSubscription(id: Long) {
        subscriptionApi.finishSubscription(id)
    }

    override suspend fun checkInUsage(id: Long, request: AddUsageRequest) {
        subscriptionApi.checkInUsage(id, request)
    }

    override suspend fun pageUsages(id: Long, page: Int, size: Int): PageData<UsageLogVo> {
        return subscriptionApi.pageUsages(id = id, page = page, size = size).data ?: run {
            AppLogger.warn(TAG, "pageUsages: 使用记录响应 data 为空（id=$id page=$page），按失败上抛")
            throw NetworkException("使用记录为空", R.string.error_network_fail)
        }
    }

    override suspend fun deleteUsage(id: Long, usageId: Long) {
        subscriptionApi.deleteUsage(id, usageId)
    }

    override suspend fun listReminders(unreadOnly: Boolean?): List<ReminderVo> {
        return subscriptionApi.listReminders(unread = unreadOnly).data ?: run {
            AppLogger.warn(TAG, "listReminders: 提醒列表响应 data 为空（unreadOnly=$unreadOnly），按失败上抛")
            throw NetworkException("提醒列表为空", R.string.error_network_fail)
        }
    }

    override suspend fun markReminderRead(reminderId: Long) {
        subscriptionApi.markReminderRead(reminderId)
    }

    override suspend fun getStats(): SubscriptionStatsVo {
        return subscriptionApi.getStats().data ?: run {
            AppLogger.warn(TAG, "getStats: 统计响应 data 为空，按失败上抛")
            throw NetworkException("统计数据为空", R.string.error_network_fail)
        }
    }

    private companion object {
        const val TAG = "SubscriptionRepository"
    }
}
