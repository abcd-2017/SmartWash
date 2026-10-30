package com.smartwash.network.api

import androidx.annotation.Keep
import com.smartwash.common.network.annotation.RequireAuthorization
import com.smartwash.common.utils.model.ApiResult
import retrofit2.http.GET

/**
 * 工作台入口配置接口（壳层工作台 tab 动态下发）。
 * 后端契约：WebWorkbenchEntryController#entries，路径前缀 /web/auth/workbench（需 ROLE_USER）。
 * endpoint 走相对路径，复用 :common:network 注入的 Retrofit（baseUrl 即 BuildConfig.BASE_URL），
 * 禁止硬编码地址；鉴权/错误转译拦截器经 NetworkModule 自动生效（非 200 业务码抛 NetworkException）。
 */
interface WorkbenchEntryApi {

    /** 工作台入口列表：仅启用中的入口，按 sort 升序、id 升序，不分页 */
    @GET("web/auth/workbench/entries")
    @RequireAuthorization
    suspend fun getEntries(): ApiResult<List<WorkbenchEntryVo>>
}

/**
 * 工作台入口 VO（对齐后端 WorkbenchEntryVo，字段名逐一对齐勿改名）。
 * icon/color 为端上白名单枚举 key，未知值显示默认样式（default 图标 + green 色组）。
 */
@Keep
data class WorkbenchEntryVo(
    /** 业务唯一标识，端上兜底列表对齐用（兼作列表稳定 key） */
    val entryKey: String = "",

    /** 入口标题 */
    val title: String = "",

    /** 副标题描述 */
    val description: String = "",

    /** 图标 key（白名单：calendar/search/book/calculate/history/chat/link/default） */
    val icon: String = "",

    /** 图标色组 key（白名单：green/blue/orange/pink/teal/purple） */
    val color: String = "",

    /** 跳转 URI：smartwash://... 或 https://...；空 = 功能建设中 */
    val target: String? = null,

    /** 角标文案（如 新/热），空则不显示 */
    val badge: String? = null,

    /** 排序，越小越靠前（后端已排好，端上不再排序） */
    val sort: Int = 0,
)
