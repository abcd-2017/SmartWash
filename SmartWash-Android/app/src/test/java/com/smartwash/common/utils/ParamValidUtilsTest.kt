package com.smartwash.common.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ParamValidUtils] JVM 单测（锚点）：
 * - [isValidPhone] 正则 `^1[3-9]\d{9}$` 全分支（合法号段边界、位数、非数字字符、
 *   非 1 开头、`+86` 前缀与前导/尾随空格、空串）；
 * - [isValidPassword] 长度 6-16 边界（下界 5/6、上界 16/17、空串、纯空白）。
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

    // ---------- isValidPassword ----------

    @Test
    fun `密码长度 6-16 位通过校验`() {
        assertTrue(isValidPassword("123456")) // 下界 6
        assertTrue(isValidPassword("1234567890123456")) // 上界 16
        assertTrue(isValidPassword("abcdef"))
        assertTrue(isValidPassword("a b c d e f")) // 空格计入长度
    }

    @Test
    fun `密码长度不足 6 位被拦截`() {
        assertFalse(isValidPassword(""))
        assertFalse(isValidPassword("1"))
        assertFalse(isValidPassword("12345"))
        assertFalse(isValidPassword("     ")) // 5 个空格
    }

    @Test
    fun `密码长度超过 16 位被拦截`() {
        assertFalse(isValidPassword("12345678901234567")) // 17 位
        assertFalse(isValidPassword("a".repeat(32)))
    }
}
