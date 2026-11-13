package com.smartwash.toolbox.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.common.crypto.ToolboxGcmCipher;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.from.AddShortCodeFrom;
import com.smartwash.toolbox.from.UpdateShortCodeFrom;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import com.smartwash.toolbox.service.ToolboxShortCodeService.ResolveResult;
import com.smartwash.toolbox.toolkit.PublicBaseUrlResolver;
import com.smartwash.toolbox.toolkit.SecretState;
import com.smartwash.toolbox.toolkit.ShortCodeGenerator;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
import com.smartwash.toolbox.vo.SecretMetaVo;
import com.smartwash.toolbox.vo.SecretViewVo;
import com.smartwash.toolbox.vo.ShortCodeVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 私密文本（阅后即焚）服务测试。
 * 覆盖：创建加密落库与强制字段、meta 状态分支、一次性揭示（焚毁/口令/过期）、
 * owner 对焚毁态可见、非跳转类型 410、终态拒绝更新。
 * 说明：Mockito 单测无法复现真实行锁并发，并发焚毁竞态由事务内 SELECT FOR UPDATE + 条件 UPDATE
 * 的数据库语义保证，人工验证步骤见任务清单进度日志。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolboxShortCodeServiceImpl 私密文本（阅后即焚）测试")
class ToolboxSecretServiceImplTest {

    private static final Long OWNER_ID = 10L;
    private static final String CODE = "s3cretX";
    private static final String PLAIN = "宿舍 WiFi 密码：Campus#2026";
    private static final String CIPHER = "v1:dGVzdElW:dGVzdENpcGhlcnRleHQ=";

    @Mock
    private ToolboxShortCodeMapper shortCodeMapper;
    @Mock
    private ToolboxShortVisitService visitService;
    @Mock
    private UsersMapper usersMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PublicBaseUrlResolver publicBaseUrlResolver;
    @Mock
    private WebBaseUrlResolver webBaseUrlResolver;
    @Mock
    private ToolboxGcmCipher toolboxGcmCipher;
    @Mock
    private PasswordEncoder passwordEncoder;

    /** 模拟 Redis 的字符串存储 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final ShortCodeGenerator generator = new ShortCodeGenerator();
    private ToolboxShortCodeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ToolboxShortCode.class);
    }

    @BeforeEach
    void setUp() {
        service = new ToolboxShortCodeServiceImpl(generator, visitService, stringRedisTemplate, usersMapper,
                publicBaseUrlResolver, webBaseUrlResolver, toolboxGcmCipher, passwordEncoder);
        ReflectionTestUtils.setField(service, "baseMapper", shortCodeMapper);
        when(publicBaseUrlResolver.resolve(any())).thenReturn("http://test.local");
        when(webBaseUrlResolver.resolve()).thenReturn("https://web.test.local");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey(anyString()))
                .thenAnswer(inv -> cache.containsKey(inv.getArgument(0, String.class)));
        when(stringRedisTemplate.delete(anyString()))
                .thenAnswer(inv -> cache.remove(inv.getArgument(0, String.class)) != null);

        // 假加密器：可断言落库值与明文不同且格式带版本段
        when(toolboxGcmCipher.encrypt(anyString(), anyInt()))
                .thenAnswer(inv -> "v" + inv.getArgument(1, Integer.class) + ":dGVzdElW:"
                        + java.util.Base64.getEncoder().encodeToString(
                        inv.getArgument(0, String.class).getBytes()));
        when(toolboxGcmCipher.decrypt(CIPHER)).thenReturn(PLAIN);
    }

    private AddShortCodeFrom secretFrom(String content, String password, LocalDateTime expireAt) {
        AddShortCodeFrom from = new AddShortCodeFrom();
        from.setContentType(7);
        from.setContent(content);
        from.setPassword(password);
        from.setExpireAt(expireAt);
        return from;
    }

    private ToolboxShortCode secretRecord(LocalDateTime expireAt, LocalDateTime burnedAt, String passwordHash) {
        return new ToolboxShortCode()
                .setId(1L)
                .setCode(CODE)
                .setContentType(7)
                .setContent(CIPHER)
                .setOwnerUserId(OWNER_ID)
                .setIsPublic(1)
                .setPasswordHash(passwordHash)
                .setBurnedAt(burnedAt)
                .setExpireAt(expireAt)
                .setClickCount(0L);
    }

    // ==================== 创建 ====================

    @Test
    @DisplayName("创建私密文本：正文加密落库、口令 BCrypt、恒公开、burnAfterRead=1、secretUrl 拼接")
    void create_secret_encryptsAndForcesFields() {
        when(shortCodeMapper.insert(any(ToolboxShortCode.class))).thenReturn(1);
        when(passwordEncoder.encode("op-1234")).thenReturn("$2a$10$hash");

        LocalDateTime expire = LocalDateTime.now().plusDays(1);
        ShortCodeVo vo = service.createShortCode(secretFrom(PLAIN, "op-1234", expire), OWNER_ID);

        org.mockito.ArgumentCaptor<ToolboxShortCode> captor =
                org.mockito.ArgumentCaptor.forClass(ToolboxShortCode.class);
        verify(shortCodeMapper).insert(captor.capture());
        ToolboxShortCode saved = captor.getValue();
        assertEquals(7, saved.getContentType());
        assertNotEquals(PLAIN, saved.getContent(), "库中不得出现明文");
        assertTrue(saved.getContent().startsWith("v1:"), "密文须带版本段");
        assertEquals("$2a$10$hash", saved.getPasswordHash());
        assertEquals(1, saved.getBurnAfterRead());
        assertEquals(1, saved.getIsPublic(), "私密文本恒公开（接收者匿名查看）");
        assertEquals("https://web.test.local/s/" + saved.getCode(), vo.getSecretUrl());
        assertEquals(SecretState.ACTIVE, vo.getSecretState());
    }

    @Test
    @DisplayName("创建私密文本：缺过期时间或空内容均拒绝")
    void create_secret_rejectsMissingExpireOrBlankContent() {
        assertThrows(CustomExceptions.class,
                () -> service.createShortCode(secretFrom(PLAIN, null, null), OWNER_ID));
        assertThrows(CustomExceptions.class,
                () -> service.createShortCode(secretFrom("  ", null, LocalDateTime.now().plusDays(1)), OWNER_ID));
    }

    // ==================== meta ====================

    @Test
    @DisplayName("meta：不存在/非私密文本 404；焚毁与过期 410；ACTIVE 回口令需求")
    void meta_branches() {
        when(shortCodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        CustomExceptions notFound = assertThrows(CustomExceptions.class, () -> service.secretMeta(CODE));
        assertEquals(404, notFound.getCode());

        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), LocalDateTime.now(), null));
        CustomExceptions gone = assertThrows(CustomExceptions.class, () -> service.secretMeta(CODE));
        assertEquals(410, gone.getCode());

        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().minusDays(1), null, null));
        CustomExceptions expired = assertThrows(CustomExceptions.class, () -> service.secretMeta(CODE));
        assertEquals(410, expired.getCode());

        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), null, "$2a$10$hash"));
        SecretMetaVo withPassword = service.secretMeta(CODE);
        assertTrue(withPassword.isRequiresPassword());

        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), null, null));
        SecretMetaVo withoutPassword = service.secretMeta(CODE);
        assertTrue(!withoutPassword.isRequiresPassword());
    }

    // ==================== 一次性揭示 ====================

    @Test
    @DisplayName("揭示成功：条件焚毁一次写入并解密返回明文")
    void view_success_burnsAndReturnsPlaintext() {
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), null, null));
        when(shortCodeMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        SecretViewVo vo = service.viewSecret(CODE, null);

        assertEquals(PLAIN, vo.getContent());
        verify(shortCodeMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("口令错误：403 且不焚毁（update 不触发）")
    void view_wrongPassword_forbiddenWithoutBurn() {
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), null, "$2a$10$hash"));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        CustomExceptions e = assertThrows(CustomExceptions.class, () -> service.viewSecret(CODE, "wrong-pass"));

        assertEquals(403, e.getCode());
        verify(shortCodeMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("已焚毁/已过期：揭示一律 410")
    void view_burnedOrExpired_gone() {
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), LocalDateTime.now(), null));
        CustomExceptions burned = assertThrows(CustomExceptions.class, () -> service.viewSecret(CODE, null));
        assertEquals(410, burned.getCode());

        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().minusHours(1), null, null));
        CustomExceptions expired = assertThrows(CustomExceptions.class, () -> service.viewSecret(CODE, null));
        assertEquals(410, expired.getCode());
    }

    // ==================== owner 可见性与终态保护 ====================

    @Test
    @DisplayName("resolveForOwner：焚毁态对 owner 可见（secretState=BURNED），不抛异常")
    void resolveForOwner_burnedStillVisible() {
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), LocalDateTime.now(), null));

        ShortCodeVo vo = service.resolveForOwner(CODE, OWNER_ID);

        assertEquals(SecretState.BURNED, vo.getSecretState());
        assertNotNull(vo.getBurnedAt());
    }

    @Test
    @DisplayName("resolveForRedirect：私密文本（target 空）走 /web/t 一律 410")
    void resolveForRedirect_secretUnavailable() {
        when(shortCodeMapper.selectOne(any(Wrapper.class)))
                .thenReturn(secretRecord(LocalDateTime.now().plusDays(1), null, null));

        ResolveResult result = service.resolveForRedirect(CODE);

        assertTrue(result.isUnavailable());
    }

    @Test
    @DisplayName("updateShortCode：焚毁态拒绝更新（409）；未焚毁允许改过期时间")
    void update_secret_terminalRejected() {
        when(shortCodeMapper.selectById(1L)).thenReturn(
                secretRecord(LocalDateTime.now().plusDays(1), LocalDateTime.now(), null));
        CustomExceptions conflict = assertThrows(CustomExceptions.class,
                () -> service.updateShortCode(1L, new UpdateShortCodeFrom(), OWNER_ID));
        assertEquals(409, conflict.getCode());

        ToolboxShortCode active = secretRecord(LocalDateTime.now().plusDays(1), null, null);
        when(shortCodeMapper.selectById(1L)).thenReturn(active);
        UpdateShortCodeFrom renew = new UpdateShortCodeFrom();
        renew.setExpireAt(LocalDateTime.now().plusDays(7));
        ShortCodeVo vo = service.updateShortCode(1L, renew, OWNER_ID);
        assertEquals(SecretState.ACTIVE, vo.getSecretState());
    }
}
