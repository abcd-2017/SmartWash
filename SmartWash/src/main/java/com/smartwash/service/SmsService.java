package com.smartwash.service;

/**
 * 短信服务接口
 * 集成真实 SMS 服务时实现此接口（如阿里云短信、腾讯云短信）
 */
public interface SmsService {

    /**
     * 发送短信验证码
     *
     * @param phoneNumber 手机号
     * @param code        验证码
     * @return 是否发送成功
     */
    boolean sendCaptcha(String phoneNumber, String code);
}
