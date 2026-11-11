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
     * 获取平台设置（单例，不存在则初始化默认值：功能开启、每用户每日 50 次）。
     */
    public DivPlatformSetting getSetting() {
        DivPlatformSetting setting = settingMapper.selectById(1L);
        if (setting == null) {
            setting = new DivPlatformSetting();
            setting.setId(1L);
            setting.setDivUseEnabled(1);
            setting.setByokDailyLimit(50);
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
        log.info("更新平台设置, divUseEnabled: {}, byokDailyLimit: {}",
                setting.getDivUseEnabled(), setting.getByokDailyLimit());
    }
}
