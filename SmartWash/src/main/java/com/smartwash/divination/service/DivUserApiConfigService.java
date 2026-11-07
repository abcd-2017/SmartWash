package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivUserApiConfig;
import com.smartwash.divination.llm.OpenAiCompatProvider;
import com.smartwash.divination.mapper.DivUserApiConfigMapper;
import com.smartwash.divination.vo.UserApiConfigVo;
import com.smartwash.exception.CustomExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户自带 API 配置（BYOK）服务。
 * 保存前真实试呼（1-token）→ AES-GCM 加密入库；列表仅回显掩码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivUserApiConfigService {

    private final DivUserApiConfigMapper userApiConfigMapper;
    private final AesGcmCipher cipher;
    private final DivModelConfigService modelConfigService;
    private final DivPlatformSettingService platformSettingService;

    /**
     * 获取用户 BYOK 配置（仅掩码，无明文/密文）。
     * 同时下发平台 BYOK 开关（byokEnabled 0/1），供客户端决定是否隐藏配置入口；
     * 平台关闭时已有配置仍原样回显（服务端在保存/试呼/解读链路强制拦截）。
     */
    public UserApiConfigVo getUserConfig(Long userId) {
        LambdaQueryWrapper<DivUserApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivUserApiConfig::getUserId, userId);
        DivUserApiConfig config = userApiConfigMapper.selectOne(wrapper);
        if (config == null) return null;

        UserApiConfigVo vo = new UserApiConfigVo();
        vo.setId(config.getId());
        vo.setApiKeyMask(config.getApiKeyMask());
        vo.setVerified(config.getVerified());
        vo.setEnabled(config.getEnabled());
        vo.setByokEnabled(platformSettingService.getSetting().getByokEnabled());
        if (config.getModelConfigId() != null) {
            vo.setProviderName("平台预设");
        }
        vo.setCustomBaseUrl(config.getCustomBaseUrl());
        vo.setCustomModel(config.getCustomModel());
        return vo;
    }

    /**
     * 平台 BYOK 开关（div_platform_setting.byok_enabled，缺省按关闭处理）。
     * 供保存/试呼链路服务端强制，以及用户端控制器复用。
     */
    public boolean isByokEnabled() {
        Integer byokEnabled = platformSettingService.getSetting().getByokEnabled();
        return byokEnabled != null && byokEnabled == 1;
    }

    /**
     * 保存/更换 BYOK（平台开关强制 → 真实 1-token 试呼 → 通过才加密入库）。
     * 平台关闭 BYOK、试呼不通过或接入点不合法时抛业务异常
     * （经全局 advice 转为 Result 失败信封），不写库。
     */
    public void saveUserConfig(Long userId, String baseUrl, String model, String apiKey) {
        // 平台开关服务端强制：关闭后不允许保存/更换 BYOK（不写库）
        if (!isByokEnabled()) {
            throw new CustomExceptions("平台已关闭自带模型功能");
        }

        // SSRF 防护：必须 https、拒绝内网
        if (!isValidBaseUrl(baseUrl)) {
            throw new CustomExceptions("连通性校验失败：接入点必须为 HTTPS 且非内网地址");
        }

        // 保存前真实试呼（1-token 非流式，10s 超时），通过才允许写库
        if (!modelConfigService.testConnectivity(baseUrl, apiKey, model)) {
            throw new CustomExceptions("连通性校验失败：请检查接入点、模型名称与 API Key 是否正确");
        }

        // 加密
        String encrypted = cipher.encrypt(apiKey, 1);
        String mask = cipher.mask(apiKey);

        LambdaQueryWrapper<DivUserApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivUserApiConfig::getUserId, userId);
        DivUserApiConfig config = userApiConfigMapper.selectOne(wrapper);

        if (config == null) {
            config = new DivUserApiConfig();
            config.setUserId(userId);
        }
        config.setCustomBaseUrl(baseUrl);
        config.setCustomModel(model);
        config.setApiKeyCipher(encrypted);
        config.setApiKeyMask(mask);
        config.setVerified(1);
        config.setLastTestAt(LocalDateTime.now());
        config.setEnabled(1);

        if (config.getId() != null) {
            userApiConfigMapper.updateById(config);
        } else {
            userApiConfigMapper.insert(config);
        }
        log.info("保存用户 BYOK 配置, userId: {}, verified: true", userId);
    }

    /**
     * 删除 BYOK。
     */
    public void deleteUserConfig(Long userId) {
        LambdaQueryWrapper<DivUserApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivUserApiConfig::getUserId, userId);
        userApiConfigMapper.delete(wrapper);
        log.info("删除用户 BYOK 配置, userId: {}", userId);
    }

    /** 校验 base_url 是否合法（SSRF 防护） */
    private boolean isValidBaseUrl(String url) {
        if (url == null || !url.startsWith("https://")) return false;
        String lower = url.toLowerCase();
        return !lower.contains("localhost") && !lower.contains("127.0.0.1")
                && !lower.contains("192.168.") && !lower.contains("10.")
                && !lower.contains("172.16.") && !lower.contains("169.254.");
    }

    /**
     * 获取用户 BYOK 配置实体（内部使用，含密文）。
     */
    public DivUserApiConfig getConfigEntity(Long userId) {
        LambdaQueryWrapper<DivUserApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivUserApiConfig::getUserId, userId);
        return userApiConfigMapper.selectOne(wrapper);
    }

    /**
     * 解析当前用户可用的 BYOK 供应商（解读链路调用，每请求一次）。
     *
     * <p>条件：{@code user_id=? AND enabled=1 AND verified=1}；不满足、必要字段缺失
     * （如仅关联平台预设 model_config_id 的旧数据）或密钥解密失败时返回 null，
     * 由调用方落到平台默认模型。明文 key 仅在本次请求内使用，日志绝不打印。</p>
     *
     * @param userId 用户 ID
     * @return 可用的 BYOK 供应商（source="user"）；不可用时返回 null
     */
    public OpenAiCompatProvider resolveUserProvider(Long userId) {
        LambdaQueryWrapper<DivUserApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivUserApiConfig::getUserId, userId)
                .eq(DivUserApiConfig::getEnabled, 1)
                .eq(DivUserApiConfig::getVerified, 1);
        DivUserApiConfig config = userApiConfigMapper.selectOne(wrapper);
        if (config == null) {
            return null;
        }
        // 必要字段不完整无法构造自定义接入点（含 model_config_id 预设场景），落平台默认
        if (!StringUtils.hasText(config.getApiKeyCipher())
                || !StringUtils.hasText(config.getCustomBaseUrl())
                || !StringUtils.hasText(config.getCustomModel())) {
            log.debug("用户 BYOK 配置不完整，本次解读不使用 BYOK, userId: {}", userId);
            return null;
        }
        try {
            String plainKey = cipher.decrypt(config.getApiKeyCipher());
            // name 取 custom_model（缺失兜底「用户BYOK」），熔断窗口按 name 聚合
            String name = StringUtils.hasText(config.getCustomModel())
                    ? config.getCustomModel() : "用户BYOK";
            return new OpenAiCompatProvider(name, config.getCustomBaseUrl(), plainKey,
                    config.getCustomModel(), 0, true, "user", config.getId());
        } catch (Exception e) {
            // 解密失败（主密钥不匹配/密文损坏）不阻断解读，落平台默认
            log.warn("用户 BYOK 密钥解密失败，本次解读落平台默认, userId: {}, 原因: {}",
                    userId, e.getMessage());
            return null;
        }
    }
}
