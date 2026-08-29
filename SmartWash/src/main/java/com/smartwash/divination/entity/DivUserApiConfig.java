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
 * 用户自带 API 配置（BYOK，一用户一条）。API Key 以 AES-256-GCM 密文存储。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_user_api_config")
public class DivUserApiConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 用平台预设供应商时引用 div_model_config.id */
    private Long modelConfigId;

    /** 完全自定义接入点时填 */
    private String customBaseUrl;

    private String customModel;

    /** AES-256-GCM 密文 */
    private String apiKeyCipher;

    /** 掩码 */
    private String apiKeyMask;

    private Integer keyVersion;

    /** 保存前试呼通过 */
    private Integer verified;

    private LocalDateTime lastTestAt;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
