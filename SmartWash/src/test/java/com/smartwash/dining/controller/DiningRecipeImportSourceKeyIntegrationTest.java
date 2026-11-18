package com.smartwash.dining.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwash.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 菜谱导入 source_key 幂等判重 + 食材链路集成测试（test profile：H2 + mock Redis，
 * 对齐 DiningRecipeImportControllerIntegrationTest 先例；走真实服务层 + H2，行为即契约）。
 *
 * <p>覆盖三条主链路：
 * 1. 新增行（唯一标识 + 食材明细 + 营养 6 列）→ dining_recipe / dining_recipe_ingredient
 *    （amount_value/amount_unit 解析）/ dining_ingredient（按名 upsert + allergen）全链落库；
 * 2. 同 source_key 重复导入 → 幂等更新：菜不重复插（id 稳定）、内容刷新、关联重建；
 * 3. 旧格式行（无 source_key）按（菜名,分类）兜底判重 → 更新且不清空既有标识。
 * 另覆盖：同批重复 source_key 全批回滚、预览新增/更新标注、管理端 add/update 的 sourceKey 幂等保护。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("菜谱导入 source_key 判重 + 食材链路集成测试")
class DiningRecipeImportSourceKeyIntegrationTest {

    private static final String ADMIN_NAME = "dining_source_key_admin";
    private static final String BASE = "/admin/dining/recipes";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /** 替换真实 Redis：惰性占位，链路全部走 H2 */
    @MockBean
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private ValueOperations<String, String> valueOperations;

    private String adminToken;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        jdbcTemplate.update("DELETE FROM dining_recipe_ingredient");
        jdbcTemplate.update("DELETE FROM dining_ingredient");
        jdbcTemplate.update("DELETE FROM dining_recipe");
        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9105, ?, 'pwd', 1)", ADMIN_NAME);
        adminToken = jwtUtil.generatorToken("admin-" + ADMIN_NAME);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM dining_recipe_ingredient");
        jdbcTemplate.update("DELETE FROM dining_ingredient");
        jdbcTemplate.update("DELETE FROM dining_recipe");
        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
    }

    // ==================== 1. 新增行：食材明细 + 营养 + 唯一标识 全链落库 ====================

    @Test
    @DisplayName("新增行带唯一标识/食材明细/营养 → 菜谱+关联表（数量解析）+配料库（allergen）全链落库")
    void confirm_newRow_ingredientChainPersisted() throws Exception {
        String body = """
                {"rows":[{"rowNo":2,"categoryName":"集成分类","name":"番茄炒蛋","sourceKey":"hoc_it_001",
                          "ingredients":["西红柿","鸡蛋","盐"],
                          "ingredientDetails":[{"name":"鸡蛋","amountValue":575,"amountUnit":"g"},
                                               {"name":"西红柿","amountValue":1.8,"amountUnit":"kg"},
                                               {"name":"盐","amountValue":15,"amountUnit":"g"}],
                          "steps":"1. 打蛋\\n2. 炒",
                          "caloriesPer100g":86.3,"caloriesTotal":518,"proteinG":7.9,
                          "fatG":5.1,"carbsG":3.2,"sodiumMg":480.6}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.rolledBack").value(false))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.rows[0].isNew").value(true))
                .andExpect(jsonPath("$.data.rows[0].existsKind").value("CREATE"))
                .andExpect(jsonPath("$.data.rows[0].recipeId").isNumber());

        // 菜谱主表：唯一标识 + 营养 6 列 + 默认上架 + ADMIN 来源
        Map<String, Object> recipe = queryOne("SELECT id, source_key, calories_per_100g, calories_total, protein_g, fat_g, carbs_g, sodium_mg, status, source"
                + " FROM dining_recipe WHERE source_key = 'hoc_it_001'");
        assertNotNull(recipe, "source_key 必须落库且可反查");
        assertEquals(0, new BigDecimal("86.3").compareTo((BigDecimal) recipe.get("calories_per_100g")));
        assertEquals(0, new BigDecimal("518").compareTo((BigDecimal) recipe.get("calories_total")));
        assertEquals(0, new BigDecimal("7.9").compareTo((BigDecimal) recipe.get("protein_g")));
        assertEquals(0, new BigDecimal("5.1").compareTo((BigDecimal) recipe.get("fat_g")));
        assertEquals(0, new BigDecimal("3.2").compareTo((BigDecimal) recipe.get("carbs_g")));
        assertEquals(0, new BigDecimal("480.6").compareTo((BigDecimal) recipe.get("sodium_mg")));
        assertEquals(1, ((Number) recipe.get("status")).intValue(), "导入菜谱默认上架");
        assertEquals("ADMIN", recipe.get("source"));
        long recipeId = ((Number) recipe.get("id")).longValue();

        // 关联表：明细数量解析落库（买菜清单聚合主数据源），按名去重后 3 行（顺序 = 名列表首现序：西红柿、鸡蛋、盐）
        List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                "SELECT amount_value, amount_unit FROM dining_recipe_ingredient WHERE recipe_id = ? ORDER BY id", recipeId);
        assertEquals(3, relations.size(), "食材名列表与明细并存时按名去重合并");
        assertEquals(0, new BigDecimal("1.8").compareTo((BigDecimal) relations.get(0).get("amount_value")));
        assertEquals("kg", relations.get(0).get("amount_unit"), "单位原样保留（kg→g 归一在聚合侧做）");
        assertEquals(0, new BigDecimal("575").compareTo((BigDecimal) relations.get(1).get("amount_value")));
        assertEquals("g", relations.get(1).get("amount_unit"));
        assertEquals(0, new BigDecimal("15").compareTo((BigDecimal) relations.get(2).get("amount_value")));

        // 配料库：按名 upsert 自动注册，过敏原按名启发（鸡蛋→EGG）
        assertEquals(3, countRows("dining_ingredient"), "三个配料全部自动注册");
        Map<String, Object> egg = queryOne("SELECT category, allergen FROM dining_ingredient WHERE name = '鸡蛋'");
        assertEquals("EGG", egg.get("allergen"), "过敏原按名启发预判必须落库");
        assertEquals("SEASONING", queryOne("SELECT category FROM dining_ingredient WHERE name = '盐'").get("category"));
    }

    // ==================== 2. 同 source_key 重复导入：幂等更新 ====================

    @Test
    @DisplayName("同 source_key 重复导入 → 幂等更新：不重复插菜（id 稳定）、营养/食材刷新、关联重建不残留")
    void confirm_sameSourceKey_idempotentUpdate() throws Exception {
        String firstBody = """
                {"rows":[{"rowNo":2,"categoryName":"集成分类","name":"旧名菜","sourceKey":"hoc_it_002",
                          "ingredients":["鸡蛋","盐"],
                          "ingredientDetails":[{"name":"鸡蛋","amountValue":100,"amountUnit":"g"}],
                          "steps":"旧步骤","caloriesTotal":100}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1));
        Long firstId = jdbcTemplate.queryForObject(
                "SELECT id FROM dining_recipe WHERE source_key = 'hoc_it_002'", Long.class);
        assertNotNull(firstId);

        String secondBody = """
                {"rows":[{"rowNo":2,"categoryName":"集成分类","name":"新名菜","sourceKey":"hoc_it_002",
                          "ingredients":["鸡蛋"],
                          "ingredientDetails":[{"name":"鸡蛋","amountValue":575,"amountUnit":"g"}],
                          "steps":"新步骤","caloriesTotal":518,"proteinG":7.9}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].isNew").value(false))
                .andExpect(jsonPath("$.data.rows[0].existsKind").value("UPDATE"))
                .andExpect(jsonPath("$.data.rows[0].recipeId").value(firstId));

        // 幂等：仍是 1 条菜、id 稳定、内容刷新、status/created_at 不被静默改动
        assertEquals(1, countRows("dining_recipe"), "重复导入不得重复插菜");
        Map<String, Object> recipe = queryOne("SELECT id, name, steps, calories_total, protein_g, status, created_at, updated_at"
                + " FROM dining_recipe WHERE source_key = 'hoc_it_002'");
        assertEquals(firstId, ((Number) recipe.get("id")).longValue(), "更新必须保留原 id");
        assertEquals("新名菜", recipe.get("name"));
        assertEquals("新步骤", recipe.get("steps"));
        assertEquals(0, new BigDecimal("518").compareTo((BigDecimal) recipe.get("calories_total")));
        assertEquals(0, new BigDecimal("7.9").compareTo((BigDecimal) recipe.get("protein_g")));
        assertEquals(1, ((Number) recipe.get("status")).intValue(), "更新不静默改上下架");
        // 关联重建：旧关联（鸡蛋100g+盐）清空，新关联只含鸡蛋575g
        List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                "SELECT amount_value, amount_unit FROM dining_recipe_ingredient WHERE recipe_id = ?", firstId);
        assertEquals(1, relations.size(), "旧关联必须被重建清空（盐不再残留）");
        assertEquals(0, new BigDecimal("575").compareTo((BigDecimal) relations.get(0).get("amount_value")));
    }

    // ==================== 3. 旧格式行（无 source_key）：（菜名,分类）兜底判重 ====================

    @Test
    @DisplayName("旧格式行无唯一标识 → 按（菜名,分类）兜底命中更新：菜不重复、既有标识不被清空")
    void confirm_legacyRowWithoutKey_fallbackUpdate() throws Exception {
        String firstBody = """
                {"rows":[{"rowNo":2,"categoryName":"集成分类","name":"番茄蛋汤","sourceKey":"hoc_it_003",
                          "ingredients":["西红柿"],"steps":"v1 步骤"}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1));

        // 第二次导入同一道菜但不带唯一标识（模拟旧模板 CSV 重导）：按（菜名,分类）兜底命中更新
        String secondBody = """
                {"rows":[{"rowNo":2,"categoryName":"集成分类","name":"番茄蛋汤",
                          "ingredients":["西红柿","鸡蛋"],"steps":"v2 步骤"}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.createdCount").value(0));

        assertEquals(1, countRows("dining_recipe"), "兜底命中必须更新而非重复插入");
        Map<String, Object> recipe = queryOne("SELECT steps, source_key FROM dining_recipe");
        assertEquals("v2 步骤", recipe.get("steps"));
        assertEquals("hoc_it_003", recipe.get("source_key"), "无标识行更新时必须保留既有标识（不清空）");
    }

    @Test
    @DisplayName("旧格式行无唯一标识且库内无同名菜 → 兜底未命中按新增（存量 340 道首次重导前的行为）")
    void confirm_legacyRowWithoutKey_noMatch_creates() throws Exception {
        String body = """
                {"rows":[{"rowNo":2,"categoryName":"集成分类","name":"全新旧格式菜",
                          "ingredients":["盐"],"steps":"煮"}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].isNew").value(true));
        assertEquals(1, countRows("dining_recipe"));
        assertNull(jdbcTemplate.queryForObject("SELECT source_key FROM dining_recipe", String.class),
                "无标识新增行 source_key 落 NULL");
    }

    // ==================== 4. 同批重复 source_key：全批回滚 ====================

    @Test
    @DisplayName("同批两行携带同一唯一标识 → 行级报错全批回滚，库内零写入")
    void confirm_batchDuplicateSourceKey_rollsBack() throws Exception {
        String body = """
                {"rows":[{"rowNo":2,"name":"菜A","sourceKey":"hoc_dup","ingredients":["盐"],"steps":"煮"},
                         {"rowNo":3,"name":"菜B","sourceKey":"hoc_dup","ingredients":["盐"],"steps":"煮"}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rolledBack").value(true))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.rows[0].error", org.hamcrest.Matchers.containsString("hoc_dup")))
                .andExpect(jsonPath("$.data.rows[1].error", org.hamcrest.Matchers.containsString("hoc_dup")));
        assertEquals(0, countRows("dining_recipe"));
    }

    // ==================== 5. 预览：新增/更新标注与计数 ====================

    @Test
    @DisplayName("预览 CSV 带「唯一标识」列 → isNew/existsKind 逐行标注 + newCount/updateCount 汇总")
    void preview_marksNewAndUpdate() throws Exception {
        // 预置一道已带标识的菜
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"rowNo":2,"categoryName":"集成分类","name":"已存在菜",
                                          "sourceKey":"hoc_pv_001","ingredients":["盐"],"steps":"煮"}]}"""))
                .andExpect(status().isOk());

        String csv = "分类名,菜名,食材名列表,步骤,封面链接,唯一标识\n"
                + "集成分类,已存在菜,盐,煮,,hoc_pv_001\n"
                + "集成分类,全新菜,盐,煮,,hoc_pv_002\n"
                + "幽灵分类,兜底菜,盐,煮,,hoc_pv_003\n";
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart(BASE + "/import").file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "recipes.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.newCount").value(2))
                .andExpect(jsonPath("$.data.updateCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].isNew").value(false))
                .andExpect(jsonPath("$.data.rows[0].existsKind").value("UPDATE"))
                .andExpect(jsonPath("$.data.rows[1].isNew").value(true))
                .andExpect(jsonPath("$.data.rows[1].existsKind").value("CREATE"))
                .andExpect(jsonPath("$.data.rows[2].isNew").value(true))
                .andExpect(jsonPath("$.data.rows[2].warnings[0]",
                        org.hamcrest.Matchers.containsString("幽灵分类")))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("hoc_pv_001"),
                "唯一标识原文必须回显给前端");
    }

    // ==================== 6. 管理端 add/update 的 sourceKey 幂等保护 ====================

    @Test
    @DisplayName("add 带 sourceKey：新标识可建；同标识二次新增报冲突；update 挂到其他菜谱的 key 报冲突")
    void addAndUpdate_sourceKeyConflict() throws Exception {
        // 新增 A（带标识）
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"菜A\",\"sourceKey\":\"hoc_add_001\",\"ingredients\":[\"盐\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        long idA = jdbcTemplate.queryForObject(
                "SELECT id FROM dining_recipe WHERE source_key = 'hoc_add_001'", Long.class);

        // VO 回显：管理端列表带出 sourceKey
        mockMvc.perform(get(BASE + "/all").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].sourceKey").value("hoc_add_001"));

        // 同标识二次新增：撞 uk_source_key 前先业务化报冲突
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"菜B\",\"sourceKey\":\"hoc_add_001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("唯一标识")));

        // 新增 B（无标识）→ 更新 B 挂 A 的标识：报冲突
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"菜B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        long idB = jdbcTemplate.queryForObject(
                "SELECT id FROM dining_recipe WHERE name = '菜B'", Long.class);
        mockMvc.perform(post(BASE + "/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + idB + ",\"sourceKey\":\"hoc_add_001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("已被菜谱")));

        // 更新 B 挂自己的新标识：放行并落库
        mockMvc.perform(post(BASE + "/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + idB + ",\"sourceKey\":\"hoc_add_002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("hoc_add_002", jdbcTemplate.queryForObject(
                "SELECT source_key FROM dining_recipe WHERE id = " + idB, String.class));
        assertEquals(idA, ((Number) queryOne(
                "SELECT id FROM dining_recipe WHERE source_key = 'hoc_add_001'").get("id")).longValue(),
                "冲突保护后 A 的标识归属不变");
    }

    // ==================== 辅助 ====================

    /** 单行结果取首列断言（H2 直查，专供落库状态断言） */
    private Map<String, Object> queryOne(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
