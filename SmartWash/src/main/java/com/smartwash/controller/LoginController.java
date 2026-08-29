package com.smartwash.controller;

import com.smartwash.common.DefaultConstant;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.from.admin_users.AdminUserLoginFrom;
import com.smartwash.from.users.ResetPasswordFrom;
import com.smartwash.from.users.UserLoginFrom;
import com.smartwash.from.users.UserRegisterFrom;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.service.IUsersService;
import com.smartwash.service.SmsService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.vo.LoginVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Tag(name = "认证管理", description = "用户登录、注册、验证码接口")
@RestController
@Slf4j
@RequestMapping("/auth")
public class LoginController {

    private static final String PHONE_REGEX = "^(\\+86)?1[3-9]\\d{9}$";
    private static final int CAPTCHA_LENGTH = 6;
    private static final int LOGIN_MAX_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_MINUTES = 5;
    /**
     * IP 维度登录失败上限：同一 IP 在统计窗口（LOGIN_IP_LOCKOUT_MINUTES）内失败达到该次数即限制该 IP 登录。
     * 阈值远高于单账号锁定阈值（5 次），正常用户不受影响，仅拦截单点高频爆破/恶意锁号行为（评审报告后端 #18）。
     */
    private static final int LOGIN_IP_MAX_ATTEMPTS = 20;
    /** IP 维度失败计数窗口与锁定时长（分钟）：计数窗口=锁定时长，锁定到期后计数已过期、自动从零开始 */
    private static final long LOGIN_IP_LOCKOUT_MINUTES = 15;

    /**
     * 可信反向代理网段（IPv4 CIDR，逗号分隔，如 "10.0.0.0/8,172.16.0.0/12"；默认空=无可信代理）。
     * 仅当 TCP 直连地址落在可信网段内才采信 X-Forwarded-For / X-Real-IP 头；
     * 直连部署下这些头完全由客户端控制，采信会导致 IP 登录锁被逐请求轮换伪造头绕过，
     * 甚至被用于伪造受害者共享出口 IP 定向触发锁定（可用性攻击）。
     */
    @Value("${security.trusted-proxy-cidrs:}")
    private String trustedProxyCidrs;

    /** 验证码 Redis key 前缀，格式：captcha:{purpose}:{phone}，注册与重置密码按用途隔离 */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    /** 验证码校验失败计数 key 前缀，格式：captcha:fail:{purpose}:{phone}，TTL 与验证码对齐 */
    private static final String CAPTCHA_FAIL_KEY_PREFIX = "captcha:fail:";
    /** 验证码发送频控 key 前缀，格式：captcha:rate:{phone}（一次发送同时覆盖注册/重置两种校验场景，按手机号限频比按 purpose+phone 更严格） */
    private static final String CAPTCHA_RATE_KEY_PREFIX = "captcha:rate:";
    /** 验证码用途标识：注册 */
    private static final String CAPTCHA_PURPOSE_REGISTER = "register";
    /** 验证码用途标识：重置密码 */
    private static final String CAPTCHA_PURPOSE_RESET = "reset";
    /** 单个验证码最大校验失败次数，达到即销毁验证码并拒绝（防穷举） */
    private static final int CAPTCHA_MAX_FAIL_ATTEMPTS = 5;
    /** IP 维度登录失败计数 key 前缀，格式：login:attempt:ip:{ip} */
    private static final String LOGIN_IP_ATTEMPT_KEY_PREFIX = "login:attempt:ip:";
    /** IP 维度登录锁定 key 前缀，格式：login:lock:ip:{ip} */
    private static final String LOGIN_IP_LOCK_KEY_PREFIX = "login:lock:ip:";

    private final IAdminUsersService adminUsersService;
    private final IUsersService usersService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;
    private final SmsService smsService;

    public LoginController(IAdminUsersService adminUsersService, IUsersService usersService, JwtUtil jwtUtil, AuthenticationManager authenticationManager, StringRedisTemplate redisTemplate, SmsService smsService) {
        this.adminUsersService = adminUsersService;
        this.usersService = usersService;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.redisTemplate = redisTemplate;
        this.smsService = smsService;
    }

    @Operation(summary = "管理员登录", description = "管理员通过用户名和密码登录，返回JWT令牌与角色")
    @PostMapping("/adminUsers/login")
    public Result<LoginVo> login(@RequestBody @Valid AdminUserLoginFrom userLoginFrom, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String lockKey = "login:lock:admin:" + userLoginFrom.getUsername();
        String attemptKey = "login:attempt:admin:" + userLoginFrom.getUsername();
        if (isLocked(lockKey) || isLocked(LOGIN_IP_LOCK_KEY_PREFIX + clientIp)) {
            return Result.failMsg("登录尝试过于频繁，请稍后再试");
        }

        String username = String.format("%s-%s", DefaultConstant.ADMIN_USER_LOGIN_TYPE, userLoginFrom.getUsername());

        Authentication authenticate = null;
        try {
            authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginFrom.getPassword()));
        } catch (AuthenticationException e) {
            log.warn("管理员登录认证失败: {}", e.getMessage());
            recordFailedAttempt(attemptKey, lockKey);
            recordIpFailedAttempt(LOGIN_IP_ATTEMPT_KEY_PREFIX + clientIp, LOGIN_IP_LOCK_KEY_PREFIX + clientIp, clientIp);
            return Result.failMsg("用户名或密码错误");
        }
        redisTemplate.delete(attemptKey);
        // 登录成功回退一次 IP 失败计数（不整键清零，防"19 失败+1 成功"循环绕过阈值），偶发失败不会长期累积误伤
        relieveIpFailedAttempt(LOGIN_IP_ATTEMPT_KEY_PREFIX + clientIp);
        SecurityContextHolder.getContext().setAuthentication(authenticate);

        String token = jwtUtil.generatorToken(username);
        // 角色取该管理员在 roles 表中实际绑定的角色名，不硬编码（供 Web 管理后台按角色渲染）
        String role = adminUsersService.getAdminRoleName(userLoginFrom.getUsername());
        log.info("管理员登录成功, username: {}, role: {}", userLoginFrom.getUsername(), role);
        return Result.ok(new LoginVo(token, role));
    }

    @Operation(summary = "用户登录", description = "用户通过手机号和密码登录，返回JWT令牌与角色")
    @PostMapping("/user/login")
    public Result<LoginVo> login(@RequestBody @Valid UserLoginFrom userLoginFrom, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String lockKey = "login:lock:user:" + userLoginFrom.getPhoneNumber();
        String attemptKey = "login:attempt:user:" + userLoginFrom.getPhoneNumber();
        if (isLocked(lockKey) || isLocked(LOGIN_IP_LOCK_KEY_PREFIX + clientIp)) {
            return Result.failMsg("登录尝试过于频繁，请稍后再试");
        }

        if (usersService.getUserByPhone(userLoginFrom.getPhoneNumber()) == null) {
            log.warn("用户登录失败：手机号未注册, phone: {}, ip: {}", maskPhone(userLoginFrom.getPhoneNumber()), clientIp);
            // 手机号未注册同样计入 IP 失败计数：该分支对外与密码错误不可区分，
            // 不计数会被用于绕过账号锁定做手机号枚举/爆破
            recordIpFailedAttempt(LOGIN_IP_ATTEMPT_KEY_PREFIX + clientIp, LOGIN_IP_LOCK_KEY_PREFIX + clientIp, clientIp);
            return Result.failMsg("用户名或密码错误");
        }
        String username = String.format("%s-%s", DefaultConstant.USER_LOGIN_TYPE, userLoginFrom.getPhoneNumber());

        Authentication authenticate = null;
        try {
            authenticate = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, userLoginFrom.getPassword()));
        } catch (AuthenticationException e) {
            log.warn("用户登录认证失败: {}", e.getMessage());
            recordFailedAttempt(attemptKey, lockKey);
            recordIpFailedAttempt(LOGIN_IP_ATTEMPT_KEY_PREFIX + clientIp, LOGIN_IP_LOCK_KEY_PREFIX + clientIp, clientIp);
            return Result.failMsg("用户名或密码错误");
        }
        redisTemplate.delete(attemptKey);
        // 登录成功回退一次 IP 失败计数（不整键清零，防"19 失败+1 成功"循环绕过阈值），偶发失败不会长期累积误伤
        relieveIpFailedAttempt(LOGIN_IP_ATTEMPT_KEY_PREFIX + clientIp);
        SecurityContextHolder.getContext().setAuthentication(authenticate);

        String token = jwtUtil.generatorToken(username);
        log.info("用户登录成功, phone: {}", maskPhone(userLoginFrom.getPhoneNumber()));
        return Result.ok(new LoginVo(token, DefaultConstant.USER_LOGIN_TYPE));
    }

    @Operation(summary = "用户注册", description = "用户通过手机号、密码和验证码注册新账号，注册成功返回JWT令牌")
    @PostMapping("/user/register")
    public Result<String> register(@RequestBody @Valid UserRegisterFrom userRegisterFrom) {
        String phone = userRegisterFrom.getPhoneNumber();
        // 使用注册用途（register）的独立 key 校验，杜绝重置密码场景的验证码跨场景使用
        CaptchaVerifyResult verifyResult = verifyCaptcha(CAPTCHA_PURPOSE_REGISTER, phone, userRegisterFrom.getCode());
        if (verifyResult != CaptchaVerifyResult.SUCCESS) {
            log.warn("注册验证码校验失败[{}], phone: {}", verifyResult.getDesc(), maskPhone(phone));
            return Result.failMsg(verifyResult.getDesc());
        }

        // 说明：验证码在校验成功时即被销毁（一次性使用），若因手机号已注册等原因注册失败，需重新获取验证码
        if (usersService.registerUser(userRegisterFrom)) {
            String username = String.format("%s-%s", DefaultConstant.USER_LOGIN_TYPE, phone);
            log.info("用户注册成功, phone: {}", maskPhone(phone));
            return Result.ok(jwtUtil.generatorToken(username));
        }
        return Result.failMsg("注册失败");
    }

    @Operation(summary = "获取短信验证码", description = "向指定手机号发送6位数字短信验证码，同一手机号60秒内只能请求一次")
    @GetMapping("/user/captcha/{phoneNumber}")
    public Result<String> getCaptcha(@PathVariable @Parameter(description = "手机号码", required = true, example = "13800138000") String phoneNumber) {
        if (!phoneNumber.matches(PHONE_REGEX)) {
            log.warn("验证码请求手机号格式错误, phone: {}", maskPhone(phoneNumber));
            return Result.failMsg("手机号格式错误");
        }

        // 频率限制：同一手机号 60 秒内只能请求一次（一次发送覆盖注册/重置两种校验场景，按手机号限频比按 purpose+phone 更严格）
        String rateKey = CAPTCHA_RATE_KEY_PREFIX + phoneNumber;
        Boolean firstTry = redisTemplate.opsForValue().setIfAbsent(rateKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(firstTry)) {
            log.warn("验证码请求过于频繁, phone: {}", maskPhone(phoneNumber));
            return Result.failMsg("验证码发送过于频繁，请稍后再试");
        }

        try {
            // 发送与校验一一对应：对外契约（路径/参数）不能携带 purpose，按手机号注册状态推断唯一用途——
            // 已注册手机号签发的验证码仅可用于重置密码（reset），未注册仅可用于注册（register），
            // 从根本上杜绝 A 场景验证码通过 B 场景校验；DB 异常时拒绝签发（注册/重置本就依赖 DB，fail-safe）。
            boolean registered = usersService.getUserByPhone(phoneNumber) != null;
            String purpose = registered ? CAPTCHA_PURPOSE_RESET : CAPTCHA_PURPOSE_REGISTER;

            String code = generateCaptchaCode();
            // 新验证码签发前清理该手机号全部旧验证码与失败计数，旧码即刻失效
            clearCaptcha(phoneNumber);
            String captchaKey = captchaKey(purpose, phoneNumber);
            redisTemplate.opsForValue().set(captchaKey, code, DefaultConstant.CAPTCHA_TIMEOUT, TimeUnit.MILLISECONDS);

            // 经短信服务接口下发（当前为桩实现：dev 环境以 debug 日志输出验证码，仅开发环境）；
            // 响应体只返回提示文案，严禁携带验证码明文
            if (!smsService.sendCaptcha(phoneNumber, code)) {
                redisTemplate.delete(captchaKey);
                redisTemplate.delete(rateKey);
                log.warn("短信验证码下发失败, purpose: {}, phone: {}", purpose, maskPhone(phoneNumber));
                return Result.failMsg("验证码发送失败，请稍后再试");
            }
            log.info("验证码已生成并下发, purpose: {}, phone: {}", purpose, maskPhone(phoneNumber));
            return Result.ok("验证码已发送");
        } catch (Exception e) {
            // 系统异常（如 DB 不可用）：释放频控便于重试，且不签出无法校验的验证码
            redisTemplate.delete(rateKey);
            log.error("验证码发送流程异常, phone: {}", maskPhone(phoneNumber), e);
            return Result.failMsg("验证码发送失败，请稍后再试");
        }
    }

    @Operation(summary = "重置密码", description = "通过短信验证码重置用户密码")
    @PostMapping("/user/resetPassword")
    public Result<String> resetPassword(@RequestBody @Valid ResetPasswordFrom resetPasswordFrom) {
        String phone = resetPasswordFrom.getPhoneNumber();
        // 使用重置密码用途（reset）的独立 key 校验，杜绝注册场景的验证码跨场景使用
        CaptchaVerifyResult verifyResult = verifyCaptcha(CAPTCHA_PURPOSE_RESET, phone, resetPasswordFrom.getCode());
        if (verifyResult != CaptchaVerifyResult.SUCCESS) {
            log.warn("重置密码验证码校验失败[{}], phone: {}", verifyResult.getDesc(), maskPhone(phone));
            return Result.failMsg(verifyResult.getDesc());
        }

        // 说明：验证码在校验成功时即被销毁（一次性使用），若用户不存在导致重置失败，需重新获取验证码
        if (usersService.resetPassword(phone, resetPasswordFrom.getNewPassword())) {
            log.info("密码重置成功, phone: {}", maskPhone(phone));
            return Result.ok("密码重置成功");
        }
        return Result.failMsg("用户不存在");
    }

    /**
     * 按用途校验短信验证码，并维护失败计数防穷举
     *
     * @param purpose   验证码用途（register/reset），发送与校验必须使用同一 purpose
     * @param phone     手机号
     * @param inputCode 用户提交的验证码
     * @return 校验结果
     */
    private CaptchaVerifyResult verifyCaptcha(String purpose, String phone, String inputCode) {
        String captchaKey = captchaKey(purpose, phone);
        String code = redisTemplate.opsForValue().get(captchaKey);
        if (!StringUtils.hasText(code)) {
            return CaptchaVerifyResult.EXPIRED;
        }
        if (code.equals(inputCode)) {
            // 校验成功：立即销毁验证码与失败计数，保证一次性使用
            destroyCaptcha(purpose, phone);
            return CaptchaVerifyResult.SUCCESS;
        }
        // 校验失败：递增失败计数（TTL 与验证码对齐），达到上限立即销毁验证码，防止持续穷举
        String failKey = captchaFailKey(purpose, phone);
        Long fails = redisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1) {
            redisTemplate.expire(failKey, DefaultConstant.CAPTCHA_TIMEOUT, TimeUnit.MILLISECONDS);
        }
        if (fails != null && fails >= CAPTCHA_MAX_FAIL_ATTEMPTS) {
            destroyCaptcha(purpose, phone);
            log.warn("验证码校验失败达上限，验证码已销毁, purpose: {}, phone: {}", purpose, maskPhone(phone));
            return CaptchaVerifyResult.TOO_MANY_FAILS;
        }
        return CaptchaVerifyResult.MISMATCH;
    }

    /**
     * 销毁指定用途的验证码及其失败计数
     */
    private void destroyCaptcha(String purpose, String phone) {
        redisTemplate.delete(captchaKey(purpose, phone));
        redisTemplate.delete(captchaFailKey(purpose, phone));
    }

    /**
     * 清理该手机号在全部用途下的旧验证码与失败计数（新验证码签发前调用，旧码即刻失效）
     */
    private void clearCaptcha(String phone) {
        destroyCaptcha(CAPTCHA_PURPOSE_REGISTER, phone);
        destroyCaptcha(CAPTCHA_PURPOSE_RESET, phone);
    }

    /**
     * 生成 6 位数字验证码
     */
    private String generateCaptchaCode() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder otp = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * 验证码 Redis key：captcha:{purpose}:{phone}
     */
    private String captchaKey(String purpose, String phone) {
        return CAPTCHA_KEY_PREFIX + purpose + ":" + phone;
    }

    /**
     * 验证码失败计数 Redis key：captcha:fail:{purpose}:{phone}
     */
    private String captchaFailKey(String purpose, String phone) {
        return CAPTCHA_FAIL_KEY_PREFIX + purpose + ":" + phone;
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

    /**
     * 验证码校验结果
     */
    private enum CaptchaVerifyResult {
        /** 校验成功 */
        SUCCESS("验证成功"),
        /** 未找到有效验证码（不存在或已过期） */
        EXPIRED("验证码已过期，请重新获取"),
        /** 验证码不匹配（仍在可重试范围内） */
        MISMATCH("验证码错误，请重新输入"),
        /** 失败次数达上限，验证码已被销毁 */
        TOO_MANY_FAILS("验证码错误次数过多，请重新获取");

        private final String desc;

        CaptchaVerifyResult(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    private boolean isLocked(String lockKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    private void recordFailedAttempt(String attemptKey, String lockKey) {
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts >= LOGIN_MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockKey, "1", LOGIN_LOCKOUT_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(attemptKey);
            log.warn("登录失败次数过多，账号已锁定 {} 分钟", LOGIN_LOCKOUT_MINUTES);
        } else if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptKey, LOGIN_LOCKOUT_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * 记录 IP 维度登录失败计数：同一 IP 在统计窗口内失败达 LOGIN_IP_MAX_ATTEMPTS 次即锁定该 IP。
     * 与账号维度锁定并存——攻击者无法再通过"只撞他人账号"绕过 IP 限制恶意锁死任意账号（评审报告后端 #18）。
     *
     * @param ipAttemptKey IP 失败计数 key
     * @param ipLockKey    IP 锁定 key
     * @param clientIp     客户端 IP（仅用于日志）
     */
    private void recordIpFailedAttempt(String ipAttemptKey, String ipLockKey, String clientIp) {
        Long attempts = redisTemplate.opsForValue().increment(ipAttemptKey);
        if (attempts != null && attempts >= LOGIN_IP_MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(ipLockKey, "1", LOGIN_IP_LOCKOUT_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(ipAttemptKey);
            log.warn("同一 IP 登录失败次数过多，IP 已被限制 {} 分钟, ip: {}", LOGIN_IP_LOCKOUT_MINUTES, clientIp);
        } else if (attempts != null && attempts == 1) {
            // 计数窗口与锁定时长一致：窗口内从首次失败开始计数，过期自动归零
            redisTemplate.expire(ipAttemptKey, LOGIN_IP_LOCKOUT_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * 从请求中解析客户端真实 IP：
     * 仅当 TCP 直连地址（remoteAddr）落在 security.trusted-proxy-cidrs 配置的可信代理网段内时，
     * 才采信 X-Forwarded-For 首个 IP / X-Real-IP；否则一律使用直连地址（应用层无法验证的头不可作为风控依据）。
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    /** 判断直连地址是否落在可信代理网段（IPv4 CIDR 匹配；未配置网段或地址非法时一律视为不可信） */
    private boolean isTrustedProxy(String ip) {
        if (!StringUtils.hasText(trustedProxyCidrs) || !StringUtils.hasText(ip)) {
            return false;
        }
        long ipLong = ipv4ToLong(ip);
        if (ipLong < 0) {
            return false;
        }
        for (String cidr : trustedProxyCidrs.split(",")) {
            String[] parts = cidr.trim().split("/");
            if (parts.length != 2) {
                continue;
            }
            long network = ipv4ToLong(parts[0]);
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (network < 0 || prefix < 0 || prefix > 32) {
                continue;
            }
            long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            if ((ipLong & mask) == (network & mask)) {
                return true;
            }
        }
        return false;
    }

    /** IPv4 点分十进制转 long；格式非法返回 -1（IPv6 场景不支持 CIDR 匹配，视为不可信代理） */
    private long ipv4ToLong(String ip) {
        String[] segments = ip.split("\\.");
        if (segments.length != 4) {
            return -1L;
        }
        long result = 0;
        for (String segment : segments) {
            int value;
            try {
                value = Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                return -1L;
            }
            if (value < 0 || value > 255) {
                return -1L;
            }
            result = (result << 8) | value;
        }
        return result;
    }

    /**
     * 登录成功后回退一次 IP 失败计数（不整键清零）：
     * 整键清零可被"19 次失败 + 1 次成功"循环利用，使计数永远达不到锁定阈值。
     */
    private void relieveIpFailedAttempt(String ipAttemptKey) {
        Long attempts = redisTemplate.opsForValue().decrement(ipAttemptKey);
        if (attempts != null && attempts < 0) {
            // key 不存在时 decrement 会产生无 TTL 的 -1 残留，直接清理
            redisTemplate.delete(ipAttemptKey);
        }
    }
}
