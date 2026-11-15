package com.smartwash.repository

import com.smartwash.network.api.WorkbenchEntryVo

/**
 * 工作台入口数据契约（接口抽象，ViewModel 面向接口消费；实现见 [WorkbenchEntryRepositoryImpl]）。
 */
interface WorkbenchEntryRepository {

    /**
     * 拉取启用中的工作台入口（后端已按 sort 升序返回）。
     * 失败抛 NetworkException（ResponseInterceptor 转译），由上层走本地兜底列表。
     */
    suspend fun getEntries(): List<WorkbenchEntryVo>
}
