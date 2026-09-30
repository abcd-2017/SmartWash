package com.smartwash.feature.order.api.model

/**
 * 订单域对外订单模型（订单详情 / 列表条目，T6.1 自 app 的 network/vo/order/OrderInfo 迁入）。
 *
 * 字段名与原 OrderInfo 完全一致（含 userVo/schoolsVo/lockersVo/laundryPackageVo 四个
 * 快照字段名）——消费页面的字段访问路径（orderInfo?.userVo?.balance 等）零改动。
 *
 * 原 OrderInfo 的四个嵌套类型分属四个域（user-impl 的 UserInfoVo、app 的 SchoolVo/
 * LockerVo/LaundryItem），模块化后 order-api 不得依赖任何 feature-impl 或 app——
 * 故改为本模型内嵌的轻量快照类型，字段面对齐原类型（仅 UserSnapshot 去掉订单上下文
 * 用不到的 schoolVo 嵌套）。Gson 反序列化在 order-impl 的 OrderInfoVo 完成后映射到
 * 本类（模式同 user-api 的 UserInfo ← UserInfoVo）。
 *
 * 刻意不带 @Keep：本模型不直接参与 Gson 反序列化，保持 api 模块最小依赖面。
 */
data class OrderInfo(
    val orderId: Long,
    val userVo: UserSnapshot,
    val schoolsVo: School,
    val lockersVo: Locker,
    val orderNo: String,
    val laundryPackageVo: LaundryPackage,
    val totalPrice: Float,
    val payPrice: Float,
    val status: String,
    val pickupCode: String,
    val createdAt: String,
    val updatedAt: String,
) {

    /** 订单携带的用户快照（JSON 键 userVo，字段面对齐 user-impl 的 UserInfoVo 标量字段） */
    data class UserSnapshot(
        val phoneNumber: String,
        val studentId: String,
        val campusCard: String,
        val balance: Float,
        val avatar: String? = null,
    )

    /** 订单携带的学校快照（JSON 键 schoolsVo，字段面对齐 school 域 SchoolVo） */
    data class School(
        val schoolId: Long,
        val schoolName: String,
        val location: String,
        val lockerCount: Int,
    )

    /** 订单携带的柜机快照（JSON 键 lockersVo，字段面对齐 locker 域 LockerVo） */
    data class Locker(
        val lockerId: Long,
        val schoolId: Long,
        val lockerNumber: Int,
        val status: String,
    )

    /** 订单携带的洗衣套餐快照（JSON 键 laundryPackageVo，字段面对齐 laundry 域 LaundryItem） */
    data class LaundryPackage(
        val itemId: Long,
        val itemName: String,
        val basePrice: Float,
        val description: String,
    )
}
