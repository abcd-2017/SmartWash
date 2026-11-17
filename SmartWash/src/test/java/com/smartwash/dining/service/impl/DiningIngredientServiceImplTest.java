package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.from.AddDiningIngredientFrom;
import com.smartwash.dining.from.SearchDiningIngredientFrom;
import com.smartwash.dining.from.UpdateDiningIngredientFrom;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.vo.DiningIngredientVo;
import com.smartwash.exception.CustomExceptions;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配料库服务测试（纯 Mockito，对齐 DiningCategoryServiceImplTest 先例）。
 * 覆盖外部行为契约：新增重名 409（先查重 + 唯一索引兜底）、更新分支（不存在/重名 409/字段 null 不覆盖）、
 * 删除引用校验（被菜谱配料关联引用时 409 拒绝并提示引用数量）、管理端分页（keyword/category 筛选 + id 升序）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiningIngredientServiceImpl 配料库增删改查测试")
class DiningIngredientServiceImplTest {

    @Mock
    private DiningIngredientMapper ingredientMapper;

    @Mock
    private DiningRecipeIngredientMapper recipeIngredientMapper;

    private DiningIngredientServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DiningIngredient.class);
        TableInfoHelper.initTableInfo(assistant, DiningRecipeIngredient.class);
    }

    @BeforeEach
    void setUp() {
        service = new DiningIngredientServiceImpl();
        // ServiceImpl 的内置方法走父类 baseMapper 字段，需手动注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", ingredientMapper);
        ReflectionTestUtils.setField(service, "recipeIngredientMapper", recipeIngredientMapper);
    }

    private DiningIngredient ingredient(long id, String name, String category) {
        return new DiningIngredient().setId(id).setName(name).setCategory(category);
    }

    private AddDiningIngredientFrom addFrom(String name, String category) {
        AddDiningIngredientFrom from = new AddDiningIngredientFrom();
        from.setName(name);
        from.setCategory(category);
        return from;
    }

    // ==================== 新增 ====================

    @Test
    @DisplayName("新增：配料名已存在时 409 拒绝，不触库")
    void add_duplicateName_rejected409() {
        when(ingredientMapper.selectOne(any(Wrapper.class))).thenReturn(ingredient(9L, "鸡蛋", "MAIN"));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addIngredient(addFrom("鸡蛋", "MAIN")));
        assertEquals(409, ex.getCode(), "重名必须用 409 业务码（契约 §3.4）");
        assertEquals("配料名称已存在", ex.getMessage());
        verify(ingredientMapper, never()).insert(any(DiningIngredient.class));
    }

    @Test
    @DisplayName("新增：并发窗口下唯一索引兜底裁决，DuplicateKeyException 转 409 业务提示")
    void add_uniqueIndexFallback_rejected409() {
        when(ingredientMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(ingredientMapper.insert(any(DiningIngredient.class)))
                .thenThrow(new DuplicateKeyException("uk_name"));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addIngredient(addFrom("race-name", null)));
        assertEquals(409, ex.getCode());
    }

    @Test
    @DisplayName("新增：字段完整映射落库，category 未传缺省 OTHER")
    void add_success_mapsAllFieldsWithDefaultCategory() {
        when(ingredientMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(ingredientMapper.insert(any(DiningIngredient.class))).thenReturn(1);

        service.addIngredient(addFrom("五花肉", null));

        ArgumentCaptor<DiningIngredient> captor = ArgumentCaptor.forClass(DiningIngredient.class);
        verify(ingredientMapper).insert(captor.capture());
        assertEquals("五花肉", captor.getValue().getName());
        assertEquals(DiningIngredient.CATEGORY_OTHER, captor.getValue().getCategory(), "category 缺省 OTHER");

        service.addIngredient(addFrom("鸡蛋", DiningIngredient.CATEGORY_MAIN));
        ArgumentCaptor<DiningIngredient> explicit = ArgumentCaptor.forClass(DiningIngredient.class);
        verify(ingredientMapper, org.mockito.Mockito.times(2)).insert(explicit.capture());
        assertEquals(DiningIngredient.CATEGORY_MAIN, explicit.getAllValues().get(1).getCategory());
    }

    // ==================== 更新 ====================

    @Test
    @DisplayName("更新：配料不存在时拒绝")
    void update_missing_rejected() {
        when(ingredientMapper.selectById(404L)).thenReturn(null);

        UpdateDiningIngredientFrom from = new UpdateDiningIngredientFrom();
        from.setName("新名");
        expectBusinessException("配料不存在", () -> service.updateIngredient(404L, from));
        verify(ingredientMapper, never()).updateById(any(DiningIngredient.class));
    }

    @Test
    @DisplayName("更新：改名与其他配料重名时 409 拒绝（排除自身），不触库")
    void update_nameConflictsWithOther_rejected409() {
        when(ingredientMapper.selectById(1L)).thenReturn(ingredient(1L, "白糖", "SEASONING"));
        when(ingredientMapper.selectOne(any(Wrapper.class))).thenReturn(ingredient(2L, "冰糖", "SEASONING"));

        UpdateDiningIngredientFrom from = new UpdateDiningIngredientFrom();
        from.setName("冰糖");
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.updateIngredient(1L, from));
        assertEquals(409, ex.getCode());
        verify(ingredientMapper, never()).updateById(any(DiningIngredient.class));
    }

    @Test
    @DisplayName("更新：仅覆盖传入字段；name/category 为 null 不修改")
    void update_partialFields_appliedAndNullKeeps() {
        when(ingredientMapper.selectById(1L)).thenReturn(ingredient(1L, "白糖", "SEASONING"));
        when(ingredientMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(ingredientMapper.updateById(any(DiningIngredient.class))).thenReturn(1);

        UpdateDiningIngredientFrom from = new UpdateDiningIngredientFrom();
        from.setCategory(DiningIngredient.CATEGORY_SEASONING);
        service.updateIngredient(1L, from);

        ArgumentCaptor<DiningIngredient> captor = ArgumentCaptor.forClass(DiningIngredient.class);
        verify(ingredientMapper).updateById(captor.capture());
        assertEquals("白糖", captor.getValue().getName(), "未传入的 name 不得被修改");
        assertEquals(DiningIngredient.CATEGORY_SEASONING, captor.getValue().getCategory());
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除：配料不存在时拒绝")
    void delete_missing_rejected() {
        when(ingredientMapper.selectById(404L)).thenReturn(null);
        expectBusinessException("配料不存在", () -> service.deleteIngredient(404L));
        verify(ingredientMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：被菜谱配料关联引用时 409 拒绝，提示引用数量，不触库")
    void delete_referencedByRecipeIngredient_rejected409() {
        when(ingredientMapper.selectById(1L)).thenReturn(ingredient(1L, "鸡蛋", "MAIN"));
        when(recipeIngredientMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.deleteIngredient(1L));

        assertEquals(409, ex.getCode(), "引用拒绝必须用 409 业务码（契约 §3.4）");
        assertEquals("配料「鸡蛋」已被 3 个菜谱引用，无法删除", ex.getMessage());
        verify(ingredientMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除：无引用时物理删除放行")
    void delete_noReference_removed() {
        when(ingredientMapper.selectById(1L)).thenReturn(ingredient(1L, "干辣椒", "SEASONING"));
        when(recipeIngredientMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(ingredientMapper.deleteById(1L)).thenReturn(1);

        service.deleteIngredient(1L);

        verify(ingredientMapper).deleteById(1L);
    }

    // ==================== 管理端分页 ====================

    @Test
    @DisplayName("管理端分页：keyword 模糊 + category 精确筛选生效，按 id 升序")
    void adminPage_filtersAndSortOrder() {
        Page<DiningIngredient> result = new Page<>(1, 10, 0);
        result.setRecords(List.of());
        doReturn(result).when(ingredientMapper).selectPage(any(), any());

        SearchDiningIngredientFrom from = new SearchDiningIngredientFrom();
        from.setKeyword("豆");
        from.setCategory(DiningIngredient.CATEGORY_AUX);
        service.adminPage(from);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<DiningIngredient>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ingredientMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，条件参数才写入 paramNameValuePairs
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.values().stream().anyMatch(v -> v instanceof String s && s.contains("豆")),
                "keyword 模糊筛选必须进入查询条件");
        assertTrue(params.containsValue(DiningIngredient.CATEGORY_AUX), "category 精确筛选必须进入查询条件");

        String segment = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(segment.contains("ORDER BY") && segment.contains("ASC"), "必须带升序 ORDER BY 段");
    }

    @Test
    @DisplayName("管理端分页：Vo 映射含 id/name/category")
    void adminPage_mapsVoFields() {
        DiningIngredient record = ingredient(1L, "鸡蛋", DiningIngredient.CATEGORY_MAIN);
        Page<DiningIngredient> result = new Page<>(1, 10, 1);
        result.setRecords(List.of(record));
        doReturn(result).when(ingredientMapper).selectPage(any(), any());

        Page<DiningIngredientVo> page = service.adminPage(new SearchDiningIngredientFrom());

        assertNotNull(page.getRecords());
        DiningIngredientVo vo = page.getRecords().get(0);
        assertEquals(1L, vo.getId());
        assertEquals("鸡蛋", vo.getName());
        assertEquals(DiningIngredient.CATEGORY_MAIN, vo.getCategory());
    }

    /** 断言抛出业务异常且消息匹配 */
    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }
}
