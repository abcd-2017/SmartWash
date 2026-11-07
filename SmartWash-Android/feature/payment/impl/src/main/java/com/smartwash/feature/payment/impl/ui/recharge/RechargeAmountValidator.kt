package com.smartwash.feature.payment.impl.ui.recharge

/**
 * 充值金额校验（与后端 `RechargeRecordsServiceImpl` 的兜底校验对齐）：
 * 必须为正数、最多两位小数、不超过单笔上限 [MAX_RECHARGE_AMOUNT]。
 *
 * 抽为纯函数供 JVM 单测覆盖（页面内联 lambda 不可测）；错误类型 → 文案的映射留在页面层。
 */
internal enum class RechargeAmountError {
    /** 未选预设金额且自定义金额为空 */
    NOT_SELECTED,

    /** 自定义金额为空 */
    EMPTY,

    /** 非正整数写法或超过两位小数 */
    INVALID_FORMAT,

    /** 金额必须大于 0 */
    NOT_POSITIVE,

    /** 超过单笔上限 */
    EXCEED_MAX,
}

/** 校验通过后的可用金额 */
internal data class RechargeAmountResult(val amount: Float?, val error: RechargeAmountError?)

internal object RechargeAmountValidator {

    /** 单笔充值金额上限：与后端 `RechargeRecordsServiceImpl.MAX_RECHARGE_AMOUNT` 一致 */
    const val MAX_RECHARGE_AMOUNT = 10000f

    /**
     * 预设档赠送映射（设计稿 .amount3 em：¥50 送 ¥2 / ¥100 送 ¥8 / ¥200 送 ¥20）。
     * 仅预设档有赠送，自定义档赠送 = 0（自定义输入 50 不享受 50 档赠送）。
     * 从页面私有常量上收（2026-11-05 修复①）：页面提交与摘要行共用同一事实来源。
     */
    val PRESET_BONUSES: Map<Float, Float> = mapOf(50f to 2f, 100f to 8f, 200f to 20f)

    /** 金额格式：正整数或最多两位小数（与后端 `scale <= 2` 校验一致） */
    private val AMOUNT_PATTERN = Regex("^\\d+(\\.\\d{1,2})?$")

    /**
     * 到账金额 = 支付金额 + 赠送（仅预设档，自定义档赠送 = 0）。
     *
     * 后端 `RechargeRecordsServiceImpl.userRecharge` 的 amount 语义即「入账金额」，
     * 页面提交时必须传本值而非支付金额（2026-11-05 修复①：此前误传支付金额，
     * 选 100 送 8 档用户实付 100 只到账 100，摘要行展示的 +8 从未到账）。
     * 充值按钮文案仍展示支付金额（语义正确：付 ¥100.00、到账 ¥108.00）。
     */
    fun creditAmount(payAmount: Float, isCustomAmountSelected: Boolean): Float =
        payAmount + if (isCustomAmountSelected) 0f else (PRESET_BONUSES[payAmount] ?: 0f)

    /**
     * 解析本次提交应使用的充值金额。
     *
     * 预设金额卡片取值固定合法；自定义金额逐项校验，失败原因写入 [RechargeAmountResult.error]。
     */
    fun resolve(isCustomAmountSelected: Boolean, presetAmount: Float?, customAmountRaw: String): RechargeAmountResult {
        if (isCustomAmountSelected) {
            val error = validateCustomAmount(customAmountRaw)
            return if (error == null) RechargeAmountResult(parseAmount(customAmountRaw), null)
            else RechargeAmountResult(null, error)
        }
        return if (presetAmount != null) {
            RechargeAmountResult(presetAmount, null)
        } else {
            RechargeAmountResult(null, RechargeAmountError.NOT_SELECTED)
        }
    }

    /** 校验自定义金额文本，返回 null 表示通过 */
    fun validateCustomAmount(raw: String): RechargeAmountError? {
        val text = raw.trim()
        if (text.isEmpty()) return RechargeAmountError.EMPTY
        if (!AMOUNT_PATTERN.matches(text)) return RechargeAmountError.INVALID_FORMAT
        val amount = text.toFloatOrNull() ?: return RechargeAmountError.INVALID_FORMAT
        if (amount <= 0f) return RechargeAmountError.NOT_POSITIVE
        if (amount > MAX_RECHARGE_AMOUNT) return RechargeAmountError.EXCEED_MAX
        return null
    }

    /** 取用原始文本解析的金额，保小数精度（不做取整截断） */
    fun parseAmount(raw: String): Float = raw.trim().toFloat()
}
