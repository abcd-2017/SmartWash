package com.smartwash.divination.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 应用层加密工具。
 *
 * 每条记录独立 12B 随机 IV，128-bit 认证标签；
 * 密文列格式 v{keyVersion}:{base64(iv)}:{base64(ciphertext+tag)}。
 *
 * 主密钥：DIV_MASTER_KEY 环境变量（32 字节 base64），不入库、不入 git。
 */
@Slf4j
@Component
public class AesGcmCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    /** 当前主密钥版本 */
    @Value("${div.master-key-env:DIV_MASTER_KEY}")
    private String masterKeyEnv;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 加密明文 API Key。
     *
     * @param plaintext 明文
     * @param keyVersion 主密钥版本
     * @return 密文字符串 v{ver}:{iv}:{ct}
     */
    public String encrypt(String plaintext, int keyVersion) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            SecretKey key = loadMasterKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v" + keyVersion + ":" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            log.error("AES-GCM 加密失败", e);
            throw new IllegalStateException("密钥加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param cipherText 密文字符串 v{ver}:{iv}:{ct}
     * @return 明文
     */
    public String decrypt(String cipherText) {
        try {
            String[] parts = cipherText.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("密文格式错误");
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            SecretKey key = loadMasterKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES-GCM 解密失败", e);
            throw new IllegalStateException("密钥解密失败", e);
        }
    }

    /**
     * 生成掩码（前 3 后 4）：sk-abc123xyz → sk-****23xyz
     */
    public String mask(String apiKey) {
        if (apiKey == null || apiKey.length() <= 7) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private SecretKey loadMasterKey() {
        String key = System.getenv(masterKeyEnv);
        if (key == null || key.isBlank()) {
            // 开发期兜底：使用固定 demo 密钥（生产必须通过环境变量注入）
            key = "ZGVtby1tYXN0ZXItZXkyNTYtYml0LWtleQ=="; // demo-master-key-256-bit-key 的 base64
        }
        byte[] decoded = Base64.getDecoder().decode(key);
        if (decoded.length != 32) {
            throw new IllegalStateException("主密钥长度必须为 32 字节（256 位），当前: " + decoded.length);
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
