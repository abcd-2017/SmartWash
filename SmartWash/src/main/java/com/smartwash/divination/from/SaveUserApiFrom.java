package com.smartwash.divination.from;

import lombok.Data;

import java.io.Serializable;

/**
 * 保存/更换用户 BYOK 配置请求 DTO（PUT /web/auth/div/settings/api），双模式二选一：
 *
 * <ul>
 *   <li>目录模式：{@code modelConfigId} 指向平台模型目录条目，接入点/模型名由服务端从目录取，
 *       客户端只填自己的 API Key（base_url 后台配置好，用户不输入 url）；</li>
 *   <li>自定义模式：{@code baseUrl} + {@code model} + {@code apiKey} 全量自填（OpenAI 兼容接入点）。</li>
 * </ul>
 *
 * <p>API Key 只写不读：明文仅经本请求体一次性传入，服务端 AES-GCM 加密落库，
 * 查询接口仅回掩码；更换只能覆盖，无法回看。</p>
 */
@Data
public class SaveUserApiFrom implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目录模式：平台模型目录条目 id（与 baseUrl/model 互斥，以本字段非空为准） */
    private Long modelConfigId;

    /** 自定义模式：OpenAI 兼容接入点（https 且非内网，服务端 SSRF 校验） */
    private String baseUrl;

    /** 自定义模式：模型名 */
    private String model;

    /** API Key 明文（两种模式均必填，只写不读） */
    private String apiKey;
}
