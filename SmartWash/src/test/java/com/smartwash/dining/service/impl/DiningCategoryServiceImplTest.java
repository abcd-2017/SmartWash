package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.dining.entity.DiningCategory;
import com.smartwash.dining.from.AddDiningCategoryFrom;
import com.smartwash.dining.from.SearchDiningCategoryFrom;
import com.smartwash.dining.from.UpdateDiningCategoryFrom;
import com.smartwash.dining.mapper.DiningCategoryMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.dining.vo.DiningCategoryVo;
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
 * 菜谱分类服务测试（对齐 WorkbenchEntryServiceImplTest 的纯 Mockito 先例）。
 * 覆盖外部行为契约：新增重名拒绝（先查重 + 唯一索引兜底）、更新分支（不存在拒绝/重名排除自身/
 * 字段 null 不覆盖）、管理端分页（name 模糊 + sort/id 升序 + Vo 映射）、批量物理删除。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiningCategoryServiceImpl 增删改查测试")
class DiningCategoryServiceImplTest {

    @Mock
    private DiningCategoryMapper categoryMapper;

    @Mock
    private DiningRecipeMapper recipeMapper;

    private DiningCategoryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DiningCategory.class);
        TableInfoHelper.initTableInfo(assistant, com.smartwash.dining.entity.DiningRecipe.class);
    }

    @BeforeEach
    void setUp() {
        service = new DiningCategoryServiceImpl();
        // ServiceImpl 的内置方法走父类 baseMapper 字段，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "diningRecipeMapper", recipeMapper);
    }

    private AddDiningCategoryFrom addFrom(String name) {
        AddDiningCategoryFrom from = new AddDiningCategoryFrom();
        from.setName(name);
        from.setSort(1);
        return from;
    }

    private DiningCategory record(Long id, String name, Integer sort) {
        return new DiningCategory().setId(id).setName(name).setSort(sort);
    }

    // ==================== 新增：重名校验 ====================

    @Test
    @DisplayName("新增：分类名已存在时拒绝，不触库")
    void add_duplicateName_rejected() {
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(record(9L, "家常菜", 1));

        expectBusinessException("分类名称已存在", () -> service.addCategory(addFrom("家常菜")));

        verify(categoryMapper, never()).insert(any(DiningCategory.class));
    }

    @Test
    @DisplayName("新增：并发窗口下唯一索引兜底裁决，DuplicateKeyException 转业务提示")
    void add_uniqueIndexFallback_rejected() {
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(categoryMapper.insert(any(DiningCategory.class))).thenThrow(new DuplicateKeyException("uk_name"));

        expectBusinessException("分类名称已存在", () -> service.addCategory(addFrom("race-name")));
    }

    @Test
    @DisplayName("新增：字段完整映射落库")
    void add_success_mapsAllFields() {
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(categoryMapper.insert(any(DiningCategory.class))).thenReturn(1);

        service.addCategory(addFrom("汤羹"));

        ArgumentCaptor<DiningCategory> captor = ArgumentCaptor.forClass(DiningCategory.class);
        verify(categoryMapper).insert(captor.capture());
        DiningCategory saved = captor.getValue();
        assertEquals("汤羹", saved.getName());
        assertEquals(1, saved.getSort());
    }

    // ==================== 更新 ====================

    @Test
    @DisplayName("更新：分类不存在时拒绝")
    void update_missing_rejected() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        UpdateDiningCategoryFrom from = new UpdateDiningCategoryFrom();
        from.setId(404L);
        from.setName("新名");
        expectBusinessException("分类不存在", () -> service.updateCategory(from));
        verify(categoryMapper, never()).updateById(any(DiningCategory.class));
    }

    @Test
    @DisplayName("更新：改名与其他分类重名时拒绝（排除自身），不触库")
    void update_nameConflictsWithOther_rejected() {
        when(categoryMapper.selectById(1L)).thenReturn(record(1L, "凉菜", 1));
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(record(2L, "热菜", 2));

        UpdateDiningCategoryFrom from = new UpdateDiningCategoryFrom();
        from.setId(1L);
        from.setName("热菜");
        expectBusinessException("分类名称已存在", () -> service.updateCategory(from));
        verify(categoryMapper, never()).updateById(any(DiningCategory.class));
    }

    @Test
    @DisplayName("更新：仅覆盖传入字段；name/sort 为 null 不修改")
    void update_partialFields_appliedAndNullKeeps() {
        when(categoryMapper.selectById(1L)).thenReturn(record(1L, "凉菜", 5));
        when(categoryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(categoryMapper.updateById(any(DiningCategory.class))).thenReturn(1);

        UpdateDiningCategoryFrom from = new UpdateDiningCategoryFrom();
        from.setId(1L);
        from.setName("凉拌菜");
        service.updateCategory(from);

        ArgumentCaptor<DiningCategory> captor = ArgumentCaptor.forClass(DiningCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        DiningCategory updated = captor.getValue();
        assertEquals("凉拌菜", updated.getName(), "传入的 name 必须更新");
        assertEquals(5, updated.getSort(), "未传入的 sort 不得被修改");
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除：逗号分隔 id 逐个物理删除（无菜谱引用时放行）")
    void deleteCategories_splitsIdsAndDeletes() {
        when(recipeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(categoryMapper.deleteById(anyLong())).thenReturn(1);

        service.deleteCategories("1,2,3");

        verify(categoryMapper).deleteById(1L);
        verify(categoryMapper).deleteById(2L);
        verify(categoryMapper).deleteById(3L);
    }

    @Test
    @DisplayName("删除：分类被菜谱引用时拒绝，给出明确业务提示，不触库")
    void deleteCategories_referencedByRecipe_rejected() {
        when(recipeMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(categoryMapper.selectById(1L)).thenReturn(record(1L, "家常菜", 1));

        expectBusinessException("分类「家常菜」已被 3 个菜谱引用，无法删除", () -> service.deleteCategories("1"));

        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：批量中任一 id 被引用即整体拒绝（首个冲突 id 报错），已检查未冲突的也不删除")
    void deleteCategories_anyReferencedInBatch_rejectsAll() {
        // id=1 无引用，id=2 被 1 个菜谱引用
        when(recipeMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 1L);
        when(categoryMapper.selectById(2L)).thenReturn(record(2L, "汤羹", 2));

        expectBusinessException("分类「汤羹」已被 1 个菜谱引用，无法删除", () -> service.deleteCategories("1,2"));

        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：畸形分段抛业务提示（parseId 防御），不抛 NumberFormatException")
    void deleteCategories_malformedId_parseIdDefense() {
        expectBusinessException("ids 必须为逗号分隔的数字", () -> service.deleteCategories("1,abc"));
    }

    // ==================== 管理端分页 ====================

    @Test
    @DisplayName("管理端分页：name 模糊筛选生效，按 sort/id 升序")
    void adminPage_nameFilterAndSortOrder() {
        Page<DiningCategory> result = new Page<>(1, 10, 0);
        result.setRecords(List.of());
        doReturn(result).when(categoryMapper).selectPage(any(), any());

        SearchDiningCategoryFrom from = new SearchDiningCategoryFrom();
        from.setName("汤");
        service.adminPage(from);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DiningCategory>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(categoryMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.values().stream().anyMatch(v -> v instanceof String s && s.contains("汤")),
                "name 模糊筛选必须进入查询条件");

        assertOrderBySegment(captor.getValue(), "sort", "id");
    }

    @Test
    @DisplayName("管理端分页：无筛选条件时也按 sort/id 升序")
    void adminPage_noFilter_stillSorted() {
        Page<DiningCategory> result = new Page<>(1, 10, 0);
        result.setRecords(List.of());
        doReturn(result).when(categoryMapper).selectPage(any(), any());

        service.adminPage(new SearchDiningCategoryFrom());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DiningCategory>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(categoryMapper).selectPage(any(), captor.capture());
        assertOrderBySegment(captor.getValue(), "sort", "id");
    }

    @Test
    @DisplayName("管理端分页：Vo 映射含 id/name/sort")
    void adminPage_mapsVoFields() {
        DiningCategory category = record(1L, "家常菜", 3);
        Page<DiningCategory> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(category));
        doReturn(result).when(categoryMapper).selectPage(any(), any());

        Page<DiningCategoryVo> page = service.adminPage(new SearchDiningCategoryFrom());

        assertNotNull(page.getRecords());
        DiningCategoryVo vo = page.getRecords().get(0);
        assertEquals(1L, vo.getId());
        assertEquals("家常菜", vo.getName());
        assertEquals(3, vo.getSort());
    }


    /** 断言抛出业务异常且消息匹配（CustomExceptions 经全局异常处理转 Result 信封的业务提示） */
    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }

    /**
     * 物化 ORDER BY 段校验：列名经 TableInfo 解析后按传入顺序出现，且为升序。
     * 不依赖 MP 内部 OrderItem API（与 WorkbenchEntryServiceImplTest 同款辅助方法）。
     */
    private void assertOrderBySegment(LambdaQueryWrapper<DiningCategory> wrapper, String firstColumn, String secondColumn) {
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
