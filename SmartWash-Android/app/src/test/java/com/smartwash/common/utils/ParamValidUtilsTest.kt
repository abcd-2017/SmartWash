package com.smartwash.common.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ParamValidUtils] JVM 单测（锚点）：
 * - [isValidPhone] 正则 `^1[3-9]\d{9}$` 全分支（合法号段边界、位数、非数字字符、
 *   非 1 开头、`+86` 前缀与前导/尾随空格、空串）。
 *
 * 注：原提交附带的 isValidPassword 段落引用了从未存在于 ParamValidUtils 的 API
 * （密码校验实现在 feature/toolbox 的 DeliveryRules.isValidPassword），导致 :app
 * 单测编译失败，此处移除该段落以恢复测试可编译（本地验证用修复）。
 */
class ParamValidUtilsTest {

    // ---------- isValidPhone ----------

    @Test
    fun `合法手机号通过校验`() {
        assertTrue(isValidPhone("13812345678"))
        assertTrue(isValidPhone("13000000000")) // 第二位下界 3
        assertTrue(isValidPhone("13999999999"))
        assertTrue(isValidPhone("19912345678")) // 第二位上界 9
    }

    @Test
    fun `第二位非 3-9 被拦截`() {
        assertFalse(isValidPhone("12012345678")) // 第二位 2
        assertFalse(isValidPhone("11012345678")) // 第二位 1
        assertFalse(isValidPhone("10012345678")) // 第二位 0
    }

    @Test
    fun `位数不足或超长被拦截`() {
        assertFalse(isValidPhone("1381234567")) // 10 位
        assertFalse(isValidPhone("138123456")) // 9 位
        assertFalse(isValidPhone("138123456789")) // 12 位
    }

    @Test
    fun `含非数字字符被拦截`() {
        assertFalse(isValidPhone("1381234567a"))
        assertFalse(isValidPhone("138-1234-5678"))
        assertFalse(isValidPhone("138 1234 5678"))
        assertFalse(isValidPhone("1381234567+"))
    }

    @Test
    fun `非 1 开头与前导加号空格不归一化`() {
        assertFalse(isValidPhone("23812345678"))
        assertFalse(isValidPhone("03812345678"))
        assertFalse(isValidPhone("+8613812345678"))
        assertFalse(isValidPhone(" 13812345678"))
        assertFalse(isValidPhone("13812345678 "))
    }

    @Test
    fun `空串与纯空白被拦截`() {
        assertFalse(isValidPhone(""))
        assertFalse(isValidPhone("   "))
    }
}
