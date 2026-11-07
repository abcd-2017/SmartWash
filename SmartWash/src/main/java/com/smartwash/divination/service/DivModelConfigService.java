package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivModelConfig;
import com.smartwash.divination.entity.DivPlatformSetting;
import com.smartwash.divination.llm.OpenAiCompatClient;
import com.smartwash.divination.llm.OpenAiCompatProvider;
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
    private final OpenAiCompatClient llmClient;
    private final DivPlatformSettingService platformSettingService;

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
     * 连通性测试（不保存）。真实 1-token 试呼：max_tokens=1、stream=false、10s 超时。
     * 同时服务管理端 POST /admin/div/models/{id}/test 与用户端 POST /web/auth/div/settings/api/test。
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
        // 真实 1-token chat 试呼（WebClient，超时收紧）
        return llmClient.probeConnectivity(baseUrl, apiKey, modelId);
    }

    /**
     * 按平台模型目录 ID 试呼：解密密钥 → 真实 1-token 试呼 → 回填测试留痕。
     * 供管理端 POST /admin/div/models/{id}/test 使用（接口签名不变）。
     */
    public boolean testConnectivityById(Long id) {
        DivModelConfig config = modelConfigMapper.selectById(id);
        if (config == null || !StringUtils.hasText(config.getBaseUrl())
                || !StringUtils.hasText(config.getApiKeyCipher())) {
            return false;
        }
        boolean ok;
        try {
            String plainKey = cipher.decrypt(config.getApiKeyCipher());
            ok = testConnectivity(config.getBaseUrl(), plainKey, config.getModelId());
        } catch (Exception e) {
            // 解密失败（主密钥不匹配/密文损坏）按试呼失败处理，不向管理端抛异常
            log.warn("平台模型密钥解密失败, id: {}, 原因: {}", id, e.getMessage());
            ok = false;
        }
        // 回填测试留痕（last_test_at / last_test_ok）
        DivModelConfig patch = new DivModelConfig();
        patch.setId(id);
        patch.setLastTestOk(ok ? 1 : 0);
        patch.setLastTestAt(LocalDateTime.now());
        modelConfigMapper.updateById(patch);
        log.info("平台模型连通性测试, id: {}, name: {}, 结果: {}", id, config.getName(), ok);
        return ok;
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

    /**
     * 解析平台默认模型供应商（解读链路优先级 2，由 LlmGateway 以 Supplier 懒加载调用）。
     *
     * <p>读 {@code div_platform_setting}（单例 id=1）的 {@code default_model_id}，
     * 不可用（未配置/未启用/无密钥/解密失败/熔断由网关判）时用 {@code fallback_model_id}；
     * 两者都不可用返回 null，由 LlmGateway 落到 yml bootstrap 兜底。</p>
     *
     * @return 平台默认供应商（source="platform"）；平台未配置可用模型时返回 null
     */
    public OpenAiCompatProvider resolvePlatformDefaultProvider() {
        DivPlatformSetting setting = platformSettingService.getSetting();
        OpenAiCompatProvider primary = buildModelProvider(setting.getDefaultModelId());
        if (primary != null) {
            return primary;
        }
        return buildModelProvider(setting.getFallbackModelId());
    }

    /**
     * 按 div_model_config 主键构造运行时供应商。
     * 跳过不可用配置：不存在、未启用、base_url/model_id 缺失、api_key_cipher 为空
     * （种子数据 key 留空，管理端录入后才可用）、密文解密失败。
     */
    private OpenAiCompatProvider buildModelProvider(Long modelId) {
        if (modelId == null || modelId <= 0) {
            return null;
        }
        DivModelConfig config = modelConfigMapper.selectById(modelId);
        if (config == null
                || config.getEnabled() == null || config.getEnabled() != 1
                || !StringUtils.hasText(config.getBaseUrl())
                || !StringUtils.hasText(config.getModelId())
                || !StringUtils.hasText(config.getApiKeyCipher())) {
            log.debug("平台模型配置不可用，跳过, modelId: {}", modelId);
            return null;
        }
        try {
            String plainKey = cipher.decrypt(config.getApiKeyCipher());
            int priority = config.getPriority() != null ? config.getPriority() : 100;
            return new OpenAiCompatProvider(config.getName(), config.getBaseUrl(), plainKey,
                    config.getModelId(), priority, true, "platform", config.getId(),
                    "platform:" + config.getId());
        } catch (Exception e) {
            // 解密失败（主密钥不匹配/密文损坏）：跳过该配置，默认模型落备用/兜底
            log.warn("平台模型密钥解密失败，跳过该配置, modelId: {}, 原因: {}", modelId, e.getMessage());
            return null;
        }
    }
}
