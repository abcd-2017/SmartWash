package com.smartwash.controller;

import com.smartwash.common.DefaultConstant;
import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.from.admin_users.AdminUserLoginFrom;
import com.smartwash.from.auth.RefreshTokenFrom;
import com.smartwash.from.users.ResetPasswordFrom;
import com.smartwash.from.users.UserLoginFrom;
import com.smartwash.from.users.UserRegisterFrom;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.service.IUsersService;
import com.smartwash.service.JwtBlacklistService;
import com.smartwash.service.SmsService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.vo.LoginVo;
import com.smartwash.vo.TokenPairVo;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.UUID;
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
     * 时序对齐用 BCrypt 校验器与伪哈希（启动时由随机 UUID 生成，不对应任何真实账号凭证，非密钥）：
     * "手机号未注册"分支对伪哈希执行一次哑校验，使该分支耗时与"已注册+BCrypt 密码校验"路径相当，
     * 抹平响应时序差，防止通过响应时间枚举注册手机号
     */
    private static final BCryptPasswordEncoder DUMMY_PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String DUMMY_BCRYPT_HASH = new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
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

    /**
     * 短信桩实现开关（与 StubSmsServiceImpl 的装配条件同源，未配置时默认 true 与其 matchIfMissing 一致）。
     * 仅本地演示便利：桩开启时短信并不真实发出、验证码只落 debug 日志，用户拿不到码，
     * 此时验证码随发送响应文案回显便于联调；接入真实短信通道（关闭桩）后保持原文案，验证码绝不进响应体。
     */
    @Value("${smartwash.stub.sms-enabled:true}")
    private boolean stubSmsEnabled;

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
    private final JwtBlacklistService jwtBlacklistService;

    public LoginController(IAdminUsersService adminUsersService, IUsersService usersService, JwtUtil jwtUtil, AuthenticationManager authenticationManager, StringRedisTemplate redisTemplate, SmsService smsService, JwtBlacklistService jwtBlacklistService) {
        this.adminUsersService = adminUsersService;
        this.usersService = usersService;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.redisTemplate = redisTemplate;
        this.smsService = smsService;
        this.jwtBlacklistService = jwtBlacklistService;
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

        String token = jwtUtil.generatorAccessToken(username);
        String refreshToken = jwtUtil.generatorRefreshToken(username);
        // 角色取该管理员在 roles 表中实际绑定的角色名，不硬编码（供 Web 管理后台按角色渲染）
        String role = adminUsersService.getAdminRoleName(userLoginFrom.getUsername());
        log.info("管理员登录成功, username: {}, role: {}", userLoginFrom.getUsername(), role);
        return Result.ok(new LoginVo(token, refreshToken, role));
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
            // 哑 BCrypt 校验对齐时序：未注册分支直接返回会快于已注册分支的密码 BCrypt 校验，
            // 攻击者可据此区分手机号是否注册；先执行一次等成本校验再返回相同错误文案
            DUMMY_PASSWORD_ENCODER.matches(userLoginFrom.getPassword(), DUMMY_BCRYPT_HASH);
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

        String token = jwtUtil.generatorAccessToken(username);
        String refreshToken = jwtUtil.generatorRefreshToken(username);
        log.info("用户登录成功, phone: {}", maskPhone(userLoginFrom.getPhoneNumber()));
        return Result.ok(new LoginVo(token, refreshToken, DefaultConstant.USER_LOGIN_TYPE));
    }

    @Operation(summary = "用户注册", description = "用户通过手机号、密码和验证码注册新账号，注册成功即自动登录并返回双 token（token + refreshToken）与角色")
    @PostMapping("/user/register")
    public Result<LoginVo> register(@RequestBody @Valid UserRegisterFrom userRegisterFrom, HttpServletRequest request) {
        String phone = userRegisterFrom.getPhoneNumber();
        String clientIp = getClientIp(request);
        // IP 维度统一风控：与登录共享同一 IP 锁，达到阈值后一刀切限流
        if (isLocked(LOGIN_IP_LOCK_KEY_PREFIX + clientIp)) {
            return Result.failMsg("操作过于频繁，请稍后再试");
        }
        // 已注册手机号无法注册成功：发码接口会为其签发重置密码用途的验证码（见 getCaptcha 的 purpose 推断），
        // 若放行到验证码校验将永远报"验证码已过期"，误导用户反复重发陷入死循环；在此提前拦截并给出明确指引。
        // 该分支与"验证码已过期"文案可区分、会暴露手机号注册状态，故与登录一致计入 IP 失败数限流防枚举。
        if (usersService.getUserByPhone(phone) != null) {
            log.warn("注册拒绝：手机号已注册, phone: {}, ip: {}", maskPhone(phone), clientIp);
            recordIpFailedAttempt(LOGIN_IP_ATTEMPT_KEY_PREFIX + clientIp, LOGIN_IP_LOCK_KEY_PREFIX + clientIp, clientIp);
            return Result.failMsg("该手机号已注册，请直接登录或使用忘记密码找回");
        }
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
            // 注册即自动登录：响应与登录同构（双 token + 角色），角色固定为 user
            return Result.ok(new LoginVo(
                    jwtUtil.generatorAccessToken(username),
                    jwtUtil.generatorRefreshToken(username),
                    DefaultConstant.USER_LOGIN_TYPE));
        }
        return Result.failMsg("注册失败");
    }

    @Operation(summary = "刷新令牌", description = "携带 refreshToken 换取全新的双 token；refreshToken 一次性使用，刷新成功后旧令牌立即失效，重放返回 401 信封")
    @PostMapping("/refresh")
    public Result<TokenPairVo> refresh(@RequestBody @Valid RefreshTokenFrom refreshTokenFrom) {
        Claims claims;
        try {
            // 第一环：格式/签名/过期校验（parseSignedClaims 任一不过即抛异常）
            claims = jwtUtil.getPayloadFromToken(refreshTokenFrom.getRefreshToken());
        } catch (Exception e) {
            log.warn("刷新令牌失败：refreshToken 无效或已过期");
            return refreshUnauthorized();
        }
        // 第二环：令牌分型校验——仅 refresh 型可刷新；旧格式（无 typ）视为 access，不得用于刷新
        if (!JwtUtil.TOKEN_TYPE_REFRESH.equals(jwtUtil.getTokenType(claims))) {
            log.warn("刷新令牌失败：非 refresh 型令牌, typ: {}", jwtUtil.getTokenType(claims));
            return refreshUnauthorized();
        }
        String jti = claims.get("jti", String.class);
        // 第三环：黑名单校验——命中即视为重放（已刷新过或已登出的 refreshToken 不允许再次使用）
        if (jti != null && jwtBlacklistService.isBlacklisted(jti)) {
            log.warn("刷新令牌失败：refreshToken 已被使用或已登出（重放拦截）, jti: {}", jti);
            return refreshUnauthorized();
        }
        String username = claims.getSubject();
        if (!StringUtils.hasText(username)) {
            return refreshUnauthorized();
        }
        // 以 sub 原样重签新的一对 token（admin-/user- 前缀不变，角色语义下次登录刷新，端点零 DB 依赖）
        TokenPairVo tokenPair = new TokenPairVo(
                jwtUtil.generatorAccessToken(username),
                jwtUtil.generatorRefreshToken(username));
        // 一次性轮换：旧 refreshToken 立即拉黑，TTL 对齐其剩余有效期（到期自动从 Redis 清除）
        long ttlSeconds = jwtUtil.getRemainingValiditySeconds(claims);
        if (jti != null && ttlSeconds > 0) {
            jwtBlacklistService.blacklist(jti, ttlSeconds);
        }
        log.info("刷新令牌成功, subject: {}", username);
        return Result.ok(tokenPair);
    }

    /**
     * 刷新令牌失败的统一响应：401 信封（语义与过滤器/入口点的登录失效一致，端上按 401 走重新登录）
     */
    private Result<TokenPairVo> refreshUnauthorized() {
        return Result.build(null, ResultCodeEnum.UNAUTHORIZED);
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
            // 真实短信通道下响应体只返回提示文案，严禁携带验证码明文；
            // 桩实现开启时（本地演示，短信并未真实发出）验证码随文案回显，便于本地联调
            if (!smsService.sendCaptcha(phoneNumber, code)) {
                redisTemplate.delete(captchaKey);
                redisTemplate.delete(rateKey);
                log.warn("短信验证码下发失败, purpose: {}, phone: {}", purpose, maskPhone(phoneNumber));
                return Result.failMsg("验证码发送失败，请稍后再试");
            }
            log.info("验证码已生成并下发, purpose: {}, phone: {}", purpose, maskPhone(phoneNumber));
            if (stubSmsEnabled) {
                log.debug("本地演示模式：验证码随响应回显, phone: {}", maskPhone(phoneNumber));
                return Result.ok(String.format("验证码已发送（本地演示验证码：%s）", code));
            }
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
    public Result<String> resetPassword(@RequestBody @Valid ResetPasswordFrom resetPasswordFrom, HttpServletRequest request) {
        String phone = resetPasswordFrom.getPhoneNumber();
        String clientIp = getClientIp(request);
        // IP 维度统一风控：与登录共享同一 IP 锁，达到阈值后一刀切限流
        if (isLocked(LOGIN_IP_LOCK_KEY_PREFIX + clientIp)) {
            return Result.failMsg("操作过于频繁，请稍后再试");
        }
        // 未注册手机号无法重置密码：发码接口会为其签发注册用途的验证码，若放行到验证码校验将永远报
        // "验证码已过期"误导用户；在此提前拦截并给出明确指引（与 register 的已注册拦截对称）。
        // 该分支同样会暴露手机号注册状态，计入 IP 失败数限流防枚举。
        if (usersService.getUserByPhone(phone) == null) {
            log.warn("重置密码拒绝：手机号未注册, phone: {}, ip: {}", maskPhone(phone), clientIp);
            recordIpFailedAttempt(LOGIN_IP_ATTEMPT_KEY_PREFIX + clientIp, LOGIN_IP_LOCK_KEY_PREFIX + clientIp, clientIp);
            return Result.failMsg("该手机号未注册，请先注册");
        }
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
                // 服务端自身配置的 CIDR 前缀格式非法被跳过，可信代理判定可能失效，warn 预警
                log.warn("可信代理网段配置解析失败，该网段被跳过, cidr: {}", cidr);
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
                // 来源头（X-Forwarded-For 等）可被客户端伪造注入非法格式，仅 debug 留痕防日志刷屏
                log.debug("IP 地址段解析失败，视为非法 IP, ip: {}", ip);
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
