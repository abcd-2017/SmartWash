package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivUserApiConfig;
import com.smartwash.divination.mapper.DivUserApiConfigMapper;
import com.smartwash.divination.vo.UserApiConfigVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户自带 API 配置（BYOK）服务。
 * 保存前连通性校验 → AES-GCM 加密入库；列表仅回显掩码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivUserApiConfigService {

    private final DivUserApiConfigMapper userApiConfigMapper;
    private final AesGcmCipher cipher;

    /**
     * 获取用户 BYOK 配置（仅掩码，无明文/密文）。
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
        if (config.getModelConfigId() != null) {
            vo.setProviderName("平台预设");
        }
        vo.setCustomBaseUrl(config.getCustomBaseUrl());
        vo.setCustomModel(config.getCustomModel());
        return vo;
    }

    /**
     * 保存/更换 BYOK（连通性校验 → 加密入库）。
     */
    public void saveUserConfig(Long userId, String baseUrl, String model, String apiKey) {
        // 连通性校验（SSRF 防护：必须 https、拒绝内网）
        if (!isValidBaseUrl(baseUrl)) {
            throw new IllegalStateException("连通性校验失败：接入点必须为 HTTPS 且非内网地址");
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
}
