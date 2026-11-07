package com.smartwash.feature.readlater.repository

import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.readlater.network.ReadLaterVo

/**
 * 稍后读数据契约（接口抽象，ViewModel 面向接口消费；实现见 [ReadLaterRepositoryImpl]）。
 * 返回值为承载型的（add 返回新 id、digest/shorten 返回后端文案或短链地址）；
 * data 为 null 一律按失败抛 NetworkException，让上层能区分「无数据」与「请求失败」。
 */
interface ReadLaterRepository {

    /** 收藏链接（url 仅收 https），返回新建条目 id */
    suspend fun add(url: String, tags: List<String>): Long

    /** 分页列表：status/tag/keyword 均可选 */
    suspend fun page(
        page: Int,
        size: Int,
        status: Int? = null,
        tag: String? = null,
        keyword: String? = null,
    ): PageData<ReadLaterVo>

    /** 生成 AI 摘要（可能较慢），返回后端提示文案 */
    suspend fun generateDigest(id: Long): String

    /** 更新标签/已读状态（null 不更新），返回后端提示文案 */
    suspend fun update(id: Long, tags: List<String>? = null, status: Int? = null): String

    /** 删除收藏，返回后端提示文案 */
    suspend fun delete(id: Long): String

    /** 转短链，返回完整短链地址 */
    suspend fun shorten(id: Long): String
}
