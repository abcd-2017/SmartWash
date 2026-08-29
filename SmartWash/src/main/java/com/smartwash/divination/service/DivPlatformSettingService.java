package com.smartwash.divination.service;

import com.smartwash.divination.entity.DivPlatformSetting;
import com.smartwash.divination.mapper.DivPlatformSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 平台全局设置服务（单例 id=1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivPlatformSettingService {

    private final DivPlatformSettingMapper settingMapper;

    /**
     * 获取平台设置（单例，不存在则初始化默认值）。
     */
    public DivPlatformSetting getSetting() {
        DivPlatformSetting setting = settingMapper.selectById(1L);
        if (setting == null) {
            setting = new DivPlatformSetting();
            setting.setId(1L);
            setting.setDefaultModelId(0L);
            setting.setFallbackModelId(null);
            setting.setByokEnabled(0);
            setting.setByokDailyLimit(50);
            setting.setPlatformDailyLimit(20);
            settingMapper.insert(setting);
            log.info("初始化平台默认设置");
        }
        return setting;
    }

    /**
     * 更新平台设置。
     */
    public void updateSetting(DivPlatformSetting setting) {
        setting.setId(1L);
        settingMapper.updateById(setting);
        log.info("更新平台设置, defaultModelId: {}, byokEnabled: {}",
                setting.getDefaultModelId(), setting.getByokEnabled());
    }
}
