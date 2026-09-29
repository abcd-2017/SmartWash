package com.smartwash.feature.laundry.api

import com.smartwash.feature.laundry.api.model.SchoolOption

/**
 * 学校搜索数据契约（T7.1 自 user-impl 的过渡 seam 升格为洗衣域正式契约）。
 *
 * 消费方：user-impl 的资料编辑页（UpdateUserInfoViewModel 按关键字搜学校供选择，
 * 注入本接口，不得触碰 :feature:laundry:impl 的 SchoolRepository 实现细节）。
 * 学校数据（SchoolRepository：内存→Room→网络三级缓存）随洗衣域归 laundry，
 * 契约由数据持有方的 api 模块持有、laundry 内 SchoolSearchSourceImpl 实现
 * （Hilt `@Binds`，di/LaundryModule），依赖方向 user-impl → laundry-api（impl→api）。
 */
interface SchoolSearchSource {

    /** 按关键字搜索学校（空关键字返回全量，语义 = SchoolRepository.getAllSchools） */
    suspend fun search(keyword: String): List<SchoolOption>
}
