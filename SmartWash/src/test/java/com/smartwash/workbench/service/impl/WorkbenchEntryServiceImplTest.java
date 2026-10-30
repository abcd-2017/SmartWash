package com.smartwash.workbench.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.workbench.entity.WorkbenchEntry;
import com.smartwash.workbench.from.AddWorkbenchEntryFrom;
import com.smartwash.workbench.from.SearchWorkbenchEntryFrom;
import com.smartwash.workbench.from.UpdateWorkbenchEntryFrom;
import com.smartwash.workbench.mapper.WorkbenchEntryMapper;
import com.smartwash.workbench.vo.AdminWorkbenchEntryVo;
import com.smartwash.workbench.vo.WorkbenchEntryVo;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作台入口配置服务测试。
 * 覆盖：新增 entry_key 重复拒绝（先查重 + 唯一索引兜底）、更新分支（不存在拒绝/空串清空/entry_key 不可改）、
 * 用户端列表 enabled 过滤 + sort/id 升序、管理端分页筛选、批量删除。
 * 纯 Mockito 单测，baseMapper 与 self（缓存自注入）手动注入，不校验缓存注解本身。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkbenchEntryServiceImpl 增删改查与下发过滤测试")
class WorkbenchEntryServiceImplTest {

    @Mock
    private WorkbenchEntryMapper entryMapper;

    private WorkbenchEntryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WorkbenchEntry.class);
    }

    @BeforeEach
    void setUp() {
        service = new WorkbenchEntryServiceImpl();
        // ServiceImpl 的内置方法走父类 baseMapper 字段，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", entryMapper);
        // 缓存自注入：单测无 Spring 代理，指向自身即可打通 listEnabledEntries → enabledEntriesFromCache
        ReflectionTestUtils.setField(service, "self", service);
    }

    private AddWorkbenchEntryFrom addFrom(String entryKey) {
        AddWorkbenchEntryFrom from = new AddWorkbenchEntryFrom();
        from.setEntryKey(entryKey);
        from.setTitle("短链工具箱");
        from.setDescription("生成与管理短链");
        from.setIcon("link");
        from.setColor("blue");
        from.setTarget("smartwash://toolbox-short-code");
        from.setSort(1);
        return from;
    }

    private WorkbenchEntry record(Long id, String entryKey, Integer enabled, Integer sort) {
        return new WorkbenchEntry()
                .setId(id)
                .setEntryKey(entryKey)
                .setTitle("入口" + id)
                .setIcon("link")
                .setColor("blue")
                .setTarget("smartwash://" + entryKey)
                .setEnabled(enabled)
                .setSort(sort);
    }

    // ==================== 新增：entry_key 重复校验 ====================

    @Test
    @DisplayName("新增：entry_key 已存在时拒绝，不触库")
    void add_duplicateEntryKey_rejected() {
        when(entryMapper.selectOne(any(Wrapper.class))).thenReturn(record(9L, "toolbox-short-code", 1, 1));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addEntry(addFrom("toolbox-short-code")));

        assertEquals("entry_key 已存在", ex.getMessage());
        verify(entryMapper, never()).insert(any(WorkbenchEntry.class));
    }

    @Test
    @DisplayName("新增：并发窗口下唯一索引兜底裁决，DuplicateKeyException 转业务提示")
    void add_uniqueIndexFallback_rejected() {
        when(entryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(entryMapper.insert(any(WorkbenchEntry.class))).thenThrow(new DuplicateKeyException("uk_entry_key"));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addEntry(addFrom("race-key")));

        assertEquals("entry_key 已存在", ex.getMessage());
    }

    @Test
    @DisplayName("新增：字段完整映射落库")
    void add_success_mapsAllFields() {
        when(entryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(entryMapper.insert(any(WorkbenchEntry.class))).thenReturn(1);

        service.addEntry(addFrom("toolbox-short-code"));

        ArgumentCaptor<WorkbenchEntry> captor = ArgumentCaptor.forClass(WorkbenchEntry.class);
        verify(entryMapper).insert(captor.capture());
        WorkbenchEntry saved = captor.getValue();
        assertEquals("toolbox-short-code", saved.getEntryKey());
        assertEquals("短链工具箱", saved.getTitle());
        assertEquals("生成与管理短链", saved.getDescription());
        assertEquals("link", saved.getIcon());
        assertEquals("blue", saved.getColor());
        assertEquals("smartwash://toolbox-short-code", saved.getTarget());
        assertEquals(1, saved.getEnabled(), "默认启用");
        assertEquals(1, saved.getSort());
    }

    // ==================== 用户端列表：enabled 过滤 + sort 排序 ====================

    @Test
    @DisplayName("用户端列表：仅查 enabled=1，按 sort 升序、id 升序，不下发管理字段")
    void listEnabledEntries_filtersEnabledAndOrdersBySortThenId() {
        when(entryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                record(2L, "my-history", 1, 2),
                record(1L, "toolbox-short-code", 1, 1)));

        List<WorkbenchEntryVo> voList = service.listEnabledEntries();

        // 过滤与排序语义落在查询条件上
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<WorkbenchEntry>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(entryMapper).selectList(captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(1), "enabled=1 过滤必须进入查询条件");

        // ORDER BY 段物化校验（列名经 TableInfo 解析后必须为 sort 在前、id 在后，且为升序）
        assertOrderBySegment(captor.getValue(), "sort", "id");

        // Vo 映射：仅下发用户端字段，管理字段不外泄
        assertEquals(2, voList.size());
        WorkbenchEntryVo vo = voList.get(0);
        assertEquals("my-history", vo.getEntryKey());
        assertEquals("smartwash://my-history", vo.getTarget());
        assertEquals(2, vo.getSort());
    }

    @Test
    @DisplayName("用户端列表：停用入口（enabled=0）不得出现在下发结果")
    void listEnabledEntries_disabledEntryExcluded() {
        // 查询条件由 SQL 过滤；此处验证 mock 返回启用项时 Vo 正常映射
        when(entryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record(1L, "ai-assistant", 1, 3)));

        List<WorkbenchEntryVo> voList = service.listEnabledEntries();

        assertEquals(1, voList.size());
        assertEquals("ai-assistant", voList.get(0).getEntryKey());
    }

    // ==================== 更新 / 删除 ====================

    @Test
    @DisplayName("更新：入口不存在时拒绝")
    void update_missing_rejected() {
        when(entryMapper.selectById(404L)).thenReturn(null);

        UpdateWorkbenchEntryFrom from = new UpdateWorkbenchEntryFrom();
        from.setId(404L);
        from.setTitle("新标题");
        assertThrows(CustomExceptions.class, () -> service.updateEntry(from));
        verify(entryMapper, never()).updateById(any(WorkbenchEntry.class));
    }

    @Test
    @DisplayName("更新：仅覆盖传入字段；entry_key 不变；badge 空串清空为 NULL")
    void update_partialFields_appliedAndEmptyStringClears() {
        WorkbenchEntry existing = record(1L, "toolbox-short-code", 1, 1)
                .setBadge("新").setRemark("运营位");
        when(entryMapper.selectById(1L)).thenReturn(existing);
        when(entryMapper.updateById(any(WorkbenchEntry.class))).thenReturn(1);

        UpdateWorkbenchEntryFrom from = new UpdateWorkbenchEntryFrom();
        from.setId(1L);
        from.setTitle("短链工具箱Pro");
        from.setBadge(""); // 空串=清空角标
        service.updateEntry(from);

        ArgumentCaptor<WorkbenchEntry> captor = ArgumentCaptor.forClass(WorkbenchEntry.class);
        verify(entryMapper).updateById(captor.capture());
        WorkbenchEntry updated = captor.getValue();
        assertEquals("短链工具箱Pro", updated.getTitle(), "传入字段必须更新");
        assertNull(updated.getBadge(), "badge 空串必须清空为 NULL");
        assertEquals("运营位", updated.getRemark(), "未传字段不得被修改");
        assertEquals("toolbox-short-code", updated.getEntryKey(), "entry_key 是端上对齐标识，不可修改");
    }

    @Test
    @DisplayName("删除：逗号分隔 id 逐个物理删除")
    void deleteEntries_splitsIdsAndDeletes() {
        when(entryMapper.deleteById(anyLong())).thenReturn(1);

        service.deleteEntries("1,2,3");

        verify(entryMapper).deleteById(1L);
        verify(entryMapper).deleteById(2L);
        verify(entryMapper).deleteById(3L);
    }

    // ==================== 管理端分页 ====================

    @Test
    @DisplayName("管理端分页：title 模糊 + enabled 筛选生效，按 sort/id 升序")
    void adminPage_filtersApplied() {
        Page<WorkbenchEntry> result = new Page<>(1, 10, 0);
        result.setRecords(List.of());
        doReturn(result).when(entryMapper).selectPage(any(), any());

        SearchWorkbenchEntryFrom from = new SearchWorkbenchEntryFrom();
        from.setTitle("短链");
        from.setEnabled(1);
        service.adminPage(from);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<WorkbenchEntry>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(entryMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment();
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        // like 条件的参数值为 %关键词% 形式
        assertTrue(params.values().stream().anyMatch(v -> v instanceof String s && s.contains("短链")),
                "title 模糊筛选必须进入查询条件");
        assertTrue(params.containsValue(1), "enabled 筛选必须进入查询条件");

        // 管理端排序与端上渲染顺序一致：sort 在前、id 在后
        assertOrderBySegment(captor.getValue(), "sort", "id");
    }

    @Test
    @DisplayName("管理端分页：Vo 含管理字段（enabled/remark/id）")
    void adminPage_mapsAdminFields() {
        WorkbenchEntry entry = record(1L, "toolbox-short-code", 1, 1).setRemark("首页运营位");
        Page<WorkbenchEntry> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(entry));
        doReturn(result).when(entryMapper).selectPage(any(), any());

        Page<AdminWorkbenchEntryVo> page = service.adminPage(new SearchWorkbenchEntryFrom());

        assertNotNull(page.getRecords());
        AdminWorkbenchEntryVo vo = page.getRecords().get(0);
        assertEquals(1L, vo.getId());
        assertEquals("toolbox-short-code", vo.getEntryKey());
        assertEquals(1, vo.getEnabled());
        assertEquals("首页运营位", vo.getRemark());
    }

    /**
     * 物化 ORDER BY 段校验：列名经 TableInfo 解析后按传入顺序出现，且为升序。
     * 不依赖 MP 内部 OrderItem API（3.5.7 的 getOrderBy() 返回段列表而非 List<OrderItem>）。
     */
    private void assertOrderBySegment(LambdaQueryWrapper<WorkbenchEntry> wrapper, String firstColumn, String secondColumn) {
        String segment = wrapper.getSqlSegment();
        String upper = segment.toUpperCase();
        int orderByIdx = upper.indexOf("ORDER BY");
        assertTrue(orderByIdx >= 0, "查询必须带 ORDER BY 段，实际: " + segment);
        String orderPart = segment.substring(orderByIdx);
        int firstPos = orderPart.indexOf(firstColumn);
        int secondPos = orderPart.indexOf(secondColumn);
        assertTrue(firstPos >= 0 && secondPos > firstPos,
                "必须先按 " + firstColumn + " 再按 " + secondColumn + " 排序，实际: " + orderPart);
        assertTrue(!orderPart.contains("DESC"), "必须为升序排序，实际: " + orderPart);
    }
}
