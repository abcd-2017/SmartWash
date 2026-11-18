package com.smartwash.feature.dining.api.model

/**
 * 点单订单状态全集（后端 dining/constant/DiningOrderStatus 契约，端上映射单一事实来源）。
 *
 * 两态：DRAFT（编辑中——标题/菜单可空，全员可加/移除菜、可邀请加入）与 FINISHED
 * （发起者「提交」后直接结束并生成购买清单快照，拒绝一切写入）。
 * 文案（dining_order_status_*）由消费方 strings.xml 映射（枚举本体不持 res）。
 */
enum class DiningOrderStatus(
    /** 后端状态码（列表/详情 VO 的 status 原文） */
    val code: String,
) {
    /** 编辑中：菜单可加/移除、可邀请加入，提交后结束 */
    DRAFT("DRAFT"),

    /** 已结束（已提交，购买清单已生成） */
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
