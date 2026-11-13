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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 菜谱管理端集成测试（test profile：H2 + mock Redis/FileStorageService，对齐
 * DiningCategoryAdminControllerIntegrationTest 先例）。三条防线一次覆盖：
 * 1. 权限：/admin/dining/recipes 由 SecurityConfig /admin/** 通配保护——匿名 401、user 角色 403、admin 角色放行；
 * 2. 信封：所有响应统一 Result{code,message,data}，业务失败 HTTP 200 + code=201（FAIL）；
 * 3. CRUD 与上下架行为：新增默认上架、三筛选生效、上下架语义（下架不出现在上架查询）、
 *    更新/删除生效、封面上传走统一 FileStorageService 返回对象键。
 * 走真实服务层 + H2（封面上传 mock 存储层），不 mock service（行为即契约）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("菜谱管理端集成测试（权限/信封/CRUD/上下架/封面）")
class DiningRecipeAdminControllerIntegrationTest {

    private static final String PHONE = "13900139001";
    private static final String ADMIN_NAME = "dining_admin";
    private static final String BASE = "/admin/dining/recipes";
    private static final String CATEGORY_BASE = "/admin/dining/categories";

    /** 1x1 合法 PNG（固定字节头 + IHDR，魔数校验命中且与 .png 扩展名一致） */
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15,
            (byte) 0xC4, (byte) 0x89, 0, 0, 0, 10, 'I', 'D', 'A', 'T', 0x78, (byte) 0x9C, 0x63, 0,
            1, 0, 0, 5, 0, 1, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE,
            0x42, 0x60, (byte) 0x82};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /** 封面存储层替换为 mock：集成测试不触真实 MinIO（对象键行为由 service 单测覆盖） */
    @MockBean
    private FileStorageService fileStorageService;

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
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (9102, 1, ?, 'pwd')", PHONE);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9102, ?, 'pwd', 1)", ADMIN_NAME);

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
    @DisplayName("匿名访问菜谱列表：401（/admin/** 需认证）")
    void list_anonymous_401() throws Exception {
        mockMvc.perform(get(BASE + "/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("user 角色访问管理端菜谱接口：403")
    void adminEndpoints_withUserRole_403() throws Exception {
        mockMvc.perform(get(BASE + "/all").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"番茄炒蛋\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(BASE + "/cover").file(coverFile(PNG_BYTES, "cover.png"))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ==================== CRUD 与上下架 ====================

    @Test
    @DisplayName("admin 全链路：建分类→新增（默认上架）→列表回读（分类名/食材数组）→筛选→更新→删除")
    void adminCrud_fullFlow() throws Exception {
        long categoryId = createCategory("汤羹");

        // 新增：HTTP 200 + code 200 信封；不传分类时未分类
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"番茄炒蛋\",\"categoryId\":" + categoryId
                                + ",\"ingredients\":[\"西红柿\",\"鸡蛋\"],\"steps\":\"热锅下油\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 缺 name：@Valid 校验返回业务失败信封
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[\"盐\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("菜名不能为空")));

        // 分类不存在：业务失败
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"幽灵菜\",\"categoryId\":99999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("分类不存在"));

        // 列表回读：默认上架（status=1）、分类名联查、食材反序列化、来源 ADMIN
        MvcResult listResult = mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        JsonNode record = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records").get(0);
        assertEquals("番茄炒蛋", record.path("name").asText());
        assertEquals("汤羹", record.path("categoryName").asText());
        assertEquals(1, record.path("status").asInt(), "管理端新增默认上架");
        assertEquals("ADMIN", record.path("source").asText());
        assertEquals(2, record.path("ingredients").size(), "食材必须以结构化数组返回");
        assertEquals("西红柿", record.path("ingredients").get(0).asText());
        long recipeId = record.path("id").asLong();

        // 三筛选：categoryId 精确 / keyword 模糊 / 组合查询不命中
        mockMvc.perform(get(BASE + "/all").param("categoryId", String.valueOf(categoryId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get(BASE + "/all").param("keyword", "番茄")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get(BASE + "/all").param("keyword", "不存在的菜")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 更新：改名 + 换未分类生效
        mockMvc.perform(post(BASE + "/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + recipeId + ",\"name\":\"西红柿炒蛋\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("西红柿炒蛋"));

        // 删除：物理删除后列表为空
        mockMvc.perform(delete(BASE + "/delete/{ids}", recipeId)
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
    @DisplayName("上下架语义：下架后不出现在 status=1 上架查询，status=0 可单独筛出，重新上架恢复")
    void statusChange_offShelf_notInOnShelfQuery() throws Exception {
        long idA = createRecipe("番茄炒蛋");
        long idB = createRecipe("凉拌黄瓜");

        // 下架 A
        mockMvc.perform(post(BASE + "/{id}/status/{status}", idA, 0)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 上架查询只看到 B
        MvcResult onShelf = mockMvc.perform(get(BASE + "/all").param("status", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        JsonNode onShelfRecords = objectMapper.readTree(onShelf.getResponse().getContentAsString())
                .path("data").path("records");
        assertEquals("凉拌黄瓜", onShelfRecords.get(0).path("name").asText(),
                "下架的菜谱不得出现在上架查询结果中");
        assertEquals(idB, onShelfRecords.get(0).path("id").asLong());

        // 下架查询只看到 A
        MvcResult offShelf = mockMvc.perform(get(BASE + "/all").param("status", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        assertEquals(idA, objectMapper.readTree(offShelf.getResponse().getContentAsString())
                .path("data").path("records").get(0).path("id").asLong());

        // 无筛选全量仍可见两菜谱（下架只影响上架查询，不影响管理端全量列表）
        mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        // 重新上架恢复
        mockMvc.perform(post(BASE + "/{id}/status/{status}", idA, 1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get(BASE + "/all").param("status", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("上下架：非法 status（2）与不存在的菜谱均返回明确业务失败")
    void statusChange_illegalOrMissing_rejected() throws Exception {
        mockMvc.perform(post(BASE + "/{id}/status/{status}", 1, 2)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("status 仅支持 1=上架 0=下架"));

        mockMvc.perform(post(BASE + "/{id}/status/{status}", 99999, 1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜谱不存在"));
    }

    // ==================== 封面上传 ====================

    @Test
    @DisplayName("封面上传：合法 PNG 走统一 FileStorageService，返回 dining/covers/ 前缀对象键")
    void uploadCover_validPng_returnsObjectKey() throws Exception {
        when(fileStorageService.uploadFile(any(), eq("dining/covers"))).thenReturn("dining/covers/abc123.png");

        mockMvc.perform(multipart(BASE + "/cover").file(coverFile(PNG_BYTES, "cover.png"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("dining/covers/abc123.png"));
    }

    @Test
    @DisplayName("封面上传：改后缀攻击（png 扩展名夹带 HTML）返回业务失败")
    void uploadCover_disguisedContent_rejected() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        mockMvc.perform(multipart(BASE + "/cover").file(coverFile(html, "evil.png"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅支持 JPG/PNG/WebP 格式图片"));
    }

    // ==================== 分类删除引用校验（补 01 号票 TODO，端到端） ====================

    @Test
    @DisplayName("分类删除被菜谱引用时拒绝：返回明确业务提示，分类与引用它的菜谱均保留")
    void deleteCategory_referencedByRecipe_rejected() throws Exception {
        long categoryId = createCategory("家常菜");
        createRecipeWithCategory("番茄炒蛋", categoryId);

        mockMvc.perform(delete(CATEGORY_BASE + "/delete/{ids}", categoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("已被 1 个菜谱引用")))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("无法删除")));

        // 分类与菜谱均保留（先校验后删，整体拒绝）
        mockMvc.perform(get(CATEGORY_BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 删除菜谱解除引用后，分类可正常删除
        MvcResult listResult = mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long recipeId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records").get(0).path("id").asLong();
        mockMvc.perform(delete(BASE + "/delete/{ids}", recipeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete(CATEGORY_BASE + "/delete/{ids}", categoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 辅助 ====================

    private MockMultipartFile coverFile(byte[] content, String filename) {
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }

    /** 直连服务层灌分类（前置数据），返回新分类 id */
    private long createCategory(String name) throws Exception {
        MvcResult result = mockMvc.perform(post(CATEGORY_BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        MvcResult listResult = mockMvc.perform(get(CATEGORY_BASE + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records");
        for (JsonNode node : records) {
            if (name.equals(node.path("name").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("分类创建失败: " + name);
    }

    /** 直连服务层灌菜谱（上架，默认状态），返回新菜谱 id */
    private long createRecipe(String name) throws Exception {
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return latestRecipeIdByName(name);
    }

    /** 直连服务层灌挂分类菜谱（分类引用校验用例的前置数据） */
    private void createRecipeWithCategory(String name, long categoryId) throws Exception {
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"categoryId\":" + categoryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 按菜名从列表接口反查 id（辅助断言，不直查库） */
    private long latestRecipeIdByName(String name) throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/all").param("keyword", name)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("records");
        assertEquals(1, records.size(), "按菜名反查应恰好命中一条: " + name);
        return records.get(0).path("id").asLong();
    }
}
