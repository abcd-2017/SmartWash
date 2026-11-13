package com.smartwash.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.SysConfig;
import com.smartwash.mapper.SysConfigMapper;
import com.smartwash.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 系统参数配置服务实现。
 *
 * <p>读取走进程内缓存（参数量个位数、写极少读低频，简单 Map 即可，不引入 Redis 缓存链）；
 * 更新落库后清空缓存，业务方下一次读取即拿到新值，重启不丢（真源在 sys_config 表）。</p>
 */
@Slf4j
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements ISysConfigService {

    /** 数值型参数键：更新时校验必须为正整数，防止误填导致业务逻辑失效 */
    private static final Set<String> NUMERIC_KEYS = Set.of(
            "order.payTimeoutMinutes",
            "login.maxAttempts",
            "login.lockoutMinutes",
            "login.ipMaxAttempts",
            "login.ipLockoutMinutes"
    );

    private static final Pattern POSITIVE_INT_PATTERN = Pattern.compile("^[1-9]\\d*$");

    /** 参数键 → 值缓存；更新/初始化时失效 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public List<SysConfig> getAllConfigs() {
        return this.list();
    }

    @Override
    public int getIntValue(String key, int defaultValue) {
        String value = cache.computeIfAbsent(key, k -> {
            SysConfig config = this.lambdaQuery().eq(SysConfig::getConfigKey, k).one();
            return config != null ? config.getConfigValue() : null;
        });
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                log.warn("系统参数 {} 值非正整数({})，回退默认 {}", key, value, defaultValue);
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("系统参数 {} 值非法({})，回退默认 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean updateValue(Long id, String configValue, String remark) {
        SysConfig config = this.getById(id);
        if (config == null) {
            return false;
        }
        if (NUMERIC_KEYS.contains(config.getConfigKey())) {
            if (!StringUtils.hasText(configValue) || !POSITIVE_INT_PATTERN.matcher(configValue.trim()).matches()) {
                throw new IllegalArgumentException("该参数必须为正整数");
            }
        }
        config.setConfigValue(configValue.trim());
        if (remark != null) {
            config.setRemark(remark);
        }
        boolean ok = this.updateById(config);
        if (ok) {
            cache.clear();
            log.info("系统参数已更新并生效, key: {}, value: {}", config.getConfigKey(), config.getConfigValue());
        }
        return ok;
    }
}
