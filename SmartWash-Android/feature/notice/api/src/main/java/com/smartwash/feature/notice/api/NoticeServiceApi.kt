package com.smartwash.feature.notice.api

import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.notice.api.model.NoticeVo
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 站内信域网络接口（路径/参数/响应结构以后端 WebNoticeController 为契约源，
 * 接口需 ROLE_USER——/web/auth 路径由 SecurityConfig 通配，@RequireAuthorization 注入 Bearer）。
 *
 * 放置说明（对齐 points 先例）：Retrofit 接口与 Gson 载荷一起放 api 模块——
 * 域内 impl 直接消费，跨模块消费方（我的页未读角标）经 [NoticeApi] 领域契约访问，
 * 不直连本接口；依赖方向 api → common:network 合法（铁律 1）。
 */
interface NoticeServiceApi {

    /** 我的收件箱：全量公告分页（最新在前），每条带当前用户 isRead */
    @GET("/web/auth/notices")
    @RequireAuthorization
    suspend fun inbox(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiResult<PageData<NoticeVo>>

    /** 未读数（公告总数 − 当前用户已读数，后端懒创建口径） */
    @GET("/web/auth/notices/unread-count")
    @RequireAuthorization
    suspend fun unreadCount(): ApiResult<Long>

    /** 标记已读（后端幂等：重复标记静默成功） */
    @POST("/web/auth/notices/{id}/read")
    @RequireAuthorization
    suspend fun markRead(@Path("id") id: Long): ApiResult<Unit>

    /** 全部已读（后端幂等：重复键吞掉；返回本次新增条数） */
    @POST("/web/auth/notices/read-all")
    @RequireAuthorization
    suspend fun readAll(): ApiResult<Int>
}
