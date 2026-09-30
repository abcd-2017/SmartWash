package com.smartwash.feature.laundry.api.model

/**
 * 学校搜索结果项（资料编辑页仅需 id 与名称；T7.1 自 user-impl 迁入，字段不变）。
 * 刻意不带 @Keep：本模型是 SchoolSearchSource 的领域载荷，不直接参与 Gson 反序列化
 * （网络层的 SchoolName 仍在 laundry 内经 Retrofit 解析后映射到本类）。
 */
data class SchoolOption(
    val schoolId: Long,
    val schoolName: String,
)
