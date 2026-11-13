package com.smartwash.dining.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 菜谱用户端集成测试（test profile：H2 + mock Redis/FileStorageService，对齐
 * WebDiningOrderControllerIntegrationTest / DiningRecipeAdminControllerIntegrationTest 先例）。
 * 覆盖验收路径：
 * 1. 权限：/web/auth/dining/** 由 SecurityConfig /web/auth/** 通配保护——匿名 401、admin 角色 403、user 角色放行（ROLE_USER）；
 * 2. 仅上架可见：列表固定过滤下架（无 status 入参可翻）、详情对下架/不存在同语义拒绝；
 * 3. 新增：source=USER、created_by_user_id 落库、直上架立即出现在上架列表（全员可见）、分类哨兵协议；
 * 4. 封面上传：与管理端同链路（RecipeImageValidator + FileStorageService），返回对象键；
 * 5. 分类目录：用户端只读全量分类（sort 升序），供菜谱库筛选。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("菜谱用户端集成测试（权限/仅上架/新增直上架/封面/分类目录）")
class WebDiningRecipeControllerIntegrationTest {

    private static final String PHONE = "13900139002";
    private static final String ADMIN_NAME = "dining_admin_web";
    private static final String BASE = "/web/auth/dining/recipes";
    private static final String CATEGORY_BASE = "/web/auth/dining/categories";

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

        cleanAll();

        // 用户 + 管理员 + 分类 + 菜谱：201/202 上架（201 挂分类 11），203 下架
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (9201, 1, ?, 'pwd')", PHONE);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9201, ?, 'pwd', 1)", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO dining_category(id, name, sort) VALUES (11, '家常菜', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, ingredients, steps, source, status) "
                + "VALUES (201, 11, '番茄炒蛋', '[\"西红柿\",\"鸡蛋\"]', '热锅下油', 'ADMIN', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, source, status) "
                + "VALUES (202, 11, '凉拌黄瓜', 'ADMIN', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, steps, source, status) "
                + "VALUES (203, 11, '下架菜谱', '机密步骤', 'ADMIN', 0)");

        userToken = jwtUtil.generatorToken("user-" + PHONE);
        adminToken = jwtUtil.generatorToken("admin-" + ADMIN_NAME);
    }

    @AfterEach
    void cleanUp() {
        cleanAll();
    }

    private void cleanAll() {
        // 与先例一致全表清理（H2 测试库每进程独立，无并行共享数据）
        jdbcTemplate.update("DELETE FROM dining_recipe");
        jdbcTemplate.update("DELETE FROM dining_category");
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
    }

    // ==================== 权限（ROLE_USER） ====================

    @Test
    @DisplayName("匿名访问用户端菜谱接口：401（/web/auth/** 需认证）")
    void anonymous_401() throws Exception {
        mockMvc.perform(get(BASE + "/all")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/{id}", 201)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"番茄炒蛋\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(BASE + "/cover").file(coverFile()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CATEGORY_BASE + "/all")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("admin 角色访问用户端接口：403（/web/auth/** 限定 ROLE_USER）")
    void adminRole_403() throws Exception {
        mockMvc.perform(get(BASE + "/all").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"管理员冒充用户\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CATEGORY_BASE + "/all").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // ==================== 仅上架可见 ====================

    @Test
    @DisplayName("上架列表：下架菜谱不可见且无 status 入参可翻；分类/关键词筛选生效")
    void list_onShelfOnlyWithFilters() throws Exception {
        // 全量：只看到两道上架菜
        MvcResult listResult = mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        JsonNode records = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("records");
        for (JsonNode record : records) {
            assertEquals(1, record.path("status").asInt(), "用户端列表必须全为上架菜谱");
        }

        // 分类筛选
        mockMvc.perform(get(BASE + "/all").param("categoryId", "11")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        // 关键词筛选
        mockMvc.perform(get(BASE + "/all").param("keyword", "番茄")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("番茄炒蛋"));

        // 尝试以 status 参数越权翻下架数据：参数被忽略，仍只返回上架
        mockMvc.perform(get(BASE + "/all").param("status", "0")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("详情：上架菜谱返回食材数组与步骤；下架与不存在同语义拒绝")
    void detail_onShelfVisibleOffShelfRejected() throws Exception {
        // 上架可见：食材结构化数组 + 步骤全文
        mockMvc.perform(get(BASE + "/{id}", 201)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("番茄炒蛋"))
                .andExpect(jsonPath("$.data.ingredients[0]").value("西红柿"))
                .andExpect(jsonPath("$.data.ingredients[1]").value("鸡蛋"))
                .andExpect(jsonPath("$.data.steps").value("热锅下油"));

        // 下架拒绝（与不存在同提示，不泄露「机密步骤」）
        mockMvc.perform(get(BASE + "/{id}", 203)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜谱不存在或已下架"));

        // 不存在拒绝
        mockMvc.perform(get(BASE + "/{id}", 99999)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜谱不存在或已下架"));
    }

    // ==================== 新增（source=USER 直上架） ====================

    @Test
    @DisplayName("新增全链路：source=USER 与创建用户落库、直上架立即全员可见、分类可空未分类")
    void add_userSourceDirectShelf_fullFlow() throws Exception {
        // 带分类新增
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"user-拿手红烧肉\",\"categoryId\":11,"
                                + "\"ingredients\":[\"五花肉\",\"冰糖\"],\"steps\":\"小火慢炖\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 不带分类新增（未分类）
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"user-凉拌西红柿\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 落库核对：source=USER / created_by_user_id / 直上架 / 管理端创建人为空
        Long userId = jdbcTemplate.queryForObject(
                "SELECT created_by_user_id FROM dining_recipe WHERE name = 'user-拿手红烧肉'", Long.class);
        assertEquals(9201L, userId, "创建人用户 ID 必须落库");
        String source = jdbcTemplate.queryForObject(
                "SELECT source FROM dining_recipe WHERE name = 'user-拿手红烧肉'", String.class);
        assertEquals("USER", source);
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM dining_recipe WHERE name = 'user-拿手红烧肉'", Integer.class);
        assertEquals(1, status, "用户端新增必须直上架（无审核流）");
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT created_by_admin_id FROM dining_recipe WHERE name = 'user-拿手红烧肉'", Long.class);
        assertNull(adminId, "用户端新增不得误记管理员创建人");
        Long uncategorizedCategoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM dining_recipe WHERE name = 'user-凉拌西红柿'", Long.class);
        assertNull(uncategorizedCategoryId, "不传分类必须落未分类（category_id 为 null）");

        // 立即可见：新增后上架列表出现新菜谱（直上架全员可见，spec 决策 8）
        mockMvc.perform(get(BASE + "/all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));
        mockMvc.perform(get(BASE + "/all").param("keyword", "拿手红烧肉")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].source").value("USER"))
                .andExpect(jsonPath("$.data.records[0].status").value(1));
    }

    @Test
    @DisplayName("新增：分类不存在拒绝、未分类哨兵 0 归一、name 缺失走校验失败")
    void add_validationAndSentinel() throws Exception {
        // 分类不存在：业务失败不落库
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"user-幽灵菜\",\"categoryId\":99999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("分类不存在"));

        // 哨兵 0 = 未分类：正常入库
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"user-哨兵未分类\",\"categoryId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM dining_recipe WHERE name = 'user-哨兵未分类'", Long.class);
        assertNull(categoryId, "categoryId=0 必须归一为 null（未分类）");

        // name 缺失：@Valid 校验失败信封
        mockMvc.perform(post(BASE + "/add")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[\"盐\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("菜名不能为空")));
    }

    // ==================== 封面上传 ====================

    @Test
    @DisplayName("封面上传：合法 PNG 走统一 FileStorageService 返回 dining/covers/ 对象键")
    void uploadCover_validPng_returnsObjectKey() throws Exception {
        when(fileStorageService.uploadFile(any(), eq("dining/covers"))).thenReturn("dining/covers/web-u1.png");

        mockMvc.perform(multipart(BASE + "/cover").file(coverFile())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("dining/covers/web-u1.png"));
    }

    @Test
    @DisplayName("封面上传：改后缀攻击（png 扩展名夹带 HTML）返回业务失败")
    void uploadCover_disguisedContent_rejected() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        mockMvc.perform(multipart(BASE + "/cover")
                        .file(new MockMultipartFile("file", "evil.png", "application/octet-stream", html))
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅支持 JPG/PNG/WebP 格式图片"));
    }

    // ==================== 分类目录（只读） ====================

    @Test
    @DisplayName("分类目录：用户端全量只读，sort 升序返回 id 与名称")
    void categories_allForUser() throws Exception {
        mockMvc.perform(get(CATEGORY_BASE + "/all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].name").value("家常菜"));
    }

    // ==================== 辅助 ====================

    private MockMultipartFile coverFile() {
        return new MockMultipartFile("file", "cover.png", "application/octet-stream", PNG_BYTES);
    }
}
