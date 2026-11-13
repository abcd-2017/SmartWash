package com.smartwash.divination.service;

import com.smartwash.common.crypto.GcmCipherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 观象台 AES-256-GCM 加密工具（主密钥：DIV_MASTER_KEY 环境变量）。
 *
 * 加解密逻辑统一收敛于 {@link GcmCipherService}，本类仅绑定占卜域主密钥；
 * 既有调用方（模型目录 / 用户 BYOK 配置）注入类型与行为不变。
 */
@Component
public class AesGcmCipher extends GcmCipherService {

    public AesGcmCipher(@Value("${div.master-key-env:DIV_MASTER_KEY}") String masterKeyEnv) {
        super(masterKeyEnv);
    }
}
