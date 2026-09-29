package com.smartwash.feature.user.api.model

/**
 * 用户域对外用户信息模型（字段对齐 app 现有 UserInfoVo，T5.2 由 user-impl 做映射）。
 *
 * 刻意不带 @Keep：本模型是 UserApi 的领域载荷，不直接参与 Gson 反序列化
 * （网络层的 UserInfoVo 仍在 user-impl 内经 Retrofit 解析后映射到本类），
 * 保持 api 模块零 androidx.annotation 依赖。
 */
data class UserInfo(
    val phoneNumber: String,
    val studentId: String,
    val campusCard: String,
    val balance: Float,
    val avatar: String? = null,
    val school: School,
) {
    /**
     * 用户所属学校（字段对齐现有 SchoolVo）。
     *
     * 嵌套在 UserInfo 内而非顶层独立模型：user-api 只暴露"用户信息里携带的学校数据"，
     * 独立的学校域模型（SchoolRepository / SchoolNameDao 等）归 laundry 域（T7.1）。
     */
    data class School(
        val schoolId: Long,
        val schoolName: String,
        val location: String,
        val lockerCount: Int,
    )
}
