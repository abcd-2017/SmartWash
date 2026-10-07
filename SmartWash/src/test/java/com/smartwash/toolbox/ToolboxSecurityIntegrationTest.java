package com.smartwash.toolbox;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 短链安全与白名单集成测试（test profile：H2 + mock Redis，方案 §十 4/5 项的 Security 层部分）。
 * 语义：/web/t/** 免 JWT（匿名 302/404，不得 401）；/web/auth/toolbox/** 匿名 401；
 * user 角色访问管理端 403；admin 角色正常治理，删除后短码公开路径 404。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("短链安全白名单与角色隔离集成测试")
class ToolboxSecurityIntegrationTest {

    private static final String CODE = "okpub01";
    private static final String PRIVATE_CODE = "okprv01";
    private static final String PHONE = "13800138000";
    private static final String ADMIN_NAME = "toolbox_admin";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 替换真实 Redis：跳转/负缓存全部 miss，链路走 H2；黑名单检查返回 false */
    @MockBean
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private ValueOperations<String, String> valueOperations;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // mock Redis：get 全 miss、set/hasKey/delete 空实现，保证 resolve 走 DB（H2）
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        jdbcTemplate.update("DELETE FROM toolbox_short_code WHERE code IN (?, ?)", CODE, PRIVATE_CODE);
        jdbcTemplate.update("DELETE FROM toolbox_short_visit WHERE code IN (?, ?)", CODE, PRIVATE_CODE);
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (9001, 1, ?, 'pwd')", PHONE);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9001, ?, 'pwd', 1)", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO toolbox_short_code(code, content_type, target, owner_user_id, is_public) "
                + "VALUES (?, 1, 'https://example.com/hit', 9001, 1)", CODE);
        jdbcTemplate.update("INSERT INTO toolbox_short_code(code, content_type, target, owner_user_id, is_public) "
                + "VALUES (?, 1, 'https://example.com/private', 9001, 2)", PRIVATE_CODE);

        userToken = jwtUtil.generatorToken("user-" + PHONE);
        adminToken = jwtUtil.generatorToken("admin-" + ADMIN_NAME);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM toolbox_short_code WHERE code IN (?, ?)", CODE, PRIVATE_CODE);
        jdbcTemplate.update("DELETE FROM toolbox_short_visit WHERE code IN (?, ?)", CODE, PRIVATE_CODE);
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
    }

    @Test
    @DisplayName("公开码匿名访问：302 + Location + no-store（/web/t/** 白名单免 JWT）")
    void publicCode_anonymous_302WithNoStore() throws Exception {
        mockMvc.perform(get("/web/t/" + CODE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/hit"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("私有码匿名访问：404（不暴露存在性），同样免 JWT")
    void privateCode_anonymous_404() throws Exception {
        mockMvc.perform(get("/web/t/" + PRIVATE_CODE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("不存在的短码匿名访问：业务 404 而非 401（白名单生效）")
    void missingCode_anonymous_404Not401() throws Exception {
        mockMvc.perform(get("/web/t/zzzz999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("用户端接口匿名访问：401")
    void userEndpoints_anonymous_401() throws Exception {
        mockMvc.perform(get("/web/auth/toolbox/short-codes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("user 角色访问管理端短码接口：403")
    void adminEndpoints_withUserRole_403() throws Exception {
        mockMvc.perform(get("/admin/toolbox/short-codes").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin 角色访问管理端短码接口：200 统一信封")
    void adminEndpoints_withAdminRole_200() throws Exception {
        mockMvc.perform(get("/admin/toolbox/short-codes").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("admin 治理删除：删除成功后旧码公开路径立即 404")
    void adminDelete_thenPublicPath404() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM toolbox_short_code WHERE code = ?", Long.class, CODE);

        mockMvc.perform(delete("/admin/toolbox/short-codes/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/web/t/" + CODE))
                .andExpect(status().isNotFound());
    }
}
