package com.smartwash.dining.controller;

import com.smartwash.service.FileStorageService;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 吃啥用户设置/收藏/搜索扩展集成测试（test profile：H2 + mock Redis/FileStorageService，
 * 对齐 WebDiningRecipeControllerIntegrationTest 先例）。覆盖契约 §2.1/§2.3/§2.4：
 * 1. 权限：/web/auth/dining/** 匿名 401、admin 角色 403；
 * 2. 饮食偏好：GET 默认语义、PUT upsert、null 恢复默认（DB 行核对）；
 * 3. 收藏：POST 幂等、GET /favorites 分页（与 /{id} 路由不冲突）、DELETE 幂等；
 * 4. favorited 批量填充（列表）与 detail allergens 聚合；
 * 5. ingredient 反查搜索：命中/组合条件/无命中。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("吃啥用户设置/收藏/搜索扩展集成测试")
class WebDiningUserControllerIntegrationTest {

    private static final String PHONE = "13900139009";
    private static final String ADMIN_NAME = "dining_pref_admin";
    private static final String RECIPES = "/web/auth/dining/recipes";
    private static final String PREFERENCE = "/web/auth/dining/dietary-preference";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private FileStorageService fileStorageService;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private ValueOperations<String, String> valueOperations;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        cleanAll();
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (9301, 1, ?, 'pwd')", PHONE);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9301, ?, 'pwd', 1)", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO dining_category(id, name, sort) VALUES (11, '家常菜', 1)");
        // 配料：鸡蛋=EGG、豆腐=SOY、西红柿=无
        jdbcTemplate.update("INSERT INTO dining_ingredient(id, name, category, allergen) VALUES (21, '鸡蛋', 'MAIN', 'EGG')");
        jdbcTemplate.update("INSERT INTO dining_ingredient(id, name, category, allergen) VALUES (22, '豆腐', 'AUX', 'SOY')");
        jdbcTemplate.update("INSERT INTO dining_ingredient(id, name, category, allergen) VALUES (23, '西红柿', 'AUX', NULL)");
        // 菜谱：201 番茄炒蛋（鸡蛋+西红柿），202 凉拌黄瓜（豆腐），203 下架
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, ingredients, steps, source, status) "
                + "VALUES (201, 11, '番茄炒蛋', '[\"鸡蛋\",\"西红柿\"]', '热锅下油', 'ADMIN', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, source, status) "
                + "VALUES (202, 11, '凉拌黄瓜', 'ADMIN', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, source, status) "
                + "VALUES (203, 11, '下架菜谱', 'ADMIN', 0)");
        jdbcTemplate.update("INSERT INTO dining_recipe_ingredient(recipe_id, ingredient_id) VALUES (201, 21)");
        jdbcTemplate.update("INSERT INTO dining_recipe_ingredient(recipe_id, ingredient_id) VALUES (201, 23)");
        jdbcTemplate.update("INSERT INTO dining_recipe_ingredient(recipe_id, ingredient_id) VALUES (202, 22)");

        userToken = jwtUtil.generatorToken("user-" + PHONE);
        adminToken = jwtUtil.generatorToken("admin-" + ADMIN_NAME);
    }

    @AfterEach
    void cleanUp() {
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM dining_recipe_favorite");
        jdbcTemplate.update("DELETE FROM user_dietary_preference");
        jdbcTemplate.update("DELETE FROM dining_recipe_ingredient");
        jdbcTemplate.update("DELETE FROM dining_ingredient");
        jdbcTemplate.update("DELETE FROM dining_recipe");
        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
    }

    // ==================== 权限 ====================

    @Test
    @DisplayName("匿名访问偏好/收藏/搜索接口：401")
    void anonymous_401() throws Exception {
        mockMvc.perform(get(PREFERENCE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PREFERENCE).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(RECIPES + "/201/favorite")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(RECIPES + "/favorites")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(RECIPES + "/all").param("ingredient", "豆腐"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("admin 角色访问用户端接口：403")
    void adminRole_403() throws Exception {
        mockMvc.perform(get(PREFERENCE).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RECIPES + "/201/favorite")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(RECIPES + "/favorites")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // ==================== 饮食偏好 ====================

    @Test
    @DisplayName("饮食偏好：GET 默认语义 → PUT 保存 → GET 回读 → PUT null 恢复默认")
    void dietaryPreference_defaultPutReset() throws Exception {
        mockMvc.perform(get(PREFERENCE).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.allergens.length()").value(0))
                .andExpect(jsonPath("$.data.calorieTarget").value(nullValue()))
                .andExpect(jsonPath("$.data.sodiumTarget").value(nullValue()));

        mockMvc.perform(put(PREFERENCE).header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allergens\":[\"NUT\",\"SEAFOOD\"],\"calorieTarget\":2200,\"sodiumTarget\":1800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String allergens = jdbcTemplate.queryForObject(
                "SELECT allergens FROM user_dietary_preference WHERE user_id = 9301", String.class);
        org.junit.jupiter.api.Assertions.assertEquals("[\"NUT\",\"SEAFOOD\"]", allergens);

        mockMvc.perform(get(PREFERENCE).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allergens[0]").value("NUT"))
                .andExpect(jsonPath("$.data.allergens[1]").value("SEAFOOD"))
                .andExpect(jsonPath("$.data.calorieTarget").value(2200))
                .andExpect(jsonPath("$.data.sodiumTarget").value(1800));

        // null 恢复默认：整行覆盖为 NULL
        mockMvc.perform(put(PREFERENCE).header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(PREFERENCE).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allergens.length()").value(0))
                .andExpect(jsonPath("$.data.calorieTarget").value(nullValue()))
                .andExpect(jsonPath("$.data.sodiumTarget").value(nullValue()));
    }

    // ==================== 收藏 ====================

    @Test
    @DisplayName("收藏：POST 幂等（重复收藏 data=true 且仅一行）、favorites 分页、DELETE 幂等")
    void favorite_idempotentListUnfavorite() throws Exception {
        mockMvc.perform(post(RECIPES + "/201/favorite").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(post(RECIPES + "/201/favorite").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_recipe_favorite WHERE user_id = 9301 AND recipe_id = 201", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count, "重复收藏必须幂等（uk_user_recipe）");

        mockMvc.perform(get(RECIPES + "/favorites").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(201))
                .andExpect(jsonPath("$.data.records[0].favorited").value(true));

        mockMvc.perform(delete(RECIPES + "/201/favorite").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
        mockMvc.perform(delete(RECIPES + "/201/favorite").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
        mockMvc.perform(get(RECIPES + "/favorites").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("收藏：菜谱不存在时业务失败，不写收藏表")
    void favorite_recipeMissing_rejected() throws Exception {
        mockMvc.perform(post(RECIPES + "/99999/favorite").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜谱不存在"));
    }

    // ==================== favorited / allergens ====================

    @Test
    @DisplayName("列表 favorited 批量填充 + 详情 allergens 聚合（当前用户）")
    void listFavoritedAndDetailAllergens() throws Exception {
        mockMvc.perform(post(RECIPES + "/201/favorite").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(RECIPES + "/all").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id==201)].favorited").value(true))
                .andExpect(jsonPath("$.data.records[?(@.id==202)].favorited").value(false));

        mockMvc.perform(get(RECIPES + "/{id}", 201).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.allergens.length()").value(1))
                .andExpect(jsonPath("$.data.allergens[0]").value("EGG"))
                .andExpect(jsonPath("$.data.favorited").value(true));
    }

    // ==================== 食材反查搜索 ====================

    @Test
    @DisplayName("搜索：ingredient 反查命中 / 与分类组合 AND / 无命中空结果")
    void search_byIngredient() throws Exception {
        mockMvc.perform(get(RECIPES + "/all").param("ingredient", "豆腐")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("凉拌黄瓜"));

        // 与 categoryId 组合（AND）：202 挂分类 11，仍命中
        mockMvc.perform(get(RECIPES + "/all").param("ingredient", "豆腐").param("categoryId", "11")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 与 keyword 组合（AND）：豆腐的菜名不含「番茄」，应为 0
        mockMvc.perform(get(RECIPES + "/all").param("ingredient", "豆腐").param("keyword", "番茄")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 无命中：空结果且不报 SQL 错误
        mockMvc.perform(get(RECIPES + "/all").param("ingredient", "不存在的食材")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
