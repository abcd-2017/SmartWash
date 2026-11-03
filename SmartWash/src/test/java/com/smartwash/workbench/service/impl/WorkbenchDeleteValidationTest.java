package com.smartwash.workbench.service.impl;

import com.smartwash.config.ExceptionControllerAdvice;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.workbench.controller.DeeplinkRouteAdminController;
import com.smartwash.workbench.controller.WorkbenchEntryAdminController;
import com.smartwash.workbench.mapper.AppDeeplinkRouteMapper;
import com.smartwash.workbench.mapper.WorkbenchEntryMapper;
import com.smartwash.workbench.service.DeeplinkRouteService;
import com.smartwash.workbench.service.WorkbenchEntryService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作台批量删除入参校验回归测试（分支事故重做）。
 * 覆盖两条防线：
 * 1. controller 层：@Validated 类级校验 + @PathVariable ids 上的 @Pattern，畸形入参必须被
 *    ExceptionControllerAdvice 的 ConstraintViolationException 分支转成 HTTP 400 + 统一 Result 信封，
 *    且不触达 service（避免 NumberFormatException 直达全局兜底）。
 * 2. service 层：deleteRoutes/deleteEntries 的 parseId 防御——合法 ids 逐个物理删除；
 *    畸形分段抛 CustomExceptions 业务提示，已解析部分按事务语义处理。
 * 控制器层用 Spring 的 MethodValidationInterceptor 代理 @Validated 控制器，
 * 还原生产环境 MethodValidationPostProcessor 抛 ConstraintViolationException 的路径（纯 Mockito 单测，无 Spring 容器）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Workbench 批量删除 ids 校验与 parseId 防御测试")
class WorkbenchDeleteValidationTest {

    @Mock
    private DeeplinkRouteService deeplinkRouteService;
    @Mock
    private WorkbenchEntryService workbenchEntryService;
    @Mock
    private AppDeeplinkRouteMapper routeMapper;
    @Mock
    private WorkbenchEntryMapper entryMapper;

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private MockMvc deeplinkMockMvc;
    private MockMvc entryMockMvc;
    private DeeplinkRouteServiceImpl routeService;
    private WorkbenchEntryServiceImpl entryService;

    @BeforeAll
    static void initValidator() {
        // 与生产一致的 Bean Validation 实现（Hibernate Validator），用于方法参数校验
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        // 控制器层：代理 @Validated 控制器，使 @Pattern 校验抛 ConstraintViolationException
        deeplinkMockMvc = mockMvc(new DeeplinkRouteAdminController(deeplinkRouteService));
        entryMockMvc = mockMvc(new WorkbenchEntryAdminController(workbenchEntryService));

        // 服务层：纯 Mockito，ServiceImpl 内置方法走父类 baseMapper 字段
        routeService = new DeeplinkRouteServiceImpl();
        ReflectionTestUtils.setField(routeService, "baseMapper", routeMapper);
        entryService = new WorkbenchEntryServiceImpl();
        ReflectionTestUtils.setField(entryService, "baseMapper", entryMapper);
    }

    /** 用 MethodValidationInterceptor 包装控制器，并挂上全局异常处理，还原真实请求链路 */
    private MockMvc mockMvc(Object controller) {
        ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor(validator));
        return MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
                .setControllerAdvice(new ExceptionControllerAdvice())
                .build();
    }

    // ==================== controller 层：畸形 ids → 400 ====================

    @Test
    @DisplayName("路由删除：非数字 ids（含空分段/负数）返回 400 与统一错误提示，不触达 service")
    void deleteRoutes_nonNumericIds_rejectedWith400() throws Exception {
        for (String invalid : new String[]{"abc", "1,,2", "-1"}) {
            deeplinkMockMvc.perform(delete("/admin/deeplink/routes/delete/{ids}", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("ids 必须为逗号分隔的数字"));
        }

        verifyNoInteractions(deeplinkRouteService);
    }

    @Test
    @DisplayName("入口删除：非数字 ids（含空分段/负数）返回 400 与统一错误提示，不触达 service")
    void deleteEntries_nonNumericIds_rejectedWith400() throws Exception {
        for (String invalid : new String[]{"abc", "1,,2", "-1"}) {
            entryMockMvc.perform(delete("/admin/workbench/entries/delete/{ids}", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("ids 必须为逗号分隔的数字"));
        }

        verifyNoInteractions(workbenchEntryService);
    }

    // ==================== service 层：parseId 防御 ====================

    @Test
    @DisplayName("路由删除：合法 ids 逐个物理删除（parseId 正确解析为 Long）")
    void deleteRoutes_validIds_deletesEachId() {
        when(routeMapper.deleteById(anyLong())).thenReturn(1);

        routeService.deleteRoutes("1,2,3");

        verify(routeMapper).deleteById(1L);
        verify(routeMapper).deleteById(2L);
        verify(routeMapper).deleteById(3L);
        verify(routeMapper, times(3)).deleteById(anyLong());
        verify(routeMapper, never()).deleteById(4L);
    }

    @Test
    @DisplayName("入口删除：畸形分段（1,abc）由 parseId 防御拒绝，仅已解析的 1 被删除")
    void deleteEntries_malformedId_parseIdDefense() {
        when(entryMapper.deleteById(anyLong())).thenReturn(1);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> entryService.deleteEntries("1,abc"));

        assertEquals("ids 必须为逗号分隔的数字", ex.getMessage());
        verify(entryMapper).deleteById(1L);
        verify(entryMapper, times(1)).deleteById(anyLong());
    }
}
