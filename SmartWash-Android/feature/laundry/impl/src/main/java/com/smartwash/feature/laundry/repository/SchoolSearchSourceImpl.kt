package com.smartwash.feature.laundry.repository

import com.smartwash.feature.laundry.api.SchoolSearchSource
import com.smartwash.feature.laundry.api.model.SchoolOption
import com.smartwash.feature.laundry.network.vo.SchoolName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学校搜索供给（T7.1 自 app/di/UserImplSeamModule 的 SchoolSearchSourceImpl 迁入，
 * 实现语义不变：SchoolRepository 全量/过滤查询映射为 SchoolOption，
 * 类型面收敛为 {schoolId, schoolName}，不泄漏学校域 VO）。
 */
@Singleton
class SchoolSearchSourceImpl @Inject constructor(
    private val schoolRepository: SchoolRepository,
) : SchoolSearchSource {

    override suspend fun search(keyword: String): List<SchoolOption> {
        return schoolRepository.getAllSchools(keyword).map(::toOption)
    }

    private fun toOption(school: SchoolName) = SchoolOption(
        schoolId = school.schoolId,
        schoolName = school.schoolName,
    )
}
