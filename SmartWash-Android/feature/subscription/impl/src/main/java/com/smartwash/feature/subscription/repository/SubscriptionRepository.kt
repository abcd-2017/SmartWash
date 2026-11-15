package com.smartwash.feature.subscription.repository

import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.subscription.api.network.AddUsageRequest
import com.smartwash.feature.subscription.api.network.ReminderVo
import com.smartwash.feature.subscription.api.network.SaveSubscriptionRequest
import com.smartwash.feature.subscription.api.network.SubscriptionStatsVo
import com.smartwash.feature.subscription.api.network.SubscriptionVo
import com.smartwash.feature.subscription.api.network.UpdateSubscriptionRequest
import com.smartwash.feature.subscription.api.network.UsageLogVo

/**
 * 订阅数据契约（接口抽象，ViewModel/PagingSource 面向接口消费，
 * 实现见 [SubscriptionRepositoryImpl]）。
 * data 为 null 一律按失败抛 [com.smartwash.common.network.exception.NetworkException]，
 * 让上层能区分「无数据」与「请求失败」；动作类端点成功以 code=200 为准。
 */
interface SubscriptionRepository {

    suspend fun createSubscription(request: SaveSubscriptionRequest): SubscriptionVo

    suspend fun updateSubscription(id: Long, request: UpdateSubscriptionRequest): SubscriptionVo

    suspend fun deleteSubscription(id: Long)

    suspend fun pageSubscriptions(page: Int, size: Int, status: String?): PageData<SubscriptionVo>

    suspend fun getSubscription(id: Long): SubscriptionVo

    suspend fun pauseSubscription(id: Long)

    suspend fun resumeSubscription(id: Long)

    suspend fun cancelSubscription(id: Long)

    suspend fun finishSubscription(id: Long)

    suspend fun checkInUsage(id: Long, request: AddUsageRequest)

    suspend fun pageUsages(id: Long, page: Int, size: Int): PageData<UsageLogVo>

    suspend fun deleteUsage(id: Long, usageId: Long)

    suspend fun listReminders(unreadOnly: Boolean?): List<ReminderVo>

    suspend fun markReminderRead(reminderId: Long)

    suspend fun getStats(): SubscriptionStatsVo
}
