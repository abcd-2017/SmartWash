package com.smartwash.dining.controller;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 菜谱分类管理端集成测试（test profile：H2 + mock Redis，对齐 ToolboxSecurityIntegrationTest 先例）。
 * 三条防线一次覆盖：
 * 1. 权限：/admin/dining/** 由 SecurityConfig /admin/** 通配保护——匿名 401、user 角色 403、admin 角色放行；
 * 2. 信封：所有响应统一 Result{code,message,data}，业务失败 HTTP 200 + code=201（FAIL）；
 * 3. CRUD 行为：新增重名拒绝、列表 sort 升序、name 模糊筛选、更新生效、不存在拒绝、批量物理删除。
 * 走真实服务层 + H2，不 mock service（行为即契约）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("菜谱分类管理端集成测试（权限/信封/CRUD）")
class DiningCategoryAdminControllerIntegrationTest {

    private static final String PHONE = "13900139000";
    private static final String ADMIN_NAME = "dining_admin";
    private static final String BASE = "/admin/dining/categories";

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

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (9101, 1, ?, 'pwd')", PHONE);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9101, ?, 'pwd', 1)", ADMIN_NAME);

        userToken = jwtUtil.generatorToken("user-" + PHONE);
        adminToken = jwtUtil.generatorToken("admin-" + ADMIN_NAME);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
    }

    // ==================== 权限 ====================

    @Test
    @DisplayName("匿名访问分类列表：401（/admin/** 需认证）")
    void list_anonymous_401() throws Exception {
        mockMvc.perform(get(BASE + "/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("user 角色访问管理端分类接口：403")
    void adminEndpoints_withUserRole_403() throws Exception {
        mockMvc.perform(get(BASE + "/all").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"家常菜\"}"))
                .andExpect(status().isForbidden());
    }

    // ==================== 信封与 CRUD 行为 ====================

    @Test
    @DisplayName("admin 全链路：新增→重名拒绝→列表→筛选→更新→不存在拒绝→删除→删除后为空")
    void adminCrud_fullFlow() throws Exception {
        // 新增：HTTP 200 + code 200 信封
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"家常菜\",\"sort\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 重名新增：业务失败走 HTTP 200 + code 201（FAIL）+ 友好提示
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"家常菜\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("分类名称已存在"));

        // 列表：信封 + 记录映射（name/sort 回读）
        MvcResult listResult = mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        JsonNode records = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records");
        assertEquals(1, records.size());
        assertEquals("家常菜", records.get(0).path("name").asText());
        assertEquals(2, records.get(0).path("sort").asInt());
        long id = records.get(0).path("id").asLong();

        // name 模糊筛选：不命中时 records 为空
        mockMvc.perform(get(BASE + "/all").param("name", "汤羹")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));

        // 更新：改名生效
        mockMvc.perform(post(BASE + "/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"name\":\"家常小炒\",\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 更新不存在的分类：业务失败提示
        mockMvc.perform(post(BASE + "/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":99999,\"name\":\"幽灵分类\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("分类不存在"));

        // 更新结果回读
        MvcResult afterUpdate = mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterRecords = objectMapper.readTree(afterUpdate.getResponse().getContentAsString())
                .path("data").path("records");
        assertEquals("家常小炒", afterRecords.get(0).path("name").asText());

        // 删除：物理删除后列表为空
        mockMvc.perform(delete(BASE + "/delete/{ids}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("admin 多分类列表按 sort 升序返回（sort 相同按 id 升序）")
    void list_ordersBySortAsc() throws Exception {
        addCategory("汤羹", 2);
        addCategory("凉菜", 1);
        addCategory("热炒", 2);

        MvcResult result = mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("records");
        assertEquals("凉菜", records.get(0).path("name").asText(), "sort=1 的分类必须排最前");
        long first = records.get(1).path("id").asLong();
        long second = records.get(2).path("id").asLong();
        assertTrue(first < second, "sort 相同的分类必须按 id 升序");
    }

    @Test
    @DisplayName("畸形 ids（非数字）返回 HTTP 400 + 统一信封错误提示")
    void delete_malformedIds_400() throws Exception {
        mockMvc.perform(delete(BASE + "/delete/{ids}", "abc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("ids 必须为逗号分隔的数字"));
    }

    @Test
    @DisplayName("新增缺 name（@Valid 校验）返回业务失败信封与字段提示")
    void add_missingName_validationEnvelope() throws Exception {
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                // 全局异常处理按「字段:提示 」拼接（含尾随空格），断言语义部分而非格式
                .andExpect(jsonPath("$.message", containsString("分类名称不能为空")));
    }

    /** 直连服务层灌数据（列表排序测试的前置数据） */
    private void addCategory(String name, int sort) throws Exception {
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sort\":" + sort + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
