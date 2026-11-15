package com.smartwash.feature.user.impl.network.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.user.impl.network.vo.user.TransactionVo
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 钱包流水 Retrofit 接口（full 变体专属，随钱包流水页下沉 app/src/full）。
 *
 * 为什么不在 :feature:user:impl 的 [UserAccountApi]：钱包流水页（WalletTransactionsPage）
 * 是 full 专属页面（依赖 payment-api 的 PaymentRoute），其分页数据契约（[TransactionVo]）
 * 与分页源（TransactionPagingSource）一并下沉，避免 user-impl 在 lite classpath 下
 * 引用 full 专属类型。接口路径与后端契约不变（GET /web/auth/user/transactions）。
 */
interface TransactionApi {

    /** 钱包流水（分页；pageSize 上限 50） */
    @RequireAuthorization
    @GET("/web/auth/user/transactions")
    suspend fun getTransactions(
        @Query("page") page: Int?,
        @Query("pageSize") pageSize: Int? = 20,
    ): ApiResult<PageData<TransactionVo>>
}
