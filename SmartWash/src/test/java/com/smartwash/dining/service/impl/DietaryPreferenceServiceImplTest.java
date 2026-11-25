package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.dining.entity.DietaryPreference;
import com.smartwash.dining.from.DietaryPreferenceFrom;
import com.smartwash.dining.mapper.DietaryPreferenceMapper;
import com.smartwash.dining.vo.DietaryPreferenceVo;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户饮食偏好服务测试（契约 §2.1，纯 Mockito 先例同 DiningIngredientServiceImplTest）。
 * 覆盖：GET 默认值语义（无记录=null 字段，端上兜底）、GET 映射与非法 JSON 兜底、
 * PUT upsert 新建/整行覆盖（null=恢复默认）、忌口去重保序。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DietaryPreferenceServiceImpl 饮食偏好读写测试")
class DietaryPreferenceServiceImplTest {

    private static final Long USER_ID = 9102L;

    @Mock
    private DietaryPreferenceMapper dietaryPreferenceMapper;

    private DietaryPreferenceServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DietaryPreference.class);
    }

    @BeforeEach
    void setUp() {
        service = new DietaryPreferenceServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", dietaryPreferenceMapper);
    }

    private DietaryPreferenceFrom from(List<String> allergens, Integer calorie, Integer sodium) {
        DietaryPreferenceFrom from = new DietaryPreferenceFrom();
        from.setAllergens(allergens);
        from.setCalorieTarget(calorie);
        from.setSodiumTarget(sodium);
        return from;
    }

    // ==================== GET ====================

    @Test
    @DisplayName("GET 无记录：返回默认语义 VO（allergens 空列表、目标 null，端上兜底 2000）")
    void get_noRecord_returnsDefaultSemantics() {
        when(dietaryPreferenceMapper.selectById(USER_ID)).thenReturn(null);

        DietaryPreferenceVo vo = service.get(USER_ID);

        assertNotNull(vo);
        assertTrue(vo.getAllergens().isEmpty(), "无记录时忌口为空列表");
        assertNull(vo.getCalorieTarget(), "null=默认值语义，不得在服务端写死 2000");
        assertNull(vo.getSodiumTarget());
    }

    @Test
    @DisplayName("GET 有记录：忌口 JSON 反序列化 + 目标映射")
    void get_existing_mapsFields() {
        DietaryPreference entity = new DietaryPreference()
                .setUserId(USER_ID)
                .setAllergens("[\"NUT\",\"SEAFOOD\"]")
                .setCalorieTarget(2200)
                .setSodiumTarget(1800);
        when(dietaryPreferenceMapper.selectById(USER_ID)).thenReturn(entity);

        DietaryPreferenceVo vo = service.get(USER_ID);

        assertEquals(List.of("NUT", "SEAFOOD"), vo.getAllergens());
        assertEquals(2200, vo.getCalorieTarget());
        assertEquals(1800, vo.getSodiumTarget());
    }

    @Test
    @DisplayName("GET 非法 JSON：按空忌口兜底返回，不抛异常阻断设置页")
    void get_invalidJson_returnsEmptyAllergens() {
        DietaryPreference entity = new DietaryPreference()
                .setUserId(USER_ID).setAllergens("not-a-json");
        when(dietaryPreferenceMapper.selectById(USER_ID)).thenReturn(entity);

        DietaryPreferenceVo vo = service.get(USER_ID);

        assertTrue(vo.getAllergens().isEmpty());
    }

    // ==================== PUT（upsert） ====================

    @Test
    @DisplayName("PUT 无记录：新建落库，user_id 主键 + 忌口 JSON + 目标")
    void save_newRecord_insertsWithUserIdAndJson() {
        when(dietaryPreferenceMapper.selectById(USER_ID)).thenReturn(null);
        when(dietaryPreferenceMapper.insert(any(DietaryPreference.class))).thenReturn(1);

        service.save(USER_ID, from(List.of("NUT", "SEAFOOD"), 2200, 1800));

        ArgumentCaptor<DietaryPreference> captor = ArgumentCaptor.forClass(DietaryPreference.class);
        verify(dietaryPreferenceMapper).insert(captor.capture());
        DietaryPreference saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId(), "一人一行，主键必须取当前用户 ID");
        assertEquals("[\"NUT\",\"SEAFOOD\"]", saved.getAllergens());
        assertEquals(2200, saved.getCalorieTarget());
        assertEquals(1800, saved.getSodiumTarget());
    }

    @Test
    @DisplayName("PUT 有记录：整行覆盖，null 目标/空忌口恢复默认（NULL 写回库）")
    void save_existing_updatesFullRowWithNulls() {
        when(dietaryPreferenceMapper.selectById(USER_ID))
                .thenReturn(new DietaryPreference().setUserId(USER_ID)
                        .setAllergens("[\"NUT\"]").setCalorieTarget(2200).setSodiumTarget(1800));
        when(dietaryPreferenceMapper.updateById(any(DietaryPreference.class))).thenReturn(1);

        service.save(USER_ID, from(null, null, null));

        ArgumentCaptor<DietaryPreference> captor = ArgumentCaptor.forClass(DietaryPreference.class);
        verify(dietaryPreferenceMapper).updateById(captor.capture());
        DietaryPreference updated = captor.getValue();
        assertEquals(USER_ID, updated.getUserId());
        assertNull(updated.getCalorieTarget(), "null=恢复默认，必须写回 NULL");
        assertNull(updated.getSodiumTarget(), "null=恢复默认，必须写回 NULL");
        assertNull(updated.getAllergens(), "空忌口恢复为 NULL（无忌口）");
    }

    // ==================== 批量取成员忌口（点单详情聚合） ====================

    @Test
    @DisplayName("批量取成员忌口：按 userId 映射，无记录用户不入表")
    void getAllergensByUserIds_mapsByUser() {
        when(dietaryPreferenceMapper.selectList(any())).thenReturn(List.of(
                new DietaryPreference().setUserId(1L).setAllergens("[\"SEAFOOD\"]"),
                new DietaryPreference().setUserId(2L).setAllergens("[\"NUT\",\"SPICY\"]")));

        Map<Long, List<String>> result = service.getAllergensByUserIds(List.of(1L, 2L, 3L));

        assertEquals(List.of("SEAFOOD"), result.get(1L));
        assertEquals(List.of("NUT", "SPICY"), result.get(2L));
        assertNull(result.get(3L), "无记录用户不入表，端上按空忌口兜底");
    }

    @Test
    @DisplayName("批量取成员忌口：空入参直接返回空 Map，不打库")
    void getAllergensByUserIds_emptyInput() {
        assertTrue(service.getAllergensByUserIds(List.of()).isEmpty());
        assertTrue(service.getAllergensByUserIds(null).isEmpty());
    }

    @Test
    @DisplayName("PUT 忌口去重保序：重复标签只落一次")
    void save_dedupesAllergensKeepingOrder() {
        when(dietaryPreferenceMapper.selectById(USER_ID)).thenReturn(null);
        when(dietaryPreferenceMapper.insert(any(DietaryPreference.class))).thenReturn(1);

        service.save(USER_ID, from(List.of("NUT", "SEAFOOD", "NUT"), null, null));

        ArgumentCaptor<DietaryPreference> captor = ArgumentCaptor.forClass(DietaryPreference.class);
        verify(dietaryPreferenceMapper).insert(captor.capture());
        assertEquals("[\"NUT\",\"SEAFOOD\"]", captor.getValue().getAllergens());
    }
}
