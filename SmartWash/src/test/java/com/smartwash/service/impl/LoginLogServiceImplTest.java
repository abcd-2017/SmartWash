package com.smartwash.service.impl;

import com.smartwash.common.LoginResult;
import com.smartwash.common.client.ClientContext;
import com.smartwash.common.client.ClientContextHolder;
import com.smartwash.entity.LoginLog;
import com.smartwash.mapper.LoginLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

/**
 * {@link LoginLogServiceImpl} 登录日志写入单测。
 * 验证平台上下文到登录日志字段的映射，以及无上下文/空结果的兜底。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginLogServiceImpl 登录日志写入")
class LoginLogServiceImplTest {

    @Mock
    private LoginLogMapper mapper;

    private LoginLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoginLogServiceImpl();
        // ServiceImpl 的 baseMapper 位于父类字段，Mockito @InjectMocks 不注入父类，显式反射注入
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @AfterEach
    void tearDown() {
        ClientContextHolder.clear();
    }

    @Test
    @DisplayName("请求上下文存在：platform/渠道/版本/UA/网络写入日志")
    void recordLoginLog_withContext_mapsAllFields() {
        ClientContextHolder.set(ClientContext.fromHeaders("android", "lite", "1.2.3", "wifi", "test-ua"));

        service.recordLoginLog("13800138000", 42L, "203.0.113.1", LoginResult.SUCCESS);

        LoginLog log = captureInserted();
        assertEquals("13800138000", log.getPhone());
        assertEquals(42L, log.getUserId());
        assertEquals("203.0.113.1", log.getIp());
        assertEquals("success", log.getResult());
        assertEquals("android", log.getPlatform());
        assertEquals("lite", log.getAppChannel());
        assertEquals("1.2.3", log.getAppVersion());
        assertEquals("test-ua", log.getUa());
        assertEquals("wifi", log.getNetworkType());
    }

    @Test
    @DisplayName("无请求上下文（非 Web 线程）：平台/渠道/网络退化为 unknown，不抛异常")
    void recordLoginLog_withoutContext_defaultsUnknown() {
        service.recordLoginLog("admin", null, "127.0.0.1", LoginResult.FAIL);

        LoginLog log = captureInserted();
        assertEquals("unknown", log.getPlatform());
        assertEquals("unknown", log.getAppChannel());
        assertEquals("unknown", log.getNetworkType());
        assertNull(log.getAppVersion());
        assertNull(log.getUa());
        assertEquals("fail", log.getResult());
        assertNull(log.getUserId());
    }

    @Test
    @DisplayName("结果为 null 时按失败落库，避免空结果脏数据")
    void recordLoginLog_nullResult_fallsBackToFail() {
        service.recordLoginLog("13800138000", null, "127.0.0.1", null);
        assertEquals("fail", captureInserted().getResult());
    }

    private LoginLog captureInserted() {
        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }
}
