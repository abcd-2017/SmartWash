package com.smartwash.feature.order.impl.network.vo.order

import androidx.annotation.Keep
import com.smartwash.feature.order.api.model.OrderInfo

/**
 * 订单详情 / 列表条目响应 VO（对应后端 vo/OrderInfo 与 vo/ShowOrderVo，T6.1 自 app 的
 * network/vo/order/OrderInfo.kt 迁入）。
 *
 * 原类的四个嵌套类型分属四个域（user-impl 的 UserInfoVo、app 的 SchoolVo/LockerVo/
 * LaundryItem），模块化后本模块不得依赖任何 feature-impl 或 app——故改为自带嵌套 VO
 * （JSON 键与字段名不变，反序列化行为一致；UserVo 不声明订单上下文用不到的 schoolVo
 * 嵌套对象，Gson 忽略未声明字段），模式同 user-impl UserInfoVo 的 School 处理。
 * 经 [toModel] 映射到 order-api 的 OrderInfo 后对外暴露。
 *
 * 字段可空是后端真实形态，不是过度防御：Gson 经 Unsafe 绕过 Kotlin 构造器反序列化，
 * 非空声明只是 Kotlin 侧谎言，缺失/为 null 的字段落到映射阶段会抛构造参数 NPE。
 * 各字段为 null 的具体原因见下方注释，[toModel] 统一落安全默认值。
 */
@Keep
data class OrderInfoVo(
    val orderId: Long,
    /** 订单列表接口（后端 ShowOrderVo）不返回该字段，详情接口才带——列表场景为 null */
    val userVo: UserVo?,
    val schoolsVo: School?,
    /** 柜子为 LEFT JOIN 且终态订单会清空 locker_id，故整个快照可能缺失 */
    val lockersVo: Locker?,
    val orderNo: String?,
    val laundryPackageVo: LaundryPackage?,
    val totalPrice: Float?,
    val payPrice: Float?,
    val status: String?,
    /** 终态（取消/完成/退款）订单的取件码会被后端清空为 null */
    val pickupCode: String?,
    val createdAt: String?,
    val updatedAt: String?,
) {

    /**
     * 订单携带的用户快照（JSON 键 userVo；schoolVo 嵌套对象订单上下文不消费，不声明）。
     *
     * studentId/campusCard 在库里可空（未绑定的新用户），balance 同理。
     */
    @Keep
    data class UserVo(
        val phoneNumber: String?,
        val studentId: String?,
        val campusCard: String?,
        val balance: Float?,
        val avatar: String? = null,
    )

    /** 订单携带的学校快照（JSON 键 schoolsVo，形态对齐 school 域 SchoolVo） */
    @Keep
    data class School(
        val schoolId: Long?,
        val schoolName: String?,
        val location: String?,
        val lockerCount: Int?,
    )

    /**
     * 订单携带的柜机快照（JSON 键 lockersVo，形态对齐 locker 域 LockerVo）。
     *
     * 字段可空原因同上：柜子为 LEFT JOIN，且终态订单会清空 locker_id——整块快照
     * 或其中任一字段都可能缺失。
     */
    @Keep
    data class Locker(
        val lockerId: Long?,
        val schoolId: Long?,
        val lockerNumber: Int?,
        val status: String?,
    )

    /**
     * 订单携带的洗衣套餐快照（JSON 键 laundryPackageVo，形态对齐 laundry 域 LaundryItem）。
     *
     * description 在详情查询里未 select，库里本身也可空。
     */
    @Keep
    data class LaundryPackage(
        val itemId: Long?,
        val itemName: String?,
        val basePrice: Float?,
        val description: String?,
    )
}

/**
 * 网络 VO → 对外领域模型（order-api 的 OrderInfo）；Repository 与 OrderPagingSource 共用。
 *
 * 字段逐项容错：null 统一落安全默认值（空串 / 0 / id 落 -1），模式同 user-impl 的
 * UserApiImpl.toUserInfo。order-api 的 OrderInfo 保持非空契约，UI 消费侧无需判空——
 * 未绑校园卡的订单曾在这里抛 NPE 导致下单后直接闪退。
 */
internal fun OrderInfoVo.toModel() = OrderInfo(
    orderId = orderId,
    userVo = OrderInfo.UserSnapshot(
        phoneNumber = userVo?.phoneNumber ?: "",
        studentId = userVo?.studentId ?: "",
        campusCard = userVo?.campusCard ?: "",
        balance = userVo?.balance ?: 0f,
        avatar = userVo?.avatar,
    ),
    schoolsVo = OrderInfo.School(
        schoolId = schoolsVo?.schoolId ?: -1L,
        schoolName = schoolsVo?.schoolName ?: "",
        location = schoolsVo?.location ?: "",
        lockerCount = schoolsVo?.lockerCount ?: 0,
    ),
    lockersVo = OrderInfo.Locker(
        lockerId = lockersVo?.lockerId ?: -1L,
        schoolId = lockersVo?.schoolId ?: -1L,
        lockerNumber = lockersVo?.lockerNumber ?: 0,
        status = lockersVo?.status ?: "",
    ),
    orderNo = orderNo ?: "",
    laundryPackageVo = OrderInfo.LaundryPackage(
        itemId = laundryPackageVo?.itemId ?: -1L,
        itemName = laundryPackageVo?.itemName ?: "",
        basePrice = laundryPackageVo?.basePrice ?: 0f,
        description = laundryPackageVo?.description ?: "",
    ),
    totalPrice = totalPrice ?: 0f,
    payPrice = payPrice ?: 0f,
    status = status ?: "",
    pickupCode = pickupCode ?: "",
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)
