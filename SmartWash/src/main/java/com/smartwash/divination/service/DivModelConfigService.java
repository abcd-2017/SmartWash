package com.smartwash.divination.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.divination.entity.DivModelConfig;
import com.smartwash.divination.from.SaveModelFrom;
import com.smartwash.divination.llm.OpenAiCompatClient;
import com.smartwash.divination.mapper.DivModelConfigMapper;
import com.smartwash.divination.support.DivUrlSafetyValidator;
import com.smartwash.divination.vo.DivModelConfigAdminVo;
import com.smartwash.divination.vo.ModelVo;
import com.smartwash.divination.vo.VendorGroupVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台模型目录管理服务。
 *
 * <p>目录条目 = 厂商 + OpenAI 兼容接入点 + 模型标识，供用户端两级选择（BYOK）。
 * 密钥由用户自带：目录条目 api_key_cipher 恒为空串占位，用户保存配置时以自己的 key
 * 按目录接入点试呼后落 {@code div_user_api_config}（密钥消费链路见
 * {@link DivUserApiConfigService#resolveUserProvider}）。</p>
 *
 * <p>管理端列表只回掩码/不含密文字段（{@link DivModelConfigAdminVo}），密文永不出接口；
 * 保存时明文 key 经 {@code SaveModelFrom.apiKey} 单向传入加密入库（仅管理端演示接入用）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivModelConfigService {

    private final DivModelConfigMapper modelConfigMapper;
    private final AesGcmCipher cipher;
    private final OpenAiCompatClient llmClient;
    /** 外呼 URL 安全校验（SSRF 边界，fail-closed） */
    private final DivUrlSafetyValidator urlSafetyValidator;

    /**
     * 查询启用的模型目录（用户端 /web/auth/div/models 用，按 priority 升序）。
     */
    public List<DivModelConfig> listEnabled() {
        LambdaQueryWrapper<DivModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DivModelConfig::getEnabled, 1);
        wrapper.orderByAsc(DivModelConfig::getPriority);
        return modelConfigMapper.selectList(wrapper);
    }

    /**
     * 组装树形目录（厂商为一级、模型为二级），供用户端设置页分步选择：
     * 一次请求返回整棵树（厂商 → 该厂商全部模型）。
     *
     * <p><b>性能模型</b>：单条 SQL（enabled=1 + ORDER BY priority）+ 内存 O(n) 分组，无 N+1；
     * 目录量级为「几十厂商 × 每厂商几到几十模型」（当前 80 条 / 11 厂商），全量 JSON
     * 约几十 KB，一次返回无压力。目录变更频率极低（管理端操作），当前不加缓存
     * （缓存一致性成本 &gt; 收益）；若未来量级上千条，可在本方法挂 {@code @Cacheable} 挂点
     * 并在管理端保存/删除处 {@code @CacheEvict}。</p>
     *
     * <p>厂商顺序 = 组内最小 priority（种子按厂商块 100 步进编号，天然有序）；
     * 模型顺序 = 行 priority 升序（SQL 已排，内存分组保持顺序）。</p>
     */
    public List<VendorGroupVo> listEnabledTree() {
        List<DivModelConfig> configs = listEnabled();
        // LinkedHashMap 保持 SQL 返回的厂商首次出现顺序（= 组内最小 priority 升序）
        Map<String, VendorGroupVo> groups = new LinkedHashMap<>();
        for (DivModelConfig config : configs) {
            VendorGroupVo group = groups.computeIfAbsent(config.getVendor(), vendor -> {
                VendorGroupVo g = new VendorGroupVo();
                g.setVendor(vendor);
                g.setProvider(config.getProvider());
                g.setBaseUrl(config.getBaseUrl());
                g.setPriority(config.getPriority());
                g.setModels(new ArrayList<>());
                return g;
            });
            ModelVo model = new ModelVo();
            model.setId(config.getId());
            model.setName(config.getName());
            model.setModelId(config.getModelId());
            model.setPriority(config.getPriority());
            model.setActive(Boolean.FALSE);
            group.getModels().add(model);
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * 分页查询模型（管理端用，条件由调用方组装）；VO 裁剪在 Controller 层完成。
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<DivModelConfig> listPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<DivModelConfig> page,
            LambdaQueryWrapper<DivModelConfig> wrapper) {
        return modelConfigMapper.selectPage(page, wrapper);
    }

    /** 实体 → 管理端 VO（裁剪密文与主密钥版本，密文永不出接口） */
    public DivModelConfigAdminVo toAdminVo(DivModelConfig config) {
        DivModelConfigAdminVo vo = new DivModelConfigAdminVo();
        vo.setId(config.getId());
        vo.setVendor(config.getVendor());
        vo.setName(config.getName());
        vo.setProvider(config.getProvider());
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModelId(config.getModelId());
        vo.setApiKeyMask(config.getApiKeyMask());
        vo.setPriority(config.getPriority());
        vo.setEnabled(config.getEnabled());
        vo.setLastTestAt(config.getLastTestAt());
        vo.setLastTestOk(config.getLastTestOk());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    /**
     * 保存/更新模型目录条目。明文 key 非空时加密入库，编辑留空表示保留原密文。
     */
    public Long save(SaveModelFrom from) {
        DivModelConfig config;
        if (from.getId() != null) {
            config = modelConfigMapper.selectById(from.getId());
            if (config == null) {
                throw new IllegalArgumentException("模型目录条目不存在");
            }
        } else {
            config = new DivModelConfig();
        }
        config.setVendor(from.getVendor());
        config.setName(from.getName());
        config.setProvider(StringUtils.hasText(from.getProvider()) ? from.getProvider() : "openai_compat");
        config.setBaseUrl(from.getBaseUrl());
        config.setModelId(from.getModelId());
        if (from.getPriority() != null) {
            config.setPriority(from.getPriority());
        } else if (config.getPriority() == null) {
            config.setPriority(100);
        }
        if (from.getEnabled() != null) {
            config.setEnabled(from.getEnabled());
        } else if (config.getEnabled() == null) {
            config.setEnabled(1);
        }

        // 明文 key 单向加密入库；编辑留空（null/空串）保留原密文不动
        if (StringUtils.hasText(from.getApiKey())) {
            int keyVersion = config.getKeyVersion() != null ? config.getKeyVersion() : 1;
            config.setApiKeyCipher(cipher.encrypt(from.getApiKey(), keyVersion));
            config.setApiKeyMask(cipher.mask(from.getApiKey()));
        } else if (config.getApiKeyCipher() == null) {
            // 目录模式：密钥由用户自带，空串占位（列 NOT NULL）
            config.setApiKeyCipher("");
            config.setApiKeyMask("");
        }

        if (config.getId() != null) {
            modelConfigMapper.updateById(config);
            log.info("更新模型目录条目, id: {}, vendor: {}, name: {}", config.getId(), config.getVendor(), config.getName());
        } else {
            modelConfigMapper.insert(config);
            log.info("新增模型目录条目, id: {}, vendor: {}, name: {}", config.getId(), config.getVendor(), config.getName());
        }
        return config.getId();
    }

    /**
     * 连通性测试（不保存）。真实 1-token 试呼：max_tokens=1、stream=false、10s 超时。
     * 同时服务管理端 POST /admin/div/models/{id}/test 与用户端 POST /web/auth/div/settings/api/test。
     */
    public boolean testConnectivity(String baseUrl, String apiKey, String modelId) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey)) {
            return false;
        }
        // SSRF 校验（fail-closed）：结构 + DNS 全解析逐 IP 判定；拒绝原因以业务异常透传
        urlSafetyValidator.validate(baseUrl);
        // 真实 1-token chat 试呼（WebClient，超时收紧）
        return llmClient.probeConnectivity(baseUrl, apiKey, modelId);
    }

    /**
     * 按目录条目 id 试呼：解密密钥（条目录入了 key 时）→ 真实 1-token 试呼 → 回填测试留痕。
     * 目录模式条目（key 空占位）无 key 可用，返回 false——BYOK 下的真实试呼在用户保存链路。
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
            log.warn("模型目录条目密钥解密失败, id: {}, 原因: {}", id, e.getMessage());
            ok = false;
        }
        // 回填测试留痕（last_test_at / last_test_ok）
        DivModelConfig patch = new DivModelConfig();
        patch.setId(id);
        patch.setLastTestOk(ok ? 1 : 0);
        patch.setLastTestAt(LocalDateTime.now());
        modelConfigMapper.updateById(patch);
        log.info("模型目录条目连通性测试, id: {}, vendor: {}, name: {}, 结果: {}",
                id, config.getVendor(), config.getName(), ok);
        return ok;
    }

    public DivModelConfig getById(Long id) {
        return modelConfigMapper.selectById(id);
    }

    public void deleteById(Long id) {
        modelConfigMapper.deleteById(id);
        log.info("删除模型目录条目, id: {}", id);
    }
}
