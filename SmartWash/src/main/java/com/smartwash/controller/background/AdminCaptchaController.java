package com.smartwash.controller.background;

import com.smartwash.common.DefaultConstant;
import com.smartwash.common.Result;
import com.smartwash.vo.AdminCaptchaVo;
import com.smartwash.vo.AdminCaptchaVo.CaptchaInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 演示工具：管理员查询指定手机号当前有效的短信验证码（仅用于开发/演示环境）。
 * 验证码明文仅在此接口返回，严禁在其他接口返回。
 * 路径前缀 /admin/** 已由 SecurityConfig 配置为需要 ROLE_ADMIN。
 */
@Tag(name = "演示工具")
@Slf4j
@RestController
@RequestMapping("/admin/captcha")
public class AdminCaptchaController {

    /** 手机号格式正则：可选 +86 前缀，1 开头，第二位 3-9，共 11 位 */
    private static final String PHONE_REGEX = "^(\\+86)?1[3-9]\\d{9}$";

    /** 验证码用途：注册 */
    private static final String PURPOSE_REGISTER = "register";
    /** 验证码用途：重置密码 */
    private static final String PURPOSE_RESET = "reset";

    /** 验证码 Redis key 前缀，格式：captcha:{purpose}:{phone} */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    private final StringRedisTemplate redisTemplate;

    public AdminCaptchaController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询指定手机号当前有效的验证码。
     * 同一手机号可能同时存在 register 和 reset 两种用途的验证码（理论上不会，防御性处理），全部返回。
     *
     * @param phoneNumber 手机号
     * @return 验证码信息列表
     */
    @Operation(summary = "查询手机号有效验证码（演示用）", description = "从 Redis 查询指定手机号当前有效的短信验证码，仅演示/调试场景使用")
    @GetMapping("/{phoneNumber}")
    public Result<AdminCaptchaVo> queryCaptcha(
            @PathVariable @Parameter(description = "手机号码", required = true, example = "13800138000") String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches(PHONE_REGEX)) {
            log.warn("管理员查询验证码：手机号格式错误, phone: {}", maskPhone(phoneNumber));
            return Result.failMsg("手机号格式错误");
        }

        List<CaptchaInfo> captchas = new ArrayList<>();

        // 查询注册用途验证码
        CaptchaInfo registerInfo = queryCaptchaByPurpose(PURPOSE_REGISTER, phoneNumber);
        if (registerInfo != null) {
            captchas.add(registerInfo);
        }

        // 查询重置密码用途验证码
        CaptchaInfo resetInfo = queryCaptchaByPurpose(PURPOSE_RESET, phoneNumber);
        if (resetInfo != null) {
            captchas.add(resetInfo);
        }

        if (captchas.isEmpty()) {
            log.info("管理员查询验证码：未找到有效验证码, phone: {}", maskPhone(phoneNumber));
            return Result.failMsg("该手机号暂无有效验证码");
        }

        log.info("管理员查询验证码：找到 {} 个有效验证码, phone: {}", captchas.size(), maskPhone(phoneNumber));
        AdminCaptchaVo vo = new AdminCaptchaVo();
        vo.setPhone(phoneNumber);
        vo.setCaptchas(captchas);
        return Result.ok(vo);
    }

    /**
     * 按用途查询单个验证码及其剩余有效时间
     *
     * @param purpose 验证码用途（register/reset）
     * @param phone   手机号
     * @return 查到则返回 CaptchaInfo，否则返回 null
     */
    private CaptchaInfo queryCaptchaByPurpose(String purpose, String phone) {
        String key = buildCaptchaKey(purpose, phone);
        String code = redisTemplate.opsForValue().get(key);
        if (code == null || code.isEmpty()) {
            return null;
        }

        // 获取 TTL 剩余秒数；key 不存在或已过期时返回 null 或负数
        Long expireSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        long remainSeconds = (expireSeconds != null && expireSeconds > 0) ? expireSeconds : 0L;

        CaptchaInfo info = new CaptchaInfo();
        info.setPurpose(purpose);
        info.setPurposeDesc(purposeDesc(purpose));
        info.setCode(code);
        info.setRemainSeconds(remainSeconds);
        return info;
    }

    /**
     * 构造验证码 Redis key：captcha:{purpose}:{phone}
     */
    private String buildCaptchaKey(String purpose, String phone) {
        return CAPTCHA_KEY_PREFIX + purpose + ":" + phone;
    }

    /**
     * 验证码用途中文描述映射
     */
    private String purposeDesc(String purpose) {
        return switch (purpose) {
            case PURPOSE_REGISTER -> "注册";
            case PURPOSE_RESET -> "重置密码";
            default -> purpose;
        };
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
