package com.smartwash.feature.payment.impl.ui.recharge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RechargeAmountValidator] JVM 单测：金额上限 10000 与 >0 校验（对齐后端兜底规则）。
 */
class RechargeAmountValidatorTest {

    @Test
    fun `预设金额与自定义金额通过校验`() {
        assertNull(RechargeAmountValidator.resolve(isCustomAmountSelected = false, presetAmount = 10f, customAmountRaw = "").error)
        assertNull(RechargeAmountValidator.validateCustomAmount("0.01"))
        assertNull(RechargeAmountValidator.validateCustomAmount("100"))
        assertNull(RechargeAmountValidator.validateCustomAmount("10000"))
    }

    @Test
    fun `自定义金额 0_01 通过且保小数精度`() {
        val result = RechargeAmountValidator.resolve(
            isCustomAmountSelected = true,
            presetAmount = null,
            customAmountRaw = "0.01"
        )
        assertNull(result.error)
        assertEquals(0.01f, result.amount!!, 0f)
    }

    @Test
    fun `自定义金额超过上限 10000 被拦截`() {
        assertEquals(
            RechargeAmountError.EXCEED_MAX,
            RechargeAmountValidator.validateCustomAmount("10001")
        )
        assertEquals(
            RechargeAmountError.EXCEED_MAX,
            RechargeAmountValidator.validateCustomAmount("10000.01")
        )
    }

    @Test
    fun `自定义金额不大于 0 被拦截`() {
        assertEquals(RechargeAmountError.NOT_POSITIVE, RechargeAmountValidator.validateCustomAmount("0"))
        assertEquals(RechargeAmountError.NOT_POSITIVE, RechargeAmountValidator.validateCustomAmount("0.00"))
    }

    @Test
    fun `自定义金额格式非法被拦截`() {
        assertEquals(RechargeAmountError.EMPTY, RechargeAmountValidator.validateCustomAmount(""))
        assertEquals(RechargeAmountError.EMPTY, RechargeAmountValidator.validateCustomAmount("   "))
        assertEquals(RechargeAmountError.INVALID_FORMAT, RechargeAmountValidator.validateCustomAmount("1.234"))
        assertEquals(RechargeAmountError.INVALID_FORMAT, RechargeAmountValidator.validateCustomAmount("-1"))
    }

    @Test
    fun `未选金额与未选自定义金额给出区分文案`() {
        assertEquals(
            RechargeAmountError.NOT_SELECTED,
            RechargeAmountValidator.resolve(isCustomAmountSelected = false, presetAmount = null, customAmountRaw = "").error
        )
        assertEquals(
            RechargeAmountError.EMPTY,
            RechargeAmountValidator.resolve(isCustomAmountSelected = true, presetAmount = null, customAmountRaw = "").error
        )
    }
}
