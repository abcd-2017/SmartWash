package com.smartwash.feature.user.impl.network.vo.user

import androidx.annotation.Keep

/**
 * 用户详细信息响应（对应后端 vo/UserInfoVo）。
 *
 * schoolVo 字段原引用 school 域的 SchoolVo（app 内）；T5.2 迁入本模块时改为嵌套
 * [School] —— JSON 键与字段名不变（schoolId/schoolName/location/lockerCount），
 * 反序列化行为完全一致；学校域（SchoolRepository 等）T7.1 才迁 laundry，
 * 两处形态独立演进，互不拖拽。
 *
 * couponCount/orderCount 声明为可空（P-201，2026-11-05）：后端 vo/users/UserInfoVo.java
 * 现无这两个字段，响应 JSON 缺键；可空声明才是响应的真实形态（对齐 School 字段的
 * 空值处理），映射到 user-api UserInfo 时由 UserApiImpl 统一落 0。
 * 待后端 getUserInfo 补齐统计字段后（四端联动），此处无需再改。
 */
@Keep
data class UserInfoVo(
    val phoneNumber: String,
    val studentId: String,
    val campusCard: String,
    val balance: Float,
    val avatar: String? = null,
    val schoolVo: School,
    val couponCount: Int? = null,
    val orderCount: Int? = null,
) {
    /**
     * 用户所属学校（JSON 键 schoolVo，形态对齐 school 域 SchoolVo）。
     *
     * 字段可空：后端对未绑定学校的用户返回空对象（字段全 null），Gson 反序列化
     * 会绕过 Kotlin 非空检查置 null——可空声明才是该 VO 的真实形态。
     * 映射到 user-api 的 UserInfo 时由 UserApiImpl 统一落默认值（schoolId = -1）。
     */
    @Keep
    data class School(
        val schoolId: Long?,
        val schoolName: String?,
        val location: String?,
        val lockerCount: Int?,
    )
}
