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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 菜谱批量导入集成测试（test profile：H2 + mock Redis，对齐 DiningRecipeAdminControllerIntegrationTest
 * 先例）。三条防线一次覆盖：
 * 1. 权限：/admin/dining/recipes/import/** 由 /admin/** 通配保护——匿名 401、user 角色 403、admin 放行；
 * 2. 信封：解析校验类业务失败 HTTP 200 + code=201；成功 code=200；
 * 3. 两阶段行为：模板可下载且能被预览接口解析、预览不落库、确认整批事务（全过逐行写入 / 任一失败全批不落库）。
 * 走真实服务层 + H2，不 mock service（行为即契约）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("菜谱批量导入集成测试（权限/信封/两阶段导入）")
class DiningRecipeImportControllerIntegrationTest {

    private static final String PHONE = "13900139002";
    private static final String ADMIN_NAME = "dining_import_admin";
    private static final String BASE = "/admin/dining/recipes";
    private static final String CATEGORY_BASE = "/admin/dining/categories";

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

        jdbcTemplate.update("DELETE FROM dining_recipe");
        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (9103, 1, ?, 'pwd')", PHONE);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9103, ?, 'pwd', 1)", ADMIN_NAME);

        userToken = jwtUtil.generatorToken("user-" + PHONE);
        adminToken = jwtUtil.generatorToken("admin-" + ADMIN_NAME);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM dining_recipe");
        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
    }

    // ==================== 权限 ====================

    @Test
    @DisplayName("匿名访问导入接口：401（预览上传与确认写入均受 /admin/** 保护）")
    void importEndpoints_anonymous_401() throws Exception {
        mockMvc.perform(multipart(BASE + "/import").file(csvFile(HEADER_ROW + "\n,菜,盐,煮,\n")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/import/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"rowNo\":2,\"name\":\"菜\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("user 角色访问导入接口：403")
    void importEndpoints_withUserRole_403() throws Exception {
        mockMvc.perform(multipart(BASE + "/import").file(csvFile(HEADER_ROW + "\n,菜,盐,煮,\n"))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"rowNo\":2,\"name\":\"菜\"}]}"))
                .andExpect(status().isForbidden());
    }

    // ==================== 模板下载 ====================

    @Test
    @DisplayName("admin 下载模板：xlsx 字节流可回传给预览接口解析（模板与解析自洽）")
    void template_downloadAndReparse() throws Exception {
        MvcResult templateResult = mockMvc.perform(get(BASE + "/import/template")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        byte[] bytes = templateResult.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0, "模板字节非空");
        assertTrue(templateResult.getResponse().getContentType()
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        // 模板回灌预览接口：示例行被识别（端到端自洽）
        mockMvc.perform(multipart(BASE + "/import").file(new MockMultipartFile(
                        "file", "dining_recipes_import_template.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("匿名下载模板：401")
    void template_anonymous_401() throws Exception {
        mockMvc.perform(get(BASE + "/import/template"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 预览（不落库） ====================

    @Test
    @DisplayName("admin 上传 CSV 预览：逐行校验结果返回（合法行/错误行各带行号与原因），库中零新增")
    void preview_csv_rowLevelResults_andNoWrite() throws Exception {
        createCategory("汤羹");
        String body = HEADER_ROW + "\n汤羹,番茄蛋汤,西红柿、鸡蛋,冲汤,\n幽灵分类,幽灵菜,盐,煮,\n";
        mockMvc.perform(multipart(BASE + "/import").file(csvFile(body))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.validCount").value(1))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].rowNo").value(2))
                .andExpect(jsonPath("$.data.rows[0].categoryId").value(categoryIdByName("汤羹")))
                .andExpect(jsonPath("$.data.rows[0].name").value("番茄蛋汤"))
                .andExpect(jsonPath("$.data.rows[0].ingredients.length()").value(2))
                .andExpect(jsonPath("$.data.rows[1].errors[0]",
                        org.hamcrest.Matchers.containsString("幽灵分类")))
                .andExpect(jsonPath("$.data.rows[1].rowNo").value(3));

        // 预览不落库
        assertEquals(0, countRecipes(), "预览阶段不得写入任何菜谱");
    }

    @Test
    @DisplayName("admin 上传缺列表头 CSV：整体拒绝（code=201，指明缺哪列），库中零新增")
    void preview_missingColumn_businessFail() throws Exception {
        mockMvc.perform(multipart(BASE + "/import")
                        .file(csvFile("分类名,食材名列表,步骤,封面链接\n汤羹,盐,煮,\n"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("菜名")));
        assertEquals(0, countRecipes());
    }

    // ==================== 确认（整批事务） ====================

    @Test
    @DisplayName("admin 确认导入：全部通过逐行写入 H2（默认上架/ADMIN 来源/分类映射），逐行报告含菜谱 ID")
    void confirm_allValid_writesAll() throws Exception {
        long categoryId = createCategory("汤羹");
        String body = """
                {"rows":[{"rowNo":2,"categoryName":"汤羹","name":"番茄蛋汤","ingredients":["西红柿","鸡蛋"],"steps":"冲汤"},
                         {"rowNo":3,"categoryName":null,"name":"凉拌黄瓜","ingredients":["黄瓜"],"steps":"拍碎"}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.rolledBack").value(false))
                .andExpect(jsonPath("$.data.rows[0].success").value(true))
                .andExpect(jsonPath("$.data.rows[0].recipeId").isNumber());

        // H2 回读：逐行写入生效（走列表接口而非直查库，外部行为即契约）；列表按 id 降序，按菜名定位目标行
        MvcResult listResult = mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        JsonNode records = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records");
        JsonNode first = null;
        for (JsonNode node : records) {
            if ("番茄蛋汤".equals(node.path("name").asText())) {
                first = node;
            }
        }
        assertEquals("汤羹", first.path("categoryName").asText(), "导入菜谱分类映射生效");
        assertEquals(1, first.path("status").asInt(), "导入菜谱默认上架");
        assertEquals("ADMIN", first.path("source").asText());
        assertEquals(2, first.path("ingredients").size());
        assertEquals(categoryId, first.path("categoryId").asLong());
    }

    @Test
    @DisplayName("admin 确认导入：任一行失败（分类不存在）全批不落库，逐行失败报告可读")
    void confirm_anyInvalid_rollsBackWholeBatch() throws Exception {
        createCategory("汤羹");
        String body = """
                {"rows":[{"rowNo":2,"categoryName":"汤羹","name":"番茄蛋汤","ingredients":["西红柿"],"steps":"冲汤"},
                         {"rowNo":3,"categoryName":"幽灵分类","name":"幽灵菜","ingredients":["盐"],"steps":"煮"}]}""";
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.rolledBack").value(true))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.rows[1].error",
                        org.hamcrest.Matchers.containsString("分类")));

        assertEquals(0, countRecipes(), "任一行失败必须全批不落库");
    }

    @Test
    @DisplayName("admin 确认导入：请求体缺 rows / 行缺菜名 → 校验拒绝，明确业务提示")
    void confirm_invalidRequest_rejected() throws Exception {
        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("rows")));

        mockMvc.perform(post(BASE + "/import/confirm")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{\"rowNo\":2,\"ingredients\":[\"盐\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("菜名")));
        assertEquals(0, countRecipes());
    }

    // ==================== 辅助 ====================

    private static final String HEADER_ROW = "分类名,菜名,食材名列表,步骤,封面链接";

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "recipes.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private long createCategory(String name) throws Exception {
        mockMvc.perform(post(CATEGORY_BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return categoryIdByName(name);
    }

    /** 从分类列表接口反查分类 ID（外部行为即契约） */
    private long categoryIdByName(String name) throws Exception {
        MvcResult result = mockMvc.perform(get(CATEGORY_BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("records");
        for (JsonNode node : records) {
            if (name.equals(node.path("name").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("分类不存在: " + name);
    }

    /** 菜谱总数（H2 直查计数，专供「不落库/全批回滚」断言） */
    private int countRecipes() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dining_recipe", Integer.class);
    }
}
