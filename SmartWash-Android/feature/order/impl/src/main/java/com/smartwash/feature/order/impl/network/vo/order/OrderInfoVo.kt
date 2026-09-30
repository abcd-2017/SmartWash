package com.smartwash.feature.order.impl.network.vo.order

import androidx.annotation.Keep
import com.smartwash.feature.order.api.model.OrderInfo

/**
 * 订单详情响应 VO（对应后端 vo/OrderInfo，T6.1 自 app 的 network/vo/order/OrderInfo.kt 迁入）。
 *
 * 原类的四个嵌套类型分属四个域（user-impl 的 UserInfoVo、app 的 SchoolVo/LockerVo/
 * LaundryItem），模块化后本模块不得依赖任何 feature-impl 或 app——故改为自带嵌套 VO
 * （JSON 键与字段名不变，反序列化行为一致；UserVo 不声明订单上下文用不到的 schoolVo
 * 嵌套对象，Gson 忽略未声明字段），模式同 user-impl UserInfoVo 的 School 处理。
 * 经 [toModel] 映射到 order-api 的 OrderInfo 后对外暴露。
 */
@Keep
data class OrderInfoVo(
    val orderId: Long,
    val userVo: UserVo,
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

    /** 订单携带的用户快照（JSON 键 userVo；schoolVo 嵌套对象订单上下文不消费，不声明） */
    @Keep
    data class UserVo(
        val phoneNumber: String,
        val studentId: String,
        val campusCard: String,
        val balance: Float,
        val avatar: String? = null,
    )

    /** 订单携带的学校快照（JSON 键 schoolsVo，形态对齐 school 域 SchoolVo） */
    @Keep
    data class School(
        val schoolId: Long,
        val schoolName: String,
        val location: String,
        val lockerCount: Int,
    )

    /** 订单携带的柜机快照（JSON 键 lockersVo，形态对齐 locker 域 LockerVo） */
    @Keep
    data class Locker(
        val lockerId: Long,
        val schoolId: Long,
        val lockerNumber: Int,
        val status: String,
    )

    /** 订单携带的洗衣套餐快照（JSON 键 laundryPackageVo，形态对齐 laundry 域 LaundryItem） */
    @Keep
    data class LaundryPackage(
        val itemId: Long,
        val itemName: String,
        val basePrice: Float,
        val description: String,
    )
}

/** 网络 VO → 对外领域模型（order-api 的 OrderInfo）；Repository 与 OrderPagingSource 共用 */
internal fun OrderInfoVo.toModel() = OrderInfo(
    orderId = orderId,
    userVo = OrderInfo.UserSnapshot(
        phoneNumber = userVo.phoneNumber,
        studentId = userVo.studentId,
        campusCard = userVo.campusCard,
        balance = userVo.balance,
        avatar = userVo.avatar,
    ),
    schoolsVo = OrderInfo.School(
        schoolId = schoolsVo.schoolId,
        schoolName = schoolsVo.schoolName,
        location = schoolsVo.location,
        lockerCount = schoolsVo.lockerCount,
    ),
    lockersVo = OrderInfo.Locker(
        lockerId = lockersVo.lockerId,
        schoolId = lockersVo.schoolId,
        lockerNumber = lockersVo.lockerNumber,
        status = lockersVo.status,
    ),
    orderNo = orderNo,
    laundryPackageVo = OrderInfo.LaundryPackage(
        itemId = laundryPackageVo.itemId,
        itemName = laundryPackageVo.itemName,
        basePrice = laundryPackageVo.basePrice,
        description = laundryPackageVo.description,
    ),
    totalPrice = totalPrice,
    payPrice = payPrice,
    status = status,
    pickupCode = pickupCode,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
