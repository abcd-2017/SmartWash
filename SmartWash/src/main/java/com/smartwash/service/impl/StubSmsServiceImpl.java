package com.smartwash.service.impl;

import com.smartwash.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 短信服务桩实现（开发环境）
 * 生产环境请替换为真实的 SMS 服务实现（如阿里云短信、腾讯云短信）
 */
@Slf4j
@Service
@ConditionalOnMissingBean(SmsService.class)
public class StubSmsServiceImpl implements SmsService {

    @Override
    public boolean sendCaptcha(String phoneNumber, String code) {
        log.info("[桩实现] 短信验证码已生成, phone: {}, code: {}", phoneNumber, code);
        // 开发环境：验证码已存入 Redis，此处仅记录日志
        // 生产环境：替换为真实 SMS SDK 调用
        return true;
    }
}
