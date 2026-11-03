package com.smartwash.controller.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户端 Web 接口契约集成测试（test profile：H2 + mock Redis）。
 * 覆盖：
 * 1. 校园卡绑定/解绑路径拼写统一为 bindCampus/unBindCampus（旧 bingCampus 路径必须 404）；
 * 2. 用户优惠券分页参数统一为 page/size（旧 pageSize 不再绑定，回退默认值）；
 * 3. SecurityConfig 删除 /admin/div/** 死规则后，该前缀仍受 /admin/** 的 ROLE_ADMIN 保护。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("用户端 Web 接口契约集成测试")
class WebUserApiContractIntegrationTest {

    private static final String PHONE = "13700137000";
    private static final Long USER_ID = 9101L;
    private static final String ADMIN_NAME = "web_contract_admin";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 替换真实 Redis：黑名单检查返回 false，上下文不依赖真实中间件 */
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

        jdbcTemplate.update("DELETE FROM user_coupon WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (?, 1, ?, 'pwd')",
                USER_ID, PHONE);
        jdbcTemplate.update("INSERT INTO admin_users(admin_id, username, password_hash, role_id) VALUES (9101, ?, 'pwd', 1)",
                ADMIN_NAME);

        userToken = jwtUtil.generatorToken("user-" + PHONE);
        adminToken = jwtUtil.generatorToken("admin-" + ADMIN_NAME);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_coupon WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE phone_number = ?", PHONE);
        jdbcTemplate.update("DELETE FROM admin_users WHERE username = ?", ADMIN_NAME);
    }

    // ==================== 任务 1：bingCampus 路径拼写统一 ====================

    @Test
    @DisplayName("绑定/解绑校园卡：新路径 bindCampus/unBindCampus 生效并落库")
    void campusBind_newPaths_workAndPersist() throws Exception {
        mockMvc.perform(post("/web/auth/user/bindCampus/C2021001").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
        org.junit.jupiter.api.Assertions.assertEquals("C2021001",
                jdbcTemplate.queryForObject("SELECT campus_card FROM users WHERE user_id = ?", String.class, USER_ID));

        mockMvc.perform(post("/web/auth/user/unBindCampus").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
        org.junit.jupiter.api.Assertions.assertNull(
                jdbcTemplate.queryForObject("SELECT campus_card FROM users WHERE user_id = ?", String.class, USER_ID));
    }

    @Test
    @DisplayName("绑定/解绑校园卡：旧拼写 bingCampus/unBingCampus 路径已下线（无成功响应且不落库）")
    void campusBind_oldTypoPaths_noLongerBound() throws Exception {
        mockMvc.perform(post("/web/auth/user/bingCampus/C2021001").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.code").value(201));
        org.junit.jupiter.api.Assertions.assertNull(
                jdbcTemplate.queryForObject("SELECT campus_card FROM users WHERE user_id = ?", String.class, USER_ID),
                "旧绑定路径不得再写入校园卡");

        mockMvc.perform(post("/web/auth/user/unBingCampus").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.code").value(201));
    }

    // ==================== 任务 2：分页参数统一 page/size ====================

    @Test
    @DisplayName("用户优惠券分页：size 生效（每页 1 条只返回 1 条）")
    void getUserCoupon_sizeParam_isBound() throws Exception {
        seedTwoCoupons();

        mockMvc.perform(get("/web/auth/userCoupon/getUserCoupon")
                        .param("status", "0").param("page", "1").param("size", "1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("用户优惠券分页：旧参数 pageSize 不再绑定，回退默认每页 10 条")
    void getUserCoupon_legacyPageSize_fallsBackToDefault() throws Exception {
        seedTwoCoupons();

        mockMvc.perform(get("/web/auth/userCoupon/getUserCoupon")
                        .param("status", "0").param("page", "1").param("pageSize", "1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private void seedTwoCoupons() {
        jdbcTemplate.update("DELETE FROM coupon WHERE coupon_id IN (9101, 9102)");
        jdbcTemplate.update("INSERT INTO coupon(coupon_id, title, discount, threshold, start_time, end_time, valid_days) "
                + "VALUES (9101, 'a', 5.00, 20.00, '2020-01-01 00:00:00', '2030-01-01 00:00:00', 7)");
        jdbcTemplate.update("INSERT INTO coupon(coupon_id, title, discount, threshold, start_time, end_time, valid_days) "
                + "VALUES (9102, 'b', 5.00, 20.00, '2020-01-01 00:00:00', '2030-01-01 00:00:00', 7)");
        jdbcTemplate.update("INSERT INTO user_coupon(user_coupon_id, user_id, coupon_id, is_used, received_at, expired_at) "
                + "VALUES (9101, ?, 9101, 0, CURRENT_TIMESTAMP, '2030-01-01 00:00:00')", USER_ID);
        jdbcTemplate.update("INSERT INTO user_coupon(user_coupon_id, user_id, coupon_id, is_used, received_at, expired_at) "
                + "VALUES (9102, ?, 9102, 0, CURRENT_TIMESTAMP, '2030-01-01 00:00:00')", USER_ID);
    }

    // ==================== 任务 3：SecurityConfig 死配置清理后 /admin/div/** 仍受保护 ====================

    @Test
    @DisplayName("/admin/div/** 匿名访问：401（删除死规则不得放开）")
    void adminDiv_anonymous_401() throws Exception {
        mockMvc.perform(get("/admin/div/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/admin/div/** user 角色访问：403（ROLE_ADMIN 约束仍生效）")
    void adminDiv_withUserRole_403() throws Exception {
        mockMvc.perform(get("/admin/div/usage").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/admin/div/** admin 角色访问：200（通配规则已覆盖，无需重复显式规则）")
    void adminDiv_withAdminRole_200() throws Exception {
        mockMvc.perform(get("/admin/div/usage").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
