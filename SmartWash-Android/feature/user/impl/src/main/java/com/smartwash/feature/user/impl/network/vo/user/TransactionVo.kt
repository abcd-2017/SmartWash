package com.smartwash.feature.user.impl.network.vo.user

import androidx.annotation.Keep
import java.math.BigDecimal

/**
 * 钱包流水条目（对应后端 TransactionVo：GET /web/auth/user/transactions 分页返回）。
 *
 * 符号约定（对齐鸿蒙端决策）：[amount] 服务端原样下发（payment 为负数已下发），
 * 端上按 [type] 重算符号（充值 + / 消费 −）并对金额取绝对值展示，防双符号。
 */
@Keep
data class TransactionVo(
    /** 类型："recharge"（充值）/ "payment"（洗衣消费） */
    val type: String? = null,
    /** 金额（BigDecimal，服务端 payment 已带负号；展示时取绝对值重算符号） */
    val amount: BigDecimal? = null,
    /** 描述（如「账户充值」「订单支付」） */
    val description: String? = null,
    /** 交易时间（yyyy-MM-dd HH:mm:ss，LocalDateTime 序列化；解析失败原样展示） */
    val transactionTime: String? = null,
    /** 交易状态（如 "success"；端上暂不展示，仅随契约下发） */
    val status: String? = null,
)
