package com.smartwash.toolbox.toolkit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短码生成器测试：长度恒为 7、字符集不越界（54 字符去混淆字母表）、批量生成无重复。
 */
@DisplayName("ShortCodeGenerator 随机码与别名校验测试")
class ShortCodeGeneratorTest {

    /** 与实现保持一致的去混淆字母表（剔除 0 O 1 l I J L j 等易混字符） */
    private static final String ALPHABET = "23456789abcdefghkmnpqrstuvwxyzABCDEFGHKLMNPQRSTUVWXYZ";

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    @DisplayName("随机码长度恒为 7")
    void randomCode_alwaysSevenChars() {
        for (int i = 0; i < 1000; i++) {
            assertEquals(7, generator.randomCode().length(), "随机码长度必须恒为 7");
        }
    }

    @Test
    @DisplayName("随机码字符集不越界（全部落在去混淆字母表内，不含易混字符）")
    void randomCode_charsetWithinAlphabet() {
        for (int i = 0; i < 1000; i++) {
            for (char c : generator.randomCode().toCharArray()) {
                assertTrue(ALPHABET.indexOf(c) >= 0, "字符 " + c + " 不在去混淆字母表内");
            }
        }
    }

    @Test
    @DisplayName("批量生成无重复（1 万个码全部唯一）")
    void randomCode_batchNoDuplicate() {
        Set<String> codes = new HashSet<>();
        int batch = 10_000;
        for (int i = 0; i < batch; i++) {
            codes.add(generator.randomCode());
        }
        assertEquals(batch, codes.size(), "批量生成的短码必须全部唯一");
    }

    @Test
    @DisplayName("自定义别名格式校验：4-16 位字母数字及 -_")
    void validCustomCode() {
        // 合法
        assertTrue(generator.validCustomCode("abcd"));
        assertTrue(generator.validCustomCode("a_b-c1"));
        assertTrue(generator.validCustomCode("0123456789abcdef"), "16 位上限合法");
        assertTrue(generator.validCustomCode("My-Code_99"));
        // 非法：长度
        assertFalse(generator.validCustomCode("abc"), "3 位低于下限");
        assertFalse(generator.validCustomCode("0123456789abcdefg"), "17 位超过上限");
        // 非法：字符集
        assertFalse(generator.validCustomCode("ab cd"));
        assertFalse(generator.validCustomCode("短码"));
        assertFalse(generator.validCustomCode("ab.c"));
        assertFalse(generator.validCustomCode("ab#c"));
        // 非法：空与 null
        assertFalse(generator.validCustomCode(""));
        assertFalse(generator.validCustomCode(null));
    }
}
