package com.smartwash.repository

import com.smartwash.network.api.WorkbenchEntryApi
import com.smartwash.network.api.WorkbenchEntryVo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作台入口数据实现：薄封装 WorkbenchEntryApi。
 * 业务失败已由 ResponseInterceptor 转译为 NetworkException 原样上抛（交 ViewModel 走本地兜底），
 * 这里只兜「code=200 但 data 缺失」的防御分支——data null 视为空列表，
 * 与「服务端配置为空」同一语义：上层一律走本地兜底列表。
 */
@Singleton
class WorkbenchEntryRepositoryImpl @Inject constructor(
    private val workbenchEntryApi: WorkbenchEntryApi,
) : WorkbenchEntryRepository {

    override suspend fun getEntries(): List<WorkbenchEntryVo> {
        return workbenchEntryApi.getEntries().data.orEmpty()
    }
}
