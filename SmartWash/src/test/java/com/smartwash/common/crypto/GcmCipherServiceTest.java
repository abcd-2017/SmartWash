package com.smartwash.common.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用 AES-256-GCM 加密内核单测：roundtrip、密文格式与随机 IV、掩码、篡改与格式错误拒绝。
 * 测试进程无对应环境变量，主密钥走 demo 兜底（与生产行为差异仅密钥来源）。
 */
class GcmCipherServiceTest {

    private final GcmCipherService cipher = new ToolboxGcmCipher("SMARTWASH_TEST_NON_EXIST_KEY");

    @Test
    void 加密解密roundtrip() {
        String plaintext = "宿舍 WiFi 密码：Campus#2026";
        String cipherText = cipher.encrypt(plaintext, 1);
        assertEquals(plaintext, cipher.decrypt(cipherText));
    }

    @Test
    void 密文格式与随机IV() {
        String cipherText = cipher.encrypt("同一段明文", 1);
        String[] parts = cipherText.split(":");
        assertEquals(3, parts.length);
        assertTrue(parts[0].equals("v1"), "版本段应为 v1");
        assertNotEquals(cipher.encrypt("同一段明文", 1), cipherText, "同明文两次加密密文必须不同（随机 IV）");
    }

    @Test
    void 掩码规则() {
        assertEquals("****", cipher.mask(null));
        assertEquals("****", cipher.mask("short"));
        assertEquals("sk-****3xyz", cipher.mask("sk-abc123xyz"));
    }

    @Test
    void 篡改密文拒绝解密() {
        String cipherText = cipher.encrypt("敏感内容", 1);
        String[] parts = cipherText.split(":");
        char[] ct = parts[2].toCharArray();
        ct[0] = ct[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + ":" + parts[1] + ":" + new String(ct);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void 非法密文格式拒绝() {
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("not-a-cipher"));
    }
}
