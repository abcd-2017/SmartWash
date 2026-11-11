package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivModelConfig;
import com.smartwash.divination.entity.DivUserApiConfig;
import com.smartwash.divination.from.SaveUserApiFrom;
import com.smartwash.divination.llm.OpenAiCompatProvider;
import com.smartwash.divination.mapper.DivUserApiConfigMapper;
import com.smartwash.divination.support.DivUrlSafetyValidator;
import com.smartwash.divination.vo.UserApiConfigVo;
import com.smartwash.exception.CustomExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户模型配置（BYOK）服务。
 *
 * <p>平台零兜底：每位用户必须自行配置模型（选目录条目或全自定义）才能发起解读；
 * 保存前真实 1-token 试呼，通过才 AES-GCM 加密入库。apiKey 只写不读：
 * 查询仅回掩码，更换只能覆盖更新。</p>
 *
 * <p>双模式（{@link SaveUserApiFrom}）：
 * 目录模式 modelConfigId 非空——接入点/模型名服务端从 {@code div_model_config} 取，用户只填 key；
 * 自定义模式——baseUrl/model/apiKey 全量自填（https + 非内网 SSRF 校验）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivUserApiConfigService {

    private final DivUserApiConfigMapper userApiConfigMapper;
    private final AesGcmCipher cipher;
    private final DivModelConfigService modelConfigService;
    private final DivPlatformSettingService platformSettingService;
    private final DivUrlSafetyValidator urlSafetyValidator;

    /**
     * 获取用户 BYOK 配置（仅掩码，无明文/密文）。
     * 同时下发观星台功能总开关（divUseEnabled 0/1），供客户端决定是否隐藏解读入口。
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
        vo.setDivUseEnabled(platformSettingService.getSetting().getDivUseEnabled());
        if (config.getModelConfigId() != null) {
            // 目录模式：回显厂商/模型名（目录被删时降级为快照，不阻断展示）
            DivModelConfig catalog = modelConfigService.getById(config.getModelConfigId());
            vo.setModelConfigId(config.getModelConfigId());
            if (catalog != null) {
                vo.setVendor(catalog.getVendor());
                vo.setModelName(catalog.getName());
            }
        }
        if (StringUtils.hasText(config.getCustomBaseUrl()) || StringUtils.hasText(config.getCustomModel())) {
            vo.setCustomBaseUrl(config.getCustomBaseUrl());
            vo.setCustomModel(config.getCustomModel());
            if (vo.getModelName() == null) {
                vo.setModelName(config.getCustomModel());
            }
        }
        return vo;
    }

    /**
     * 观星台 AI 解读总开关（div_platform_setting.div_use_enabled，缺省按关闭处理）。
     * 供保存/试呼/解读链路服务端强制，以及用户端控制器复用。
     */
    public boolean isDivUseEnabled() {
        Integer divUseEnabled = platformSettingService.getSetting().getDivUseEnabled();
        return divUseEnabled != null && divUseEnabled == 1;
    }

    /**
     * 保存/更换用户模型配置（功能开关强制 → 按模式解析接入点 → 真实 1-token 试呼 → 加密入库）。
     * 开关关闭、接入点不合法或试呼不通过时抛业务异常（经全局 advice 转失败信封），不写库。
     */
    public void saveUserConfig(Long userId, SaveUserApiFrom from) {
        // 功能总开关服务端强制：关闭后不允许保存/更换（不写库）
        if (!isDivUseEnabled()) {
            throw new CustomExceptions("观星台 AI 解读暂未开放");
        }
        if (!StringUtils.hasText(from.getApiKey())) {
            throw new CustomExceptions("API Key 不能为空");
        }

        String baseUrl;
        String model;
        Long modelConfigId = null;
        if (from.getModelConfigId() != null) {
            // 目录模式：接入点/模型名服务端从目录取，用户不可输入 url
            DivModelConfig catalog = modelConfigService.getById(from.getModelConfigId());
            if (catalog == null || catalog.getEnabled() == null || catalog.getEnabled() != 1
                    || !StringUtils.hasText(catalog.getBaseUrl()) || !StringUtils.hasText(catalog.getModelId())) {
                throw new CustomExceptions("所选模型不可用，请重新选择");
            }
            baseUrl = catalog.getBaseUrl();
            model = catalog.getModelId();
            modelConfigId = catalog.getId();
        } else if (StringUtils.hasText(from.getBaseUrl()) && StringUtils.hasText(from.getModel())) {
            // 自定义模式：全量自填
            baseUrl = from.getBaseUrl();
            model = from.getModel();
        } else {
            throw new CustomExceptions("请选择平台模型或填写自定义接入点与模型名");
        }

        // SSRF 校验（fail-closed）：结构 + DNS 全解析逐 IP 判定，拒绝原因直接透传用户
        urlSafetyValidator.validate(baseUrl);

        // 保存前真实试呼（1-token 非流式，10s 超时），通过才允许写库
        if (!modelConfigService.testConnectivity(baseUrl, from.getApiKey(), model)) {
            throw new CustomExceptions("连通性校验失败：请检查模型与 API Key 是否正确");
        }

        // 加密
        String encrypted = cipher.encrypt(from.getApiKey(), 1);
        String mask = cipher.mask(from.getApiKey());

        LambdaQueryWrapper<DivUserApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivUserApiConfig::getUserId, userId);
        DivUserApiConfig config = userApiConfigMapper.selectOne(wrapper);

        if (config == null) {
            config = new DivUserApiConfig();
            config.setUserId(userId);
        }
        config.setModelConfigId(modelConfigId);
        // custom_base_url/custom_model 存快照供回显：目录模式存目录值（目录条目删除后仍可展示），自定义模式存用户输入
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
        log.info("保存用户模型配置, userId: {}, mode: {}, verified: true",
                userId, modelConfigId != null ? "catalog:" + modelConfigId : "custom");
    }

    /**
     * 删除用户模型配置。
     */
    public void deleteUserConfig(Long userId) {
        LambdaQueryWrapper<DivUserApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivUserApiConfig::getUserId, userId);
        userApiConfigMapper.delete(wrapper);
        log.info("删除用户模型配置, userId: {}", userId);
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
     * 解析当前用户可用的模型供应商（解读链路每请求调用，平台零兜底——无可用配置返回 null，
     * 由解读链路发 428 引导客户端去设置页）。
     *
     * <p>条件：{@code user_id=? AND enabled=1 AND verified=1}；必要字段缺失或密钥解密失败返回 null。
     * 目录模式按 modelConfigId 实时取目录接入点/模型名（目录停用/删除即不可用）；自定义模式取快照。
     * 明文 key 仅在本次请求内使用，日志绝不打印。</p>
     *
     * @param userId 用户 ID
     * @return 可用的供应商（source="user"）；无可用配置返回 null
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
        if (!StringUtils.hasText(config.getApiKeyCipher())) {
            log.debug("用户模型配置不完整（无密钥），本次解读不可用, userId: {}", userId);
            return null;
        }

        String baseUrl;
        String model;
        String displayName;
        if (config.getModelConfigId() != null) {
            // 目录模式：实时取目录条目（停用/删除即不可用，零兜底）
            DivModelConfig catalog = modelConfigService.getById(config.getModelConfigId());
            if (catalog == null || catalog.getEnabled() == null || catalog.getEnabled() != 1
                    || !StringUtils.hasText(catalog.getBaseUrl()) || !StringUtils.hasText(catalog.getModelId())) {
                log.debug("目录模式条目不可用（已删除/停用）, userId: {}, modelConfigId: {}",
                        userId, config.getModelConfigId());
                return null;
            }
            baseUrl = catalog.getBaseUrl();
            model = catalog.getModelId();
            displayName = catalog.getName();
        } else if (StringUtils.hasText(config.getCustomBaseUrl()) && StringUtils.hasText(config.getCustomModel())) {
            baseUrl = config.getCustomBaseUrl();
            model = config.getCustomModel();
            displayName = model;
        } else {
            log.debug("用户模型配置不完整（接入点/模型缺失）, userId: {}", userId);
            return null;
        }

        try {
            String plainKey = cipher.decrypt(config.getApiKeyCipher());
            // 熔断键按用户配置 id 隔离（不同用户同名模型互不污染，也不与目录同名模型互撞）
            return new OpenAiCompatProvider(displayName, baseUrl, plainKey,
                    model, 0, true, "user", config.getId(),
                    "user:" + config.getId());
        } catch (Exception e) {
            // 解密失败（主密钥不匹配/密文损坏）不阻断解读链路，按未配置处理
            log.warn("用户密钥解密失败，本次解读按未配置处理, userId: {}, 原因: {}", userId, e.getMessage());
            return null;
        }
    }
}
