package com.smartwash.feature.memo.repository

import com.smartwash.common.utils.model.PageData
import com.smartwash.feature.memo.network.MemoTagCountVo
import com.smartwash.feature.memo.network.MemoVo

/**
 * 速记手账数据契约（接口抽象，ViewModel/PagingSource 面向接口消费，实现见
 * [MemoRepositoryImpl]）。data 为 null 一律按失败抛
 * [com.smartwash.common.network.exception.NetworkException]，让上层能区分「无数据」与「请求失败」。
 */
interface MemoRepository {

    /** 新建随手记，返回记录 id */
    suspend fun createMemo(content: String, tags: List<String>): Long

    /** 更新随手记（内容/标签/置顶全量提交） */
    suspend fun updateMemo(id: Long, content: String, tags: List<String>, pinned: Boolean)

    /** 删除随手记（后端逻辑删除） */
    suspend fun deleteMemo(id: Long)

    /** 记录列表分页（keyword/tag 可空 = 不过滤；置顶优先由后端排序） */
    suspend fun pageMemos(page: Int, size: Int, keyword: String?, tag: String?): PageData<MemoVo>

    /** 标签聚合（tag + count） */
    suspend fun listTags(): List<MemoTagCountVo>
}
