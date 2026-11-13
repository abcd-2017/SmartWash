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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 点单订单用户端集成测试（test profile：H2 + mock Redis，对齐 DiningCategoryAdminControllerIntegrationTest 先例）。
 * 走真实服务层 + H2，覆盖验收路径：
 * 1. 权限：/web/auth/dining/orders 由 /web/auth/** 通配保护——匿名 401；
 * 2. 创建：菜谱上架校验（不存在/下架拒绝）、创建者即首个成员、join_token 生成、fromOrderId 复用；
 * 3. version 轮询协议：knownVersion 一致轻量（unchanged=true），不一致全量；
 * 4. 乐观并发：选菜 version 冲突返回业务码 409，每次变更 version 递增；
 * 5. join 三类失败：token 错误 / 订单已结束 / 重复加入；
 * 6. 选菜校验：成员身份 / 菜在本单菜单内 / 结束状态写拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("点单订单用户端集成测试（权限/创建/轮询/并发/加入/选菜）")
class WebDiningOrderControllerIntegrationTest {

    private static final String CREATOR_PHONE = "13900000001";
    private static final String JOINER_PHONE = "13900000002";
    private static final String THIRD_PHONE = "13900000003";
    private static final String FOURTH_PHONE = "13900000004";
    private static final String BASE = "/web/auth/dining/orders";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private ValueOperations<String, String> valueOperations;

    private String creatorToken;
    private String joinerToken;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        cleanAll();

        // 用户（发起者/加入者）+ 分类 + 菜谱：101/102 上架，103 下架
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (8801, 1, ?, 'pwd')", CREATOR_PHONE);
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (8802, 1, ?, 'pwd')", JOINER_PHONE);
        jdbcTemplate.update("INSERT INTO dining_category(id, name, sort) VALUES (11, '家常菜', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, ingredients, status) "
                + "VALUES (101, 11, '番茄炒蛋', '[\"西红柿\",\"鸡蛋\",\"葱\"]', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, ingredients, status) "
                + "VALUES (102, 11, '凉拌黄瓜', '[\"黄瓜\",\"蒜\"]', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, status) VALUES (103, 11, '下架菜', 0)");

        creatorToken = jwtUtil.generatorToken("user-" + CREATOR_PHONE);
        joinerToken = jwtUtil.generatorToken("user-" + JOINER_PHONE);
    }

    @AfterEach
    void cleanUp() {
        cleanAll();
    }

    private void cleanAll() {
        jdbcTemplate.update("DELETE FROM notice_read");
        jdbcTemplate.update("DELETE FROM notice");
        jdbcTemplate.update("DELETE FROM dining_order_item");
        jdbcTemplate.update("DELETE FROM dining_order_menu");
        jdbcTemplate.update("DELETE FROM dining_order_member");
        jdbcTemplate.update("DELETE FROM dining_order");
        jdbcTemplate.update("DELETE FROM dining_recipe WHERE id IN (101, 102, 103)");
        jdbcTemplate.update("DELETE FROM dining_category WHERE id = 11");
        jdbcTemplate.update("DELETE FROM users WHERE phone_number IN (?, ?, ?, ?)",
                CREATOR_PHONE, JOINER_PHONE, THIRD_PHONE, FOURTH_PHONE);
    }

    // ==================== 权限 ====================

    @Test
    @DisplayName("匿名访问订单接口：401（/web/auth/** 需认证）")
    void anonymous_401() throws Exception {
        mockMvc.perform(get(BASE + "/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"recipeIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 创建 ====================

    @Test
    @DisplayName("创建全链路：订单/首个成员/菜单落库，token 与 version=0 回传；菜谱下架拒绝；title 缺失走校验")
    void create_fullFlow() throws Exception {
        // 成功创建（上架菜 101/102）
        MvcResult created = mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"周五晚火锅\",\"recipeIds\":[101,102]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        long orderId = data.path("orderId").asLong();
        String joinToken = data.path("joinToken").asText();
        assertTrue(orderId > 0);
        assertTrue(joinToken.length() >= 16, "必须生成随机 join_token");

        // 落库核对：创建者即首个成员、菜单两条、version=0
        Long memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_member WHERE order_id = ? AND user_id = 8801", Long.class, orderId);
        assertEquals(1L, memberCount, "创建者必须自动成为首个成员");
        Long menuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_menu WHERE order_id = ?", Long.class, orderId);
        assertEquals(2L, menuCount);
        Integer version = jdbcTemplate.queryForObject(
                "SELECT version FROM dining_order WHERE id = ?", Integer.class, orderId);
        assertEquals(0, version);

        // 菜谱不存在：整体拒绝
        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"幽灵菜单\",\"recipeIds\":[101,404]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部分菜谱不存在或已下架，无法加入本单菜单"));

        // 菜谱已下架（103 status=0）：拒绝
        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"含下架菜\",\"recipeIds\":[103]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));

        // title 缺失（@Valid 校验）
        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeIds\":[101]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("订单标题不能为空")));
    }

    @Test
    @DisplayName("创建：fromOrderId 复用历史菜单（成员身份才可复用；复用菜谱仍须上架）")
    void create_fromOrderId_reuse() throws Exception {
        // 发起者建单（菜单 101/102）
        long sourceOrderId = createOrder(creatorToken, "源订单", "[101,102]");
        // 发起者从该单复用：成功，新单菜单同样两条
        MvcResult reused = mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"复用单\",\"fromOrderId\":" + sourceOrderId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long newOrderId = objectMapper.readTree(reused.getResponse().getContentAsString()).path("data").path("orderId").asLong();
        // 菜单一致：新单菜单与源单逐道一致且保序（复用是整份菜单复制，不是随机子集）
        assertEquals(menuRecipeIds(sourceOrderId), menuRecipeIds(newOrderId),
                "复用创建的新单菜单必须与源单逐道一致且保序");
        assertEquals(java.util.List.of(101L, 102L), menuRecipeIds(newOrderId));

        // 非成员复用：拒绝
        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"他人复用\",\"fromOrderId\":" + sourceOrderId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅订单成员可复用历史菜单"));

        // recipeIds 与 fromOrderId 同给：拒绝
        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"冲突参数\",\"recipeIds\":[101],\"fromOrderId\":" + sourceOrderId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("recipeIds 与 fromOrderId 只能二选一"));
    }

    // ==================== 我的订单列表 ====================

    @Test
    @DisplayName("我的订单列表：我发起 + 我参与都出现，isCreator 标记正确")
    void myOrders_creatorAndJoiner() throws Exception {
        long orderId = createOrder(creatorToken, "周五晚火锅", "[101,102]");
        joinOrder(joinerToken, orderId);

        // 发起者视角
        JsonNode creatorList = getList(BASE + "/my", creatorToken);
        assertEquals(1, creatorList.size());
        assertEquals("周五晚火锅", creatorList.get(0).path("title").asText());
        assertTrue(creatorList.get(0).path("isCreator").asBoolean());

        // 参与者视角
        JsonNode joinerList = getList(BASE + "/my", joinerToken);
        assertEquals(1, joinerList.size());
        assertTrue(joinerList.get(0).path("isCreator").asBoolean() == false);
        assertEquals("ONGOING", joinerList.get(0).path("status").asText());
    }

    // ==================== 详情轮询协议 ====================

    @Test
    @DisplayName("详情轮询：knownVersion 一致 → unchanged=true 轻量；不一致 → 全量（菜单/选菜/参与者）")
    void detail_pollProtocol() throws Exception {
        long orderId = createOrder(creatorToken, "周五晚火锅", "[101,102]");
        joinOrder(joinerToken, orderId);

        // 创建后 version=0：knownVersion=0 → 轻量
        mockMvc.perform(get(BASE + "/{id}", orderId).param("knownVersion", "0")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.unchanged").value(true))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.title").doesNotExist());

        // 不带 knownVersion → 全量
        MvcResult full = mockMvc.perform(get(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unchanged").value(false))
                .andExpect(jsonPath("$.data.title").value("周五晚火锅"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();
        JsonNode data = objectMapper.readTree(full.getResponse().getContentAsString()).path("data");
        assertEquals(2, data.path("menu").size());
        assertEquals("番茄炒蛋", data.path("menu").get(0).path("name").asText());
        assertEquals(11L, data.path("menu").get(0).path("categoryId").asLong());
        assertEquals(2, data.path("members").size());
        assertTrue(data.path("members").get(0).path("isCreator").asBoolean());
        assertEquals("139****0001", data.path("members").get(0).path("phoneMasked").asText(), "手机号必须脱敏");
        assertNull(data.path("shoppingList").asText(null), "进行中的单无购买清单快照");

        // 非成员查看：拒绝
        mockMvc.perform(get(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + notMemberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅订单成员可查看点单详情"));
    }

    @Test
    @DisplayName("详情 join_token 可见性：仅发起者全量响应回传 token，参与者与轻量响应均不下发")
    void detail_joinToken_onlyVisibleToCreator() throws Exception {
        long orderId = createOrder(creatorToken, "凭证可见单", "[101,102]");
        joinOrder(joinerToken, orderId);
        String expectedToken = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);

        // 发起者全量响应：joinToken 与库内一致（发起者可从列表/详情再次生成二维码）
        mockMvc.perform(get(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.joinToken").value(expectedToken));

        // 轻量响应（knownVersion 一致）：不下发 token，端上沿用首次全量拿到的值
        mockMvc.perform(get(BASE + "/{id}", orderId).param("knownVersion", "0")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.data.unchanged").value(true))
                .andExpect(jsonPath("$.data.joinToken").doesNotExist());

        // 参与者全量响应：不下发 token（凭证不得经详情接口扩散给非发起者）
        mockMvc.perform(get(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.joinToken").doesNotExist());
    }

    // ==================== brief 与加入 ====================

    @Test
    @DisplayName("brief：有效 token 换标题/发起者/菜品数/joined；无效 token 拒绝")
    void brief_flow() throws Exception {
        String token = createOrderAndReturnToken(creatorToken, "扫码单", "[101,102]");
        long orderId = currentOnlyOrderId();

        JsonNode brief = getJson(BASE + "/brief/" + token, joinerToken).path("data");
        assertEquals(orderId, brief.path("orderId").asLong());
        assertEquals("扫码单", brief.path("title").asText());
        assertEquals(2L, brief.path("recipeCount").asLong());
        assertEquals("139****0001", brief.path("creatorName").asText());
        assertTrue(brief.path("joined").asBoolean() == false);

        mockMvc.perform(get(BASE + "/brief/bad-token")
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("加入凭证无效或已重置"));
    }

    @Test
    @DisplayName("加入：成功；token 错误 / 重复加入 / 已结束单 三类失败均有明确业务提示")
    void join_paths() throws Exception {
        long orderId = createOrder(creatorToken, "加入单", "[101]");
        String token = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);

        // token 错误
        mockMvc.perform(post(BASE + "/{id}/join", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinToken\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("加入凭证无效或已重置"));

        // 成功
        mockMvc.perform(post(BASE + "/{id}/join", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_member WHERE order_id = ? AND user_id = 8802", Long.class, orderId);
        assertEquals(1L, memberCount);

        // 重复加入
        mockMvc.perform(post(BASE + "/{id}/join", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("你已在该点单中"));

        // 已结束单（直插 FINISHED）新用户加入
        jdbcTemplate.update(
                "INSERT INTO dining_order(id, title, creator_user_id, status, join_token, version) "
                        + "VALUES (9900, '已结束单', 8801, 'FINISHED', 'fin-token', 3)");
        mockMvc.perform(post(BASE + "/{id}/join", 9900)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinToken\":\"fin-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，无法加入"));
    }

    @Test
    @DisplayName("重置凭证：仅发起者；旧 token 立即失效，新 token 可用")
    void resetJoinToken_flow() throws Exception {
        long orderId = createOrder(creatorToken, "重置单", "[101]");
        String oldToken = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);

        // 非发起者重置：拒绝
        mockMvc.perform(post(BASE + "/{id}/join-token/reset", orderId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅发起者可重置加入凭证"));

        // 发起者重置：返回新 token
        MvcResult reset = mockMvc.perform(post(BASE + "/{id}/join-token/reset", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String newToken = objectMapper.readTree(reset.getResponse().getContentAsString()).path("data").asText();
        assertTrue(newToken.length() >= 16);
        // 新旧不同
        org.junit.jupiter.api.Assertions.assertNotEquals(oldToken, newToken);

        // 旧 token brief 拒绝，新 token 可用
        mockMvc.perform(get(BASE + "/brief/" + oldToken)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(jsonPath("$.code").value(201));
        mockMvc.perform(get(BASE + "/brief/" + newToken)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 选菜（version 乐观并发） ====================

    @Test
    @DisplayName("选菜增改删全链路：勾选（version 0→1）→ 改备注（1→2）→ 取消（2→3）")
    void itemCrud_fullFlow() throws Exception {
        long orderId = createOrder(creatorToken, "选菜单", "[101,102]");
        joinOrder(joinerToken, orderId);

        // 勾选 101（version=0）
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"note\":\"不要辣\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, currentVersion(orderId));
        Long itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_item WHERE order_id = ? AND user_id = 8802", Long.class, orderId);
        assertEquals(1L, itemCount);

        // 改备注（version=1）
        mockMvc.perform(put(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"note\":\"多放葱\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String note = jdbcTemplate.queryForObject(
                "SELECT note FROM dining_order_item WHERE order_id = ? AND user_id = 8802 AND recipe_id = 101",
                String.class, orderId);
        assertEquals("多放葱", note);
        assertEquals(2, currentVersion(orderId));

        // 取消（version=2）
        mockMvc.perform(delete(BASE + "/{orderId}/items/{recipeId}?version=2", orderId, 101)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_item WHERE order_id = ? AND user_id = 8802 AND recipe_id = 101",
                Long.class, orderId);
        assertEquals(0L, itemCount, "取消即删行");
        assertEquals(3, currentVersion(orderId));
    }

    @Test
    @DisplayName("version 冲突：用过期 version 写入返回业务码 409，落库 version 不变")
    void itemWrite_versionConflict_409() throws Exception {
        long orderId = createOrder(creatorToken, "并发单", "[101,102]");
        joinOrder(joinerToken, orderId);

        // 第一次写成功：version 0→1
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));

        // 第二次写仍携带旧 version=0 → 409（模拟并发窗口）
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单已被他人更新，请刷新后重试"));
        assertEquals(1, currentVersion(orderId), "冲突写不得改变服务端 version");

        // 携带最新 version=1 重放成功
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":1}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, currentVersion(orderId));

        // 删除接口同样受 version 闸门保护
        mockMvc.perform(delete(BASE + "/{orderId}/items/{recipeId}?version=0", orderId, 102)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("选菜校验：非成员拒绝 / 菜不在本单菜单拒绝 / 重复勾选拒绝 / 结束状态写拒绝")
    void itemWrite_guards() throws Exception {
        long orderId = createOrder(creatorToken, "校验单", "[101]");
        String token = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);

        // 非成员勾选（joiner 未加入）
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅订单成员可以选菜"));

        // 菜不在本单菜单（102 未被编排进菜单）
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该菜不在本单菜单内"));

        // 重复勾选（uk order+recipe+user）
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, currentVersion(orderId));
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"note\":\"again\",\"version\":1}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("你已选过这道菜，可直接修改备注"));

        // 结束状态写拒绝：直插 FINISHED 单 + 成员，尝试勾选
        jdbcTemplate.update(
                "INSERT INTO dining_order(id, title, creator_user_id, status, join_token, version) "
                        + "VALUES (9901, '结束单', 8801, 'FINISHED', 'done-token', 7)");
        jdbcTemplate.update("INSERT INTO dining_order_menu(order_id, recipe_id) VALUES (9901, 101)");
        mockMvc.perform(post(BASE + "/{orderId}/items", 9901)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":7}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，不能修改选菜"));
        assertEquals(7, currentVersion(9901), "结束状态写拒绝不得递增 version");
    }

    // ==================== 结束点单与购买清单 ====================

    @Test
    @DisplayName("结束点单：仅发起者；聚合食材并集 + 按菜视图写快照，status→FINISHED、记录 finished_at")
    void finish_success_writesSnapshot() throws Exception {
        long orderId = createOrder(creatorToken, "结束单", "[101,102]");
        joinOrder(joinerToken, orderId);

        // 发起者选 101（带备注），加入者选 102（无备注）
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"note\":\"多放葱\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":1}"))
                .andExpect(jsonPath("$.code").value(200));

        String snapshot = finishAndGetSnapshot(orderId, creatorToken);
        JsonNode snapshotNode = objectMapper.readTree(snapshot);
        // 按食材：两道菜食材并集（西红柿、鸡蛋、葱 + 黄瓜、蒜）
        JsonNode ingredients = snapshotNode.path("ingredientNames");
        assertEquals(5, ingredients.size());
        assertEquals("西红柿", ingredients.get(0).asText());
        assertEquals("鸡蛋", ingredients.get(1).asText());
        assertEquals("葱", ingredients.get(2).asText());
        assertEquals("黄瓜", ingredients.get(3).asText());
        assertEquals("蒜", ingredients.get(4).asText());
        // 按菜视图：仅含被勾选的菜，且带选菜人备注
        JsonNode perRecipe = snapshotNode.path("perRecipe");
        assertEquals(2, perRecipe.size());
        assertEquals("番茄炒蛋", perRecipe.get(0).path("recipeName").asText());
        assertEquals("多放葱", perRecipe.get(0).path("selectors").get(0).path("note").asText());
        assertEquals("凉拌黄瓜", perRecipe.get(1).path("recipeName").asText());

        // 落库核对：状态/快照/finished_at 一致；结束不改变 version（version 只由选菜变更驱动）
        assertEquals("FINISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));
        assertEquals(snapshot, jdbcTemplate.queryForObject(
                "SELECT shopping_list FROM dining_order WHERE id = ?", String.class, orderId));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT finished_at FROM dining_order WHERE id = ?", LocalDateTime.class, orderId));
        assertEquals(2, currentVersion(orderId));
    }

    @Test
    @DisplayName("结束点单：重复调用幂等——第二次返回同一快照，finished_at 不变")
    void finish_idempotent_repeat() throws Exception {
        long orderId = createOrder(creatorToken, "幂等单", "[101]");
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));

        String first = finishAndGetSnapshot(orderId, creatorToken);
        LocalDateTime firstFinishedAt = jdbcTemplate.queryForObject(
                "SELECT finished_at FROM dining_order WHERE id = ?", LocalDateTime.class, orderId);
        String second = finishAndGetSnapshot(orderId, creatorToken);
        LocalDateTime secondFinishedAt = jdbcTemplate.queryForObject(
                "SELECT finished_at FROM dining_order WHERE id = ?", LocalDateTime.class, orderId);

        assertEquals(first, second, "重复结束必须返回同一快照");
        assertEquals(firstFinishedAt, secondFinishedAt, "重复结束不得刷新 finished_at");
    }

    @Test
    @DisplayName("结束点单：无人选菜也正常结束，快照为空清单")
    void finish_noSelections_emptySnapshot() throws Exception {
        long orderId = createOrder(creatorToken, "空选单", "[101]");

        String snapshot = finishAndGetSnapshot(orderId, creatorToken);
        JsonNode snapshotNode = objectMapper.readTree(snapshot);
        assertTrue(snapshotNode.path("ingredientNames").isEmpty());
        assertTrue(snapshotNode.path("perRecipe").isEmpty());
        assertEquals("FINISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));
    }

    @Test
    @DisplayName("结束点单：非发起者拒绝；结束后勾选/改备注/取消三类写接口均返回明确业务错误")
    void finish_guardsAndFinishedWriteRejection() throws Exception {
        long orderId = createOrder(creatorToken, "守卫单", "[101]");
        joinOrder(joinerToken, orderId);

        // 非发起者结束：拒绝
        mockMvc.perform(post(BASE + "/{id}/finish", orderId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅发起者可结束点单"));
        assertEquals("ONGOING", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));

        // 发起者结束
        mockMvc.perform(post(BASE + "/{id}/finish", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200));

        int version = currentVersion(orderId);
        // 已结束后：勾选 / 改备注 / 取消 三类写接口均返回明确业务错误
        mockMvc.perform(post(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":" + version + "}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，不能修改选菜"));
        mockMvc.perform(put(BASE + "/{orderId}/items", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"note\":\"x\",\"version\":" + version + "}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，不能修改选菜"));
        mockMvc.perform(delete(BASE + "/{orderId}/items/{recipeId}?version=" + version, orderId, 101)
                        .header("Authorization", "Bearer " + creatorToken))
                        .andExpect(jsonPath("$.code").value(201))
                        .andExpect(jsonPath("$.message").value("点单已结束，不能修改选菜"));
        assertEquals(version, currentVersion(orderId), "结束状态写拒绝不得递增 version");
    }

    // ==================== 站内信事件接入（12 号票） ====================

    @Test
    @DisplayName("加入事件：DINING_JOIN 定向通知落库——接收人=发起者（不含操作人），深链指向订单详情")
    void join_publishesDiningJoinNotice_toCreatorExcludingOperator() throws Exception {
        long orderId = createOrder(creatorToken, "加入通知单", "[101]");
        String token = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);

        joinOrder(joinerToken, orderId);

        // 仅发起者收到（操作人不含），深链携带订单 ID 与加入凭证
        Long creatorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice WHERE type = 'DINING_JOIN' AND user_id = 8801", Long.class);
        assertEquals(1L, creatorCount, "发起者必须收到加入通知");
        Long operatorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice WHERE type = 'DINING_JOIN' AND user_id = 8802", Long.class);
        assertEquals(0L, operatorCount, "操作人不得收到自己加入的通知");
        String deepLink = jdbcTemplate.queryForObject(
                "SELECT deep_link FROM notice WHERE type = 'DINING_JOIN' AND user_id = 8801", String.class);
        assertEquals("smartwash://dining/order/" + orderId + "?t=" + token, deepLink);
    }

    @Test
    @DisplayName("加入事件：既有参与者同样收到——接收人=发起者+既有参与者（不含新操作人）")
    void join_publishesToCreatorAndExistingParticipant() throws Exception {
        long orderId = createOrder(creatorToken, "多人加入单", "[101]");
        joinOrder(joinerToken, orderId);
        String thirdToken = extraUserToken(8804, "13900000004");

        // 第三人加入：发起者 + 既有参与者（102）收到，第三人不含
        joinOrder(thirdToken, orderId);

        List<Long> receivers = jdbcTemplate.queryForList(
                "SELECT user_id FROM notice WHERE type = 'DINING_JOIN' AND title = ? ORDER BY user_id",
                Long.class, "有人加入了「多人加入单」");
        // 第一次加入（102）发给发起者 1 条；第二次加入发给发起者+102 各 1 条 → 发起者 2 条、102 1 条
        assertEquals(java.util.List.of(8801L, 8801L, 8802L), receivers,
                "接收人范围必须是发起者+既有参与者，且不含操作人");
    }

    @Test
    @DisplayName("结束事件：DINING_FINISH 定向通知落库——接收人=全体成员，深链指向订单详情")
    void finish_publishesDiningFinishNotice_toAllMembers() throws Exception {
        long orderId = createOrder(creatorToken, "结束通知单", "[101]");
        joinOrder(joinerToken, orderId);
        String token = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);

        finishAndGetSnapshot(orderId, creatorToken);

        List<Long> receivers = jdbcTemplate.queryForList(
                "SELECT user_id FROM notice WHERE type = 'DINING_FINISH' AND title = ? ORDER BY user_id",
                Long.class, "「结束通知单」已结束点单");
        assertEquals(java.util.List.of(8801L, 8802L), receivers, "结束通知必须送达全体成员");
        String deepLink = jdbcTemplate.queryForObject(
                "SELECT deep_link FROM notice WHERE type = 'DINING_FINISH' AND user_id = 8801", String.class);
        assertEquals("smartwash://dining/order/" + orderId + "?t=" + token, deepLink);
    }

    @Test
    @DisplayName("结束事件：重复结束幂等——不重复发送 DINING_FINISH 通知")
    void finish_idempotent_doesNotDuplicateNotice() throws Exception {
        long orderId = createOrder(creatorToken, "幂等结束通知单", "[101]");
        joinOrder(joinerToken, orderId);

        finishAndGetSnapshot(orderId, creatorToken);
        finishAndGetSnapshot(orderId, creatorToken);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice WHERE type = 'DINING_FINISH' AND title = ?",
                Long.class, "「幂等结束通知单」已结束点单");
        assertEquals(2L, count, "重复结束不得重复通知（幂等返回路径不发布）");
    }

    // ==================== 辅助 ====================

    private String finishAndGetSnapshot(long orderId, String token) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/{id}/finish", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("shoppingList").asText();
    }

    private String notMemberToken() {
        jdbcTemplate.update(
                "INSERT INTO users(user_id, school_id, phone_number, password) VALUES (8803, 1, '13900000003', 'pwd')");
        return jwtUtil.generatorToken("user-13900000003");
    }

    /** 追加测试用户并签发 token（ID/手机号由调用方指定，cleanAll 统一清理） */
    private String extraUserToken(long userId, String phone) {
        jdbcTemplate.update(
                "INSERT INTO users(user_id, school_id, phone_number, password) VALUES (?, 1, ?, 'pwd')",
                userId, phone);
        return jwtUtil.generatorToken("user-" + phone);
    }

    private long createOrder(String token, String title, String recipeIdsJson) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"recipeIds\":" + recipeIdsJson + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
    }

    private String createOrderAndReturnToken(String token, String title, String recipeIdsJson) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"recipeIds\":" + recipeIdsJson + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("joinToken").asText();
    }

    /** 仅存一单时的订单 ID（brief 流程用） */
    private long currentOnlyOrderId() {
        return jdbcTemplate.queryForObject("SELECT id FROM dining_order LIMIT 1", Long.class);
    }

    private void joinOrder(String token, long orderId) throws Exception {
        String joinToken = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);
        mockMvc.perform(post(BASE + "/{id}/join", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinToken\":\"" + joinToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    private JsonNode getList(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int currentVersion(long orderId) {
        Integer version = jdbcTemplate.queryForObject(
                "SELECT version FROM dining_order WHERE id = ?", Integer.class, orderId);
        return version == null ? -1 : version;
    }

    /** 订单本单菜单的菜谱 ID 列表（按菜单行 ID 升序，即创建/复用时的编排顺序） */
    private List<Long> menuRecipeIds(long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT recipe_id FROM dining_order_menu WHERE order_id = ? ORDER BY id",
                Long.class, orderId);
    }
}
