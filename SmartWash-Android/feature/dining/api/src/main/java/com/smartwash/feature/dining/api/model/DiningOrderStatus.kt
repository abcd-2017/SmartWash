package com.smartwash.feature.dining.api.model

/**
 * 点单订单状态全集（后端 dining/constant/DiningOrderStatus 契约，端上映射单一事实来源）。
 *
 * 后端白名单仅两态：ONGOING（可加入、可勾选/改备注/取消）与 FINISHED（发起者锁定并
 * 生成购买清单快照，拒绝一切选菜写入）。文案（dining_order_status_*）由消费方
 * strings.xml 映射（枚举本体不持 res，对齐 PickupDeliveryType 先例）。
 */
enum class DiningOrderStatus(
    /** 后端状态码（列表/详情 VO 的 status 原文） */
    val code: String,
) {
    /** 进行中 */
    ONGOING("ONGOING"),

    /** 已结束 */
    FINISHED("FINISHED");

    companion object {
        private val codeMap = entries.associateBy { it.code }

        /**
         * 状态码 → 枚举；null/空白/白名单外脏数据返回 null，由映射层按保守降级兜底
         * （未知状态按「不可写」处理，交互与展示同 FINISHED——不引导用户对未知态下单）。
         */
        fun fromCode(code: String?): DiningOrderStatus? = codeMap[code]
    }
}
