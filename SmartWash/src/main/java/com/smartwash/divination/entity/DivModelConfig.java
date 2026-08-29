package com.smartwash.divination.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台模型目录（管理端维护）。API Key 以 AES-256-GCM 密文存储，列表仅回显掩码。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_model_config")
public class DivModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 显示名，如 GLM-4.7 */
    private String name;

    private String provider;

    private String baseUrl;

    /** 供应商模型标识 */
    private String modelId;

    /** AES-256-GCM 密文 v{ver}:{iv}:{ct} */
    private String apiKeyCipher;

    /** 掩码 sk-****abc4 */
    private String apiKeyMask;

    /** 越小越优先 */
    private Integer priority;

    private Integer enabled;

    /** 加密主密钥版本 */
    private Integer keyVersion;

    private LocalDateTime lastTestAt;

    private Integer lastTestOk;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
