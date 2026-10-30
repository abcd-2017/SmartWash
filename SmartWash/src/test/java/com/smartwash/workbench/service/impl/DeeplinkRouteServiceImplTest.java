package com.smartwash.workbench.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.workbench.entity.AppDeeplinkRoute;
import com.smartwash.workbench.from.AddDeeplinkRouteFrom;
import com.smartwash.workbench.from.UpdateDeeplinkRouteFrom;
import com.smartwash.workbench.mapper.AppDeeplinkRouteMapper;
import com.smartwash.workbench.vo.DeeplinkRouteVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * deeplink 路由字典服务测试。
 * 覆盖：新增 host 重复拒绝（先查重 + 唯一索引兜底）、更新分支（不存在拒绝/host 不可改/空串清空）、
 * 公开下发列表 enabled 过滤、批量删除。
 * 纯 Mockito 单测，baseMapper 与 self（缓存自注入）手动注入，不校验缓存注解本身。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DeeplinkRouteServiceImpl 增删改查与下发过滤测试")
class DeeplinkRouteServiceImplTest {

    @Mock
    private AppDeeplinkRouteMapper routeMapper;

    private DeeplinkRouteServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AppDeeplinkRoute.class);
    }

    @BeforeEach
    void setUp() {
        service = new DeeplinkRouteServiceImpl();
        // ServiceImpl 的内置方法走父类 baseMapper 字段，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", routeMapper);
        // 缓存自注入：单测无 Spring 代理，指向自身即可打通 listEnabledRoutes → enabledRoutesFromCache
        ReflectionTestUtils.setField(service, "self", service);
    }

    private AddDeeplinkRouteFrom addFrom(String host) {
        AddDeeplinkRouteFrom from = new AddDeeplinkRouteFrom();
        from.setHost(host);
        from.setName("登录页");
        from.setPlatform(3);
        from.setRequireLogin(0);
        from.setDescription(null);
        return from;
    }

    private AppDeeplinkRoute record(Long id, String host, Integer enabled) {
        return new AppDeeplinkRoute()
                .setId(id)
                .setHost(host)
                .setName("页面" + id)
                .setPlatform(3)
                .setRequireLogin(0)
                .setEnabled(enabled);
    }

    // ==================== 新增：host 重复校验 ====================

    @Test
    @DisplayName("新增：host 已存在时拒绝，不触库")
    void add_duplicateHost_rejected() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(record(9L, "login", 1));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addRoute(addFrom("login")));

        assertEquals("host 已存在", ex.getMessage());
        verify(routeMapper, never()).insert(any(AppDeeplinkRoute.class));
    }

    @Test
    @DisplayName("新增：并发窗口下唯一索引兜底裁决，DuplicateKeyException 转业务提示")
    void add_uniqueIndexFallback_rejected() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(routeMapper.insert(any(AppDeeplinkRoute.class))).thenThrow(new DuplicateKeyException("uk_host"));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addRoute(addFrom("login")));

        assertEquals("host 已存在", ex.getMessage());
    }

    @Test
    @DisplayName("新增：字段完整映射落库（默认 platform=3 双端）")
    void add_success_mapsAllFields() {
        when(routeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(routeMapper.insert(any(AppDeeplinkRoute.class))).thenReturn(1);

        AddDeeplinkRouteFrom from = addFrom("web");
        from.setDescription("scheme 说明：smartwash://web?url= 打开内置 WebView");
        service.addRoute(from);

        ArgumentCaptor<AppDeeplinkRoute> captor = ArgumentCaptor.forClass(AppDeeplinkRoute.class);
        verify(routeMapper).insert(captor.capture());
        AppDeeplinkRoute saved = captor.getValue();
        assertEquals("web", saved.getHost());
        assertEquals("登录页", saved.getName());
        assertEquals(3, saved.getPlatform());
        assertEquals(0, saved.getRequireLogin());
        assertEquals(1, saved.getEnabled(), "默认启用");
        assertTrue(saved.getDescription().contains("smartwash://web?url="));
    }

    // ==================== 公开下发列表：enabled 过滤 ====================

    @Test
    @DisplayName("公开下发：仅查 enabled=1，按 id 升序，Vo 完整映射")
    void listEnabledRoutes_filtersEnabledAndMaps() {
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                record(1L, "login", 1),
                record(2L, "web", 1).setRequireLogin(1)));

        List<DeeplinkRouteVo> voList = service.listEnabledRoutes();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AppDeeplinkRoute>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(routeMapper).selectList(captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(1), "enabled=1 过滤必须进入查询条件");

        assertEquals(2, voList.size());
        DeeplinkRouteVo vo = voList.get(0);
        assertEquals("login", vo.getHost());
        assertEquals("页面1", vo.getName());
        assertEquals(3, vo.getPlatform());
        assertEquals(0, vo.getRequireLogin());
        assertEquals(1, voList.get(1).getRequireLogin());
    }

    // ==================== 更新 / 删除 ====================

    @Test
    @DisplayName("更新：路由不存在时拒绝")
    void update_missing_rejected() {
        when(routeMapper.selectById(404L)).thenReturn(null);

        UpdateDeeplinkRouteFrom from = new UpdateDeeplinkRouteFrom();
        from.setId(404L);
        from.setName("新名称");
        assertThrows(CustomExceptions.class, () -> service.updateRoute(from));
        verify(routeMapper, never()).updateById(any(AppDeeplinkRoute.class));
    }

    @Test
    @DisplayName("更新：仅覆盖传入字段；host 不变；description 空串清空为 NULL")
    void update_partialFields_appliedAndEmptyStringClears() {
        AppDeeplinkRoute existing = record(1L, "web", 1)
                .setDescription("旧说明").setName("内置 WebView");
        when(routeMapper.selectById(1L)).thenReturn(existing);
        when(routeMapper.updateById(any(AppDeeplinkRoute.class))).thenReturn(1);

        UpdateDeeplinkRouteFrom from = new UpdateDeeplinkRouteFrom();
        from.setId(1L);
        from.setRequireLogin(1);
        from.setDescription(""); // 空串=清空说明
        service.updateRoute(from);

        ArgumentCaptor<AppDeeplinkRoute> captor = ArgumentCaptor.forClass(AppDeeplinkRoute.class);
        verify(routeMapper).updateById(captor.capture());
        AppDeeplinkRoute updated = captor.getValue();
        assertEquals(1, updated.getRequireLogin(), "传入字段必须更新");
        assertNull(updated.getDescription(), "description 空串必须清空为 NULL");
        assertEquals("内置 WebView", updated.getName(), "未传字段不得被修改");
        assertEquals("web", updated.getHost(), "host 是端上路由分发标识，不可修改");
    }

    @Test
    @DisplayName("删除：逗号分隔 id 逐个物理删除")
    void deleteRoutes_splitsIdsAndDeletes() {
        when(routeMapper.deleteById(anyLong())).thenReturn(1);

        service.deleteRoutes("5,6");

        verify(routeMapper).deleteById(5L);
        verify(routeMapper).deleteById(6L);
    }
}
