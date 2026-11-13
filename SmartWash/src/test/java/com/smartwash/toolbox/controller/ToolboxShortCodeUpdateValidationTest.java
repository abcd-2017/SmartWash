package com.smartwash.toolbox.controller;

import com.smartwash.common.crypto.ToolboxGcmCipher;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.toolbox.entity.ToolboxShortCode;
import com.smartwash.toolbox.from.UpdateShortCodeFrom;
import com.smartwash.toolbox.mapper.ToolboxShortCodeMapper;
import com.smartwash.toolbox.service.ToolboxShortVisitService;
import com.smartwash.toolbox.service.impl.ToolboxShortCodeServiceImpl;
import com.smartwash.toolbox.toolkit.PublicBaseUrlResolver;
import com.smartwash.toolbox.toolkit.WebBaseUrlResolver;
import com.smartwash.toolbox.toolkit.ShortCodeGenerator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短码更新入参校验回归测试（分支事故重做）。
 * 覆盖两条防线：
 * 1. DTO 层：UpdateShortCodeFrom.target 的 @Size(max=2048) 与 @Pattern(^(https?://).+) 注解，
 *    用真实 Bean Validation 校验器触发，并反射断言注解属性存在（防止注解被误删后校验静默失效）。
 * 2. service 层：expireAt 传值必须晚于当前时刻，否则 ToolboxShortCodeServiceImpl 抛业务异常且不落库。
 * 纯单测，不加载 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolboxShortCode 更新入参校验测试")
class ToolboxShortCodeUpdateValidationTest {

    private static final Long OWNER_ID = 10L;

    @Mock
    private ToolboxShortCodeMapper shortCodeMapper;
    @Mock
    private ToolboxShortVisitService visitService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private UsersMapper usersMapper;
    @Mock
    private PublicBaseUrlResolver publicBaseUrlResolver;
    @Mock
    private WebBaseUrlResolver webBaseUrlResolver;
    @Mock
    private ToolboxGcmCipher toolboxGcmCipher;
    @Mock
    private PasswordEncoder passwordEncoder;

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private ToolboxShortCodeServiceImpl service;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        service = new ToolboxShortCodeServiceImpl(new ShortCodeGenerator(), visitService,
                stringRedisTemplate, usersMapper, publicBaseUrlResolver, webBaseUrlResolver,
                toolboxGcmCipher, passwordEncoder);
        // ServiceImpl 的 getById/updateById 等内置方法走父类 baseMapper 字段
        ReflectionTestUtils.setField(service, "baseMapper", shortCodeMapper);
    }

    private UpdateShortCodeFrom fromWithTarget(String target) {
        UpdateShortCodeFrom from = new UpdateShortCodeFrom();
        from.setTarget(target);
        return from;
    }

    // ==================== DTO 层：target 约束 ====================

    @Test
    @DisplayName("target 超过 2048 字符：@Size 校验拒绝，且注解属性存在")
    void updateShortCode_targetTooLong_violatesSize() throws NoSuchFieldException {
        String target = "https://example.com/" + "a".repeat(2048);
        assertTrue(target.length() > 2048, "构造的 target 必须超过 2048 字符");

        Set<ConstraintViolation<UpdateShortCodeFrom>> violations = validator.validate(fromWithTarget(target));

        assertFalse(violations.isEmpty(), "超长 target 必须触发校验失败");
        ConstraintViolation<UpdateShortCodeFrom> violation = violations.iterator().next();
        assertEquals("target", violation.getPropertyPath().toString());
        assertEquals("目标链接过长（上限2048字符）", violation.getMessage());

        // 注解存在性兜底：即使校验器配置变化，注解与上限值也必须保留
        Size size = UpdateShortCodeFrom.class.getDeclaredField("target").getAnnotation(Size.class);
        assertNotNull(size, "target 必须保留 @Size 注解");
        assertEquals(2048, size.max());
    }

    @Test
    @DisplayName("target 非 http/https（ftp://）：@Pattern 校验拒绝，且注解属性存在")
    void updateShortCode_targetNotHttp_violatesPattern() throws NoSuchFieldException {
        Set<ConstraintViolation<UpdateShortCodeFrom>> violations =
                validator.validate(fromWithTarget("ftp://example.com/page"));

        assertEquals(1, violations.size(), "非 http/https 的 target 必须恰好触发一条校验失败");
        ConstraintViolation<UpdateShortCodeFrom> violation = violations.iterator().next();
        assertEquals("target", violation.getPropertyPath().toString());
        assertEquals("目标链接必须为 http/https 地址", violation.getMessage());

        // 注解存在性兜底：正则必须保留
        Pattern pattern = UpdateShortCodeFrom.class.getDeclaredField("target").getAnnotation(Pattern.class);
        assertNotNull(pattern, "target 必须保留 @Pattern 注解");
        assertEquals("^(https?://).+", pattern.regexp());
    }

    // ==================== service 层：expireAt 必须为未来时间 ====================

    @Test
    @DisplayName("expireAt 早于当前时刻：service 层抛业务异常，不落库、不清缓存")
    void updateShortCode_expireAtInPast_rejectedByService() {
        ToolboxShortCode record = new ToolboxShortCode()
                .setId(1L)
                .setCode("abc1234")
                .setOwnerUserId(OWNER_ID);
        when(shortCodeMapper.selectById(1L)).thenReturn(record);

        UpdateShortCodeFrom from = new UpdateShortCodeFrom();
        from.setExpireAt(LocalDateTime.now().minusMinutes(1));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.updateShortCode(1L, from, OWNER_ID));

        assertEquals("过期时间必须为未来时间", ex.getMessage());
        verify(shortCodeMapper, never()).updateById(any(ToolboxShortCode.class));
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }
}
