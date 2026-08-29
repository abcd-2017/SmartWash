package com.smartwash.service.impl;

import com.smartwash.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 短信服务桩实现（开发环境）
 * 当前由 LoginController 注入调用：验证码仅在 debug 级日志输出（仅开发环境），
 * 严禁把验证码明文写入 info 级日志或返回给前端响应体。
 * 生产环境请替换为真实的 SMS 服务实现（如阿里云短信、腾讯云短信）：
 * 接入真实实现时按 profile/@Primary 装配替换本桩，禁止在此使用 @ConditionalOnMissingBean
 * ——该注解仅适用于自动配置类，放在组件扫描的 @Service 上会导致 Bean 不被注册、上下文启动失败。
 *
 * <p>条件装配围栏：仅当 smartwash.stub.sms-enabled=true 或未配置该属性（demo 开箱即跑）时装配；
 * application-prod.yaml 中显式置为 false——生产若未提供真实 SmsService 实现，
 * 容器缺少该 Bean 将启动失败，防止验证码仅落日志的"假发送"上线（对应评审报告后端 #31）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "smartwash.stub.sms-enabled", havingValue = "true", matchIfMissing = true)
public class StubSmsServiceImpl implements SmsService {

    @Override
    public boolean sendCaptcha(String phoneNumber, String code) {
        // 仅开发环境输出验证码明文（debug 级），生产环境严禁输出验证码；此处替换为真实 SMS SDK 调用
        log.debug("[桩实现][仅开发环境] 短信验证码已生成, phone: {}, code: {}", maskPhone(phoneNumber), code);
        return true;
    }

    /**
     * 手机号脱敏用于日志输出，避免敏感信息明文落日志
     */
    private String maskPhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }
}
