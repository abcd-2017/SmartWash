package com.smartwash.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 工具箱 AES-256-GCM 加密工具（主密钥：TOOLBOX_MASTER_KEY 环境变量，与占卜域分钥）。
 *
 * 消费方：私密文本（阅后即焚）内容加密、物品标签联系方式加密。
 */
@Component
public class ToolboxGcmCipher extends GcmCipherService {

    public ToolboxGcmCipher(@Value("${toolbox.master-key-env:TOOLBOX_MASTER_KEY}") String masterKeyEnv) {
        super(masterKeyEnv);
    }
}
