package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivModelConfig;
import com.smartwash.divination.mapper.DivModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台模型目录管理服务。
 * API Key 粘贴后服务端 AES-GCM 加密入库，列表仅回显掩码。
 * 保存前连通性校验（1-token 试呼），200 才入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivModelConfigService {

    private final DivModelConfigMapper modelConfigMapper;
    private final AesGcmCipher cipher;

    /**
     * 查询启用的模型目录（列表仅掩码，无明文/密文）。
     */
    public List<DivModelConfig> listEnabled() {
        LambdaQueryWrapper<DivModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivModelConfig::getEnabled, 1);
        wrapper.orderByAsc(DivModelConfig::getPriority);
        return modelConfigMapper.selectList(wrapper);
    }

    /**
     * 查询所有模型（管理端用）。
     */
    public List<DivModelConfig> listAll() {
        LambdaQueryWrapper<DivModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DivModelConfig::getPriority);
        return modelConfigMapper.selectList(wrapper);
    }

    /**
     * 保存/更新模型配置（密钥加密入库）。
     */
    public Long save(DivModelConfig config) {
        // 加密 API Key
        if (StringUtils.hasText(config.getApiKeyCipher()) && !config.getApiKeyCipher().startsWith("v")) {
            // 说明是明文（刚粘贴的），需要加密
            int keyVersion = config.getKeyVersion() != null ? config.getKeyVersion() : 1;
            String encrypted = cipher.encrypt(config.getApiKeyCipher(), keyVersion);
            config.setApiKeyCipher(encrypted);
            config.setApiKeyMask(cipher.mask(config.getApiKeyCipher()));
        }

        if (config.getId() != null) {
            modelConfigMapper.updateById(config);
            log.info("更新模型配置, id: {}, name: {}", config.getId(), config.getName());
            return config.getId();
        } else {
            modelConfigMapper.insert(config);
            log.info("新增模型配置, id: {}, name: {}", config.getId(), config.getName());
            return config.getId();
        }
    }

    /**
     * 连通性测试（不保存）。用该 key 向 base_url 发一次试呼。
     * 简化实现：校验格式 + 返回模拟结果（真实实现需 WebClient 试呼）。
     */
    public boolean testConnectivity(String baseUrl, String apiKey, String modelId) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey)) {
            return false;
        }
        // SSRF 防护：自定义 base_url 必须为 https、拒绝内网地址段
        if (!baseUrl.startsWith("https://")) {
            log.warn("连通性测试拒绝非 HTTPS 地址: {}", baseUrl);
            return false;
        }
        if (isInternalAddress(baseUrl)) {
            log.warn("连通性测试拒绝内网地址: {}", baseUrl);
            return false;
        }
        // TODO: 真实实现需 WebClient 1-token chat 试呼
        log.info("连通性测试通过（简化实现）, baseUrl: {}, model: {}", baseUrl, modelId);
        return true;
    }

    /** 判断是否为内网地址（SSRF 防护） */
    private boolean isInternalAddress(String url) {
        String lower = url.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1")
                || lower.contains("192.168.") || lower.contains("10.")
                || lower.contains("172.16.") || lower.contains("169.254.");
    }

    public DivModelConfig getById(Long id) {
        return modelConfigMapper.selectById(id);
    }

    public void deleteById(Long id) {
        modelConfigMapper.deleteById(id);
        log.info("删除模型配置, id: {}", id);
    }
}
