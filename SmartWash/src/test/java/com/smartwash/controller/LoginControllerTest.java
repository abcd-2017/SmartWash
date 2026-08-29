package com.smartwash.controller;

import com.smartwash.common.Result;
import com.smartwash.from.users.ResetPasswordFrom;
import com.smartwash.from.users.UserRegisterFrom;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.service.IUsersService;
import com.smartwash.service.SmsService;
import com.smartwash.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码防护回归测试（评审报告后端 P0 #9）。
 * 覆盖：失败次数达 5 次销毁验证码防穷举；注册/重置密码 purpose 隔离（key 不共用、跨场景码不互认）；
 * 验证码一次性使用；发送频控；短信下发失败回滚。
 * Redis 使用 mock 的 StringRedisTemplate + 内存 Map 模拟真实 get/set/increment/delete 语义。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoginController 验证码防穷举与 purpose 隔离测试")
class LoginControllerTest {

    private static final String PHONE = "13800138000";
    private static final String CODE = "123456";
    private static final String REGISTER_KEY = "captcha:register:" + PHONE;
    private static final String RESET_KEY = "captcha:reset:" + PHONE;

    @Mock
    private IAdminUsersService adminUsersService;
    @Mock
    private IUsersService usersService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SmsService smsService;

    /** 模拟 Redis 的字符串存储与计数器，配合 mock 还原真实 get/set/increment/delete 语义 */
    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final Map<String, Long> counters = new ConcurrentHashMap<>();

    private LoginController controller;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("dGVzdFNlY3JldEtleUZvckpXVFRlc3RpbmdQdXJwb3Nlcw==");
        controller = new LoginController(adminUsersService, usersService, jwtUtil, authenticationManager, redisTemplate, smsService);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> store.putIfAbsent(inv.getArgument(0, String.class), inv.getArgument(1, String.class)) == null);
        when(valueOperations.increment(anyString()))
                .thenAnswer(inv -> counters.merge(inv.getArgument(0, String.class), 1L, Long::sum));
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            boolean removed = store.remove(key) != null;
            removed |= counters.remove(key) != null;
            return removed;
        });
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(redisTemplate.hasKey(anyString()))
                .thenAnswer(inv -> store.containsKey(inv.getArgument(0, String.class)) || counters.containsKey(inv.getArgument(0, String.class)));
    }

    private Result<String> register(String code) {
        UserRegisterFrom from = new UserRegisterFrom();
        from.setPhoneNumber(PHONE);
        from.setPassword("abc12345");
        from.setCode(code);
        return controller.register(from);
    }

    private Result<String> resetPassword(String code) {
        ResetPasswordFrom from = new ResetPasswordFrom();
        from.setPhoneNumber(PHONE);
        from.setCode(code);
        from.setNewPassword("newpass99");
        return controller.resetPassword(from);
    }

    // ==================== 失败次数上限（防穷举） ====================

    @Test
    @DisplayName("验证码错误累计 5 次：第 5 次销毁验证码并返回\"错误次数过多\"，之后视为已过期")
    void verifyCaptcha_fiveFails_destroysCaptcha() {
        store.put(RESET_KEY, CODE);

        for (int i = 1; i <= 4; i++) {
            Result<String> result = resetPassword("000000");
            assertEquals("验证码错误，请重新输入", result.getMessage(), "前 4 次错误应提示可重试（第 " + i + " 次）");
        }
        // 第 5 次失败：达到 CAPTCHA_MAX_FAIL_ATTEMPTS=5，销毁验证码
        Result<String> fifth = resetPassword("000000");
        assertEquals("验证码错误次数过多，请重新获取", fifth.getMessage(), "第 5 次失败必须拒绝并提示重新获取");
        assertNull(store.get(RESET_KEY), "达到失败上限后验证码必须被销毁");
        assertNull(store.get("captcha:fail:reset:" + PHONE), "失败计数应与验证码一并销毁");

        // 销毁后再校验：等价于验证码过期
        Result<String> sixth = resetPassword(CODE);
        assertEquals("验证码已过期，请重新获取", sixth.getMessage(), "即使提交正确验证码，销毁后也必须拒绝（防穷举）");
    }

    @Test
    @DisplayName("验证码错误未达 5 次：验证码保持可用，正确码仍可校验通过")
    void verifyCaptcha_fewerThanFiveFails_captchaStillUsable() {
        store.put(REGISTER_KEY, CODE);

        assertEquals("验证码错误，请重新输入", register("000000").getMessage());
        assertEquals("验证码错误，请重新输入", register("111111").getMessage());

        when(usersService.registerUser(any(UserRegisterFrom.class))).thenReturn(true);
        assertEquals(200, register(CODE).getCode(), "未达失败上限时正确验证码应可正常使用");
    }

    // ==================== purpose 隔离 ====================

    @Test
    @DisplayName("purpose 隔离：注册场景签发的验证码不能用于重置密码（reset key 独立）")
    void purposeIsolation_registerCodeCannotBeUsedForReset() {
        store.put(REGISTER_KEY, CODE);

        Result<String> resetResult = resetPassword(CODE);
        assertEquals("验证码已过期，请重新获取", resetResult.getMessage(),
                "重置密码必须查询 captcha:reset:{phone}，注册码不能跨场景使用");
        verify(usersService, never()).resetPassword(anyString(), anyString());
        // 注册 key 未被误删/误用
        assertEquals(CODE, store.get(REGISTER_KEY), "注册场景的验证码不应被重置密码流程消费");
    }

    @Test
    @DisplayName("purpose 隔离：重置密码场景签发的验证码不能用于注册（register key 独立）")
    void purposeIsolation_resetCodeCannotBeUsedForRegister() {
        store.put(RESET_KEY, CODE);

        Result<String> registerResult = register(CODE);
        assertEquals("验证码已过期，请重新获取", registerResult.getMessage(),
                "注册必须查询 captcha:register:{phone}，重置密码码不能跨场景使用");
        verify(usersService, never()).registerUser(any(UserRegisterFrom.class));
        assertEquals(CODE, store.get(RESET_KEY), "重置密码场景的验证码不应被注册流程消费");
    }

    @Test
    @DisplayName("purpose 隔离：校验成功按用途销毁对应 key（一次性使用）")
    void purposeIsolation_successDestroysOnlyItsOwnKey() {
        store.put(RESET_KEY, CODE);
        when(usersService.resetPassword(PHONE, "newpass99")).thenReturn(true);

        Result<String> result = resetPassword(CODE);

        assertEquals("密码重置成功", result.getData(), "成功信封的业务文案位于 data 字段");
        assertNull(store.get(RESET_KEY), "校验成功后 reset 验证码必须销毁（一次性）");
        verify(redisTemplate, times(1)).delete(RESET_KEY);
    }

    // ==================== 发送验证码：purpose 推断与 key 隔离 ====================

    @Test
    @DisplayName("获取验证码：未注册手机号签发到 register key，绝不写入 reset key")
    void getCaptcha_unregisteredPhone_usesRegisterKeyOnly() {
        when(usersService.getUserByPhone(PHONE)).thenReturn(null);
        when(smsService.sendCaptcha(eq(PHONE), anyString())).thenReturn(true);

        Result<String> result = controller.getCaptcha(PHONE);

        assertEquals("验证码已发送", result.getData(), "成功信封的业务文案位于 data 字段");
        // 签发 key 断言
        verify(valueOperations, times(1)).set(startsWith("captcha:register:"), anyString(), anyLong(), any(TimeUnit.class));
        verify(valueOperations, never()).set(startsWith("captcha:reset:"), anyString(), anyLong(), any(TimeUnit.class));
        // 新码签发前清理全部旧码与失败计数
        verify(redisTemplate, times(4)).delete(anyString());
        // 验证码为 6 位数字且确实存入 Redis
        String stored = store.get(REGISTER_KEY);
        assertNotNull(stored, "验证码必须写入 Redis 才可校验");
        assertTrue(stored.matches("\\d{6}"), "验证码必须是 6 位数字");
    }

    @Test
    @DisplayName("获取验证码：已注册手机号签发到 reset key，绝不写入 register key")
    void getCaptcha_registeredPhone_usesResetKeyOnly() {
        when(usersService.getUserByPhone(PHONE)).thenReturn(new com.smartwash.entity.Users());
        when(smsService.sendCaptcha(eq(PHONE), anyString())).thenReturn(true);

        Result<String> result = controller.getCaptcha(PHONE);

        assertEquals("验证码已发送", result.getData(), "成功信封的业务文案位于 data 字段");
        verify(valueOperations, times(1)).set(startsWith("captcha:reset:"), anyString(), anyLong(), any(TimeUnit.class));
        verify(valueOperations, never()).set(startsWith("captcha:register:"), anyString(), anyLong(), any(TimeUnit.class));
        assertNotNull(store.get(RESET_KEY), "已注册手机号的验证码必须写入 reset key（供重置密码校验）");
    }

    @Test
    @DisplayName("获取验证码：同一手机号 60 秒内重复请求被限频拒绝")
    void getCaptcha_rateLimited_secondRequestRejected() {
        when(usersService.getUserByPhone(PHONE)).thenReturn(null);
        when(smsService.sendCaptcha(eq(PHONE), anyString())).thenReturn(true);

        assertEquals("验证码已发送", controller.getCaptcha(PHONE).getData());
        Result<String> second = controller.getCaptcha(PHONE);
        assertEquals("验证码发送过于频繁，请稍后再试", second.getMessage(), "频控窗口内第二次请求必须被拒绝");
    }

    @Test
    @DisplayName("获取验证码：短信下发失败时回滚验证码与频控 key")
    void getCaptcha_smsFailure_rollsBackKeys() {
        when(usersService.getUserByPhone(PHONE)).thenReturn(null);
        when(smsService.sendCaptcha(eq(PHONE), anyString())).thenReturn(false);

        Result<String> result = controller.getCaptcha(PHONE);

        assertEquals("验证码发送失败，请稍后再试", result.getMessage());
        assertNull(store.get(REGISTER_KEY), "下发失败时验证码不得留存在 Redis");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey("captcha:rate:" + PHONE)), "下发失败时应释放发送频控便于重试");
    }

    // ==================== 一次性使用 ====================

    @Test
    @DisplayName("注册验证码校验成功即销毁：同一验证码第二次使用必须失败")
    void register_captchaDestroyedAfterUse_oneTimeOnly() {
        store.put(REGISTER_KEY, CODE);
        when(usersService.registerUser(any(UserRegisterFrom.class))).thenReturn(true);

        Result<String> first = register(CODE);
        Result<String> second = register(CODE);

        assertEquals(200, first.getCode(), "首次注册（验证码正确）应成功");
        assertNull(second.getData(), "第二次使用同一验证码不得再签发 token");
        assertEquals("验证码已过期，请重新获取", second.getMessage(), "验证码一次性使用，消费后必须失效");
        // 返回的 JWT 必须面向该手机号用户
        Claims claims = jwtUtil.getPayloadFromToken(first.getData());
        assertEquals("user-" + PHONE, claims.getSubject(), "注册成功签发的 JWT 应是 user-{手机号}");
    }

    // ==================== IP 维度登录限制（评审报告后端 #18） ====================

    private static final String IP = "203.0.113.10";

    private com.smartwash.from.users.UserLoginFrom userLoginFrom(String phone) {
        com.smartwash.from.users.UserLoginFrom from = new com.smartwash.from.users.UserLoginFrom();
        from.setPhoneNumber(phone);
        from.setPassword("wrong-password");
        return from;
    }

    private Result<com.smartwash.vo.LoginVo> userLogin(String phone) {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr(IP);
        return controller.login(userLoginFrom(phone), request);
    }

    @Test
    @DisplayName("IP 维度限制：同一 IP 高频失败达 20 次后，即使换正常账号+正确密码也被拒绝")
    void login_ipLock_blocksAfterRepeatedFailuresFromSameIp() {
        // 用未注册手机号路径累计 IP 失败计数（该路径同样计入 IP 失败，防枚举绕过）
        when(usersService.getUserByPhone(anyString())).thenReturn(null);
        for (int i = 0; i < 20; i++) {
            assertEquals("用户名或密码错误", userLogin("1390000000" + (i % 10)).getMessage(),
                    "第 " + (i + 1) + " 次失败应正常返回密码错误提示");
        }

        // 第 21 次：换成已注册账号且密码正确，仍必须被 IP 锁定拒绝
        when(usersService.getUserByPhone(PHONE)).thenReturn(new com.smartwash.entity.Users());
        Result<com.smartwash.vo.LoginVo> blocked = userLogin(PHONE);
        assertEquals("登录尝试过于频繁，请稍后再试", blocked.getMessage(),
                "同一 IP 高频失败触发限制后，正常请求也必须被拦截（防爆破）");
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("防恶意锁号：账号 A 被按账号锁定后，同 IP 的其它账号仍可正常尝试登录")
    void login_accountLockDoesNotBlockOtherAccounts() {
        when(usersService.getUserByPhone(PHONE)).thenReturn(new com.smartwash.entity.Users());
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad"));

        // 连续失败 5 次：账号 A 被锁定
        for (int i = 0; i < 5; i++) {
            assertEquals("用户名或密码错误", userLogin(PHONE).getMessage());
        }
        assertEquals("登录尝试过于频繁，请稍后再试", userLogin(PHONE).getMessage(), "第 6 次应命中账号级锁定");

        // 同 IP 换账号 B：未被 IP 限制（IP 失败计数仅 5 < 20），仍走正常认证返回密码错误
        String otherPhone = "13900139000";
        when(usersService.getUserByPhone(otherPhone)).thenReturn(new com.smartwash.entity.Users());
        assertEquals("用户名或密码错误", userLogin(otherPhone).getMessage(),
                "攻击者锁死账号 A 不得波及同 IP 其它账号（防 DoS 锁号）");
    }

    // ==================== 登录响应下发角色 ====================

    @Test
    @DisplayName("管理员登录响应携带 roles 表实际角色名，token 面向 admin-{用户名}")
    void adminLogin_returnsRoleFromRolesTable() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class));
        when(adminUsersService.getAdminRoleName("admin")).thenReturn("root");

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr(IP);
        com.smartwash.from.admin_users.AdminUserLoginFrom from = new com.smartwash.from.admin_users.AdminUserLoginFrom();
        from.setUsername("admin");
        from.setPassword("Admin@123456");

        Result<com.smartwash.vo.LoginVo> result = controller.login(from, request);

        assertEquals(200, result.getCode(), "管理员登录应成功");
        assertNotNull(result.getData(), "登录响应必须携带 token 与角色");
        assertEquals("root", result.getData().getRole(), "角色必须取该管理员实际绑定的角色名，而非硬编码");
        assertNotNull(result.getData().getToken());
        Claims claims = jwtUtil.getPayloadFromToken(result.getData().getToken());
        assertEquals("admin-admin", claims.getSubject(), "管理员 JWT sub 应为 admin-{用户名}");
    }
}
