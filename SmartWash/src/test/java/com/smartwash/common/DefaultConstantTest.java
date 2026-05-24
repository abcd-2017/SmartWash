package com.smartwash.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultConstant 测试")
class DefaultConstantTest {

    @Test
    @DisplayName("生成随机密码长度应为12位")
    void generateDefaultPassword_length() {
        String password = DefaultConstant.generateDefaultPassword();
        assertEquals(12, password.length());
    }

    @Test
    @DisplayName("生成随机密码应包含合法字符")
    void generateDefaultPassword_complexity() {
        String password = DefaultConstant.generateDefaultPassword();
        String validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        for (char c : password.toCharArray()) {
            assertTrue(validChars.indexOf(c) >= 0, "密码包含非法字符: " + c);
        }
    }

    @Test
    @DisplayName("多次生成的密码应不同")
    void generateDefaultPassword_unique() {
        String password1 = DefaultConstant.generateDefaultPassword();
        String password2 = DefaultConstant.generateDefaultPassword();
        assertNotEquals(password1, password2);
    }

    @Test
    @DisplayName("常量值正确")
    void constants() {
        assertEquals("admin", DefaultConstant.ADMIN_USER_LOGIN_TYPE);
        assertEquals("user", DefaultConstant.USER_LOGIN_TYPE);
        assertEquals("captcha", DefaultConstant.CAPTCHA_CODE);
    }
}
