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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 点单订单用户端集成测试（test profile：H2 + mock Redis，对齐 DiningCategoryAdminControllerIntegrationTest 先例）。
 * 走真实服务层 + H2，覆盖验收路径：
 * 1. 权限：/web/auth/dining/orders 由 /web/auth/** 通配保护——匿名 401；
 * 2. 创建：菜谱上架校验（不存在/下架拒绝）、创建者即首个成员、join_token 生成、fromOrderId 复用；
 * 3. version 轮询协议：knownVersion 一致轻量（unchanged=true），不一致全量；
 * 4. 乐观并发：草稿菜单 version 冲突返回业务码 409，每次变更 version 递增；
 * 5. submit：DRAFT→FINISHED 聚合本单菜单写快照、通知全体成员、幂等；
 * 6. join 三类失败：token 错误 / 订单已结束 / 重复加入；
 * 7. 列表摘要：menuCount + ingredientSummary（批量组装）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("点单订单用户端集成测试（权限/创建/轮询/并发/提交/加入/列表摘要）")
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

        // 用户（发起者/加入者）+ 分类 + 菜谱：101/102 上架，103 下架，104 用于摘要截断
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (8801, 1, ?, 'pwd')", CREATOR_PHONE);
        jdbcTemplate.update("INSERT INTO users(user_id, school_id, phone_number, password) VALUES (8802, 1, ?, 'pwd')", JOINER_PHONE);
        jdbcTemplate.update("INSERT INTO dining_category(id, name, sort) VALUES (11, '家常菜', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, ingredients, status) "
                + "VALUES (101, 11, '番茄炒蛋', '[\"西红柿\",\"鸡蛋\",\"葱\"]', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, ingredients, status) "
                + "VALUES (102, 11, '凉拌黄瓜', '[\"黄瓜\",\"蒜\"]', 1)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, status) VALUES (103, 11, '下架菜', 0)");
        jdbcTemplate.update("INSERT INTO dining_recipe(id, category_id, name, ingredients, status) "
                + "VALUES (104, 11, '大杂烩', '[\"西红柿\",\"鸡蛋\",\"葱\",\"黄瓜\",\"蒜\",\"辣椒\",\"花椒\"]', 1)");

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
        jdbcTemplate.update("DELETE FROM dining_recipe WHERE id IN (101, 102, 103, 104)");
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
    @DisplayName("创建草稿：空标题空菜单也落 DRAFT（返回 orderId+token），创建者即首个成员，菜单可空")
    void create_emptyDraft_fullFlow() throws Exception {
        // 完全空请求体 → 空草稿
        MvcResult created = mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        long orderId = data.path("orderId").asLong();
        String joinToken = data.path("joinToken").asText();
        assertTrue(orderId > 0);
        assertTrue(joinToken.length() >= 16, "草稿创建即生成 join_token（草稿期可邀请）");

        // 落库核对：DRAFT / title 空串 / 空菜单 / version=0 / 创建者即首个成员
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));
        assertEquals("", jdbcTemplate.queryForObject(
                "SELECT title FROM dining_order WHERE id = ?", String.class, orderId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_menu WHERE order_id = ?", Long.class, orderId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_member WHERE order_id = ? AND user_id = 8801",
                Long.class, orderId), "创建者必须自动成为首个成员");
        assertEquals(0, currentVersion(orderId));

        // 带标题+菜单创建：同样 DRAFT，菜单逐条落库（先把上一张空草稿置 CANCELLED 以避开唯一闸门）
        cancelAsCreator(orderId);
        long fullId = createDraft(creatorToken, "周五晚火锅", "[101,102]");
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, fullId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_menu WHERE order_id = ?", Long.class, fullId));
    }

    @Test
    @DisplayName("创建：菜谱不存在/已下架拒绝（空菜单不受上架校验影响）")
    void create_recipeGuards() throws Exception {
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

        // 标题超长（@Valid 校验）
        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "长".repeat(65) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("订单标题过长")));
    }

    @Test
    @DisplayName("创建：fromOrderId 复用历史菜单（成员身份才可复用；复用菜谱仍须上架）")
    void create_fromOrderId_reuse() throws Exception {
        // 发起者建单（菜单 101/102），先提交使其离开「进行中」——再来一单不受唯一闸门限制
        long sourceOrderId = createDraft(creatorToken, "源订单", "[101,102]");
        submitAndGetSnapshot(sourceOrderId, creatorToken, currentVersion(sourceOrderId));
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

    // ==================== 草稿协同点单（DRAFT 状态机） ====================

    @Test
    @DisplayName("草稿全链路：空草稿 → submit 拒绝空标题/空菜单 → 加菜幂等 → 移除幂等 → submit 提交 → detail status=FINISHED")
    void draft_submit_fullFlow() throws Exception {
        // 空草稿（无标题无菜单）
        MvcResult created = mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));

        // submit 空标题 → 拒绝
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("订单标题不能为空"));

        // 空菜单 → 拒绝（服务端二次校验）
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"周五晚火锅\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("本单菜单不能为空，无法提交点单"));

        // 加菜 101（version 0→1），重复加菜按幂等成功且不递增 version
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, currentVersion(orderId));
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":101,\"version\":1}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, currentVersion(orderId), "幂等加菜不得递增 version");

        // 移除 101（version 1→2），重复移除按幂等成功且不递增 version
        mockMvc.perform(delete(BASE + "/{id}/dishes/{recipeId}?version=1", orderId, 101)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, currentVersion(orderId));
        mockMvc.perform(delete(BASE + "/{id}/dishes/{recipeId}?version=2", orderId, 101)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, currentVersion(orderId), "幂等移除不得递增 version");

        // 成员加入草稿后同样可加菜（边点边邀）
        joinOrder(joinerToken, orderId);
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":2}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(3, currentVersion(orderId));

        // submit 提交（携带最新 version）
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"周五晚火锅\",\"version\":3}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("FINISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));
        assertEquals("周五晚火锅", jdbcTemplate.queryForObject(
                "SELECT title FROM dining_order WHERE id = ?", String.class, orderId));
        assertEquals(4, currentVersion(orderId), "提交成功须递增 version，阻断并发菜单写穿透");

        // detail 返回 status
        mockMvc.perform(get(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.title").value("周五晚火锅"));
    }

    @Test
    @DisplayName("submit 守卫：非成员拒绝 / version 冲突 409 / 重复提交幂等")
    void submit_guards() throws Exception {
        long orderId = createDraft(creatorToken, "守卫草稿", "[101]");

        // 非成员（未加入的 joiner）
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅订单成员可提交点单"));

        // version 冲突：先加菜把 version 推到 1，再用旧 version=0 提交 → 409
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"守卫草稿\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单已被他人更新，请刷新后重试"));

        // 携带最新 version 提交成功；再次提交 → 幂等返回原快照（200）
        String first = submitAndGetSnapshot(orderId, creatorToken, 1);
        String second = submitAndGetSnapshot(orderId, creatorToken, 2);
        assertEquals(first, second, "重复提交必须返回同一快照");
    }

    @Test
    @DisplayName("加菜/移除守卫：非 DRAFT 拒绝 / 非成员拒绝 / version 冲突 409 / version 缺失")
    void draft_dish_guards() throws Exception {
        // 非 DRAFT（已提交）
        long finishedId = createFinishedOrder(creatorToken, "已提交单", "[101]");
        mockMvc.perform(post(BASE + "/{id}/dishes", finishedId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":1}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，不能编辑菜单"));
        mockMvc.perform(delete(BASE + "/{id}/dishes/{recipeId}?version=1", finishedId, 101)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，不能编辑菜单"));

        // 非成员
        long orderId = createDraft(creatorToken, "成员守卫单", "[101]");
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅订单成员可以加菜"));

        // version 缺失
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("version")));

        // version 冲突
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":99}"))
                .andExpect(jsonPath("$.code").value(409));
        assertEquals(0, currentVersion(orderId), "冲突写不得递增 version");

        // 移除接口 version 缺失按业务失败返回
        mockMvc.perform(delete(BASE + "/{id}/dishes/{recipeId}", orderId, 101)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("已知版本号 version 不能为空"));
    }

    @Test
    @DisplayName("放弃草稿：仅发起者 DRAFT→CANCELLED（订单与子表保留），参与者列表仍可见「已取消」")
    void draft_cancel_flow() throws Exception {
        long orderId = createDraft(creatorToken, "待弃草稿", "[101]");
        joinOrder(joinerToken, orderId);
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_member WHERE order_id = ?", Long.class, orderId));

        // 非发起者拒绝
        mockMvc.perform(delete(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅发起者可放弃草稿"));

        // 发起者放弃：订单行保留，状态置 CANCELLED + cancelled_at；菜单/成员子表保留
        mockMvc.perform(delete(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order WHERE id = ? AND cancelled_at IS NOT NULL",
                Long.class, orderId));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_menu WHERE order_id = ?", Long.class, orderId));
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_member WHERE order_id = ?", Long.class, orderId));

        // 参与者「我的订单」仍能看到该单（灰态数据源），status=CANCELLED
        JsonNode records = getList(BASE + "/my", joinerToken);
        boolean foundCancelled = false;
        for (JsonNode node : records) {
            if (node.path("id").asLong() == orderId && "CANCELLED".equals(node.path("status").asText())) {
                foundCancelled = true;
            }
        }
        assertTrue(foundCancelled, "参与者列表应保留已取消单");

        // 再次放弃 → 提示已取消
        mockMvc.perform(delete(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已取消，无需重复操作"));

        // 非 DRAFT（已提交）拒绝
        long finishedId = createFinishedOrder(creatorToken, "不可弃单", "[101]");
        mockMvc.perform(delete(BASE + "/{id}", finishedId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅草稿可放弃"));
    }

    @Test
    @DisplayName("唯一性：同一发起者已有进行中 DRAFT 时 create 拒绝（40901）；提交后可再建")
    void create_unique_active_draft_guard() throws Exception {
        long firstId = createDraft(creatorToken, "进行中单", "[101]");

        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"第二单\",\"recipeIds\":[102]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("进行中单")));

        // 提交后不再受限 → 可再建
        mockMvc.perform(post(BASE + "/{id}/submit", firstId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"进行中单\",\"version\":" + currentVersion(firstId) + "}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post(BASE + "/create")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"第二单\",\"recipeIds\":[102]}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("我的进行中点单 /active：本人 DRAFT 命中；FINISHED 不命中")
    void my_active_order() throws Exception {
        long draftId = createDraft(creatorToken, "活跃草稿", "[101]");

        mockMvc.perform(get(BASE + "/active").header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(draftId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        // 提交后 active 为空
        mockMvc.perform(post(BASE + "/{id}/submit", draftId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"活跃草稿\",\"version\":" + currentVersion(draftId) + "}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get(BASE + "/active").header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("退出点单：参与者退出成功并可重复幂等；发起者/已结束/已取消拒绝")
    void leave_flow() throws Exception {
        long orderId = createDraft(creatorToken, "协作单", "[101]");
        joinOrder(joinerToken, orderId);

        mockMvc.perform(post(BASE + "/{id}/leave", orderId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dining_order_member WHERE order_id = ?", Long.class, orderId));
        // 幂等：再次退出直接成功
        mockMvc.perform(post(BASE + "/{id}/leave", orderId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(jsonPath("$.code").value(200));
        // 发起者请走放弃
        mockMvc.perform(post(BASE + "/{id}/leave", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("发起者不能退出，如需结束请使用放弃草稿"));

        // 已结束 / 已取消拒绝（先把上一张推入终态，避开唯一闸门）
        mockMvc.perform(delete(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200));
        long finishedId = createFinishedOrder(creatorToken, "已结束单", "[101]");
        mockMvc.perform(post(BASE + "/{id}/leave", finishedId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(jsonPath("$.message").value("点单已结束，无法退出"));

        long cancelledId = createDraft(creatorToken, "已取消单", "[101]");
        mockMvc.perform(delete(BASE + "/{id}", cancelledId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post(BASE + "/{id}/leave", cancelledId)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(jsonPath("$.message").value("点单已取消，无法退出"));
    }

    @Test
    @DisplayName("我的订单列表：DRAFT 草稿带 status=DRAFT；加入 DRAFT 后同样出现在我的列表")
    void my_draft_statusAndJoin() throws Exception {
        long draftId = createDraft(creatorToken, "草稿单", "[101]");
        joinOrder(joinerToken, draftId);

        JsonNode creatorList = getList(BASE + "/my", creatorToken);
        assertEquals("DRAFT", creatorList.get(0).path("status").asText());
        JsonNode joinerList = getList(BASE + "/my", joinerToken);
        assertEquals("DRAFT", joinerList.get(0).path("status").asText(), "草稿期加入的成员同样能在列表看到草稿");

        // 列表中的草稿可被放弃；放弃后置 CANCELLED，仍留在「我的订单」供识别（发起者/参与者均可见）
        mockMvc.perform(delete(BASE + "/{id}", draftId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(200));
        JsonNode afterCancel = getList(BASE + "/my", creatorToken);
        assertEquals(1, afterCancel.size());
        assertEquals("CANCELLED", afterCancel.get(0).path("status").asText());
    }

    // ==================== 我的订单列表 ====================

    @Test
    @DisplayName("我的订单列表：我发起 + 我参与都出现，isCreator 标记正确")
    void myOrders_creatorAndJoiner() throws Exception {
        long orderId = createDraft(creatorToken, "周五晚火锅", "[101,102]");
        joinOrder(joinerToken, orderId);
        submitAndGetSnapshot(orderId, creatorToken, currentVersion(orderId));

        // 发起者视角
        JsonNode creatorList = getList(BASE + "/my", creatorToken);
        assertEquals(1, creatorList.size());
        assertEquals("周五晚火锅", creatorList.get(0).path("title").asText());
        assertTrue(creatorList.get(0).path("isCreator").asBoolean());

        // 参与者视角
        JsonNode joinerList = getList(BASE + "/my", joinerToken);
        assertEquals(1, joinerList.size());
        assertTrue(joinerList.get(0).path("isCreator").asBoolean() == false);
        assertEquals("FINISHED", joinerList.get(0).path("status").asText());
    }

    @Test
    @DisplayName("我的订单列表：摘要字段 menuCount + ingredientSummary（食材并集，超 6 个截断加省略号）")
    void myOrders_summaryFields() throws Exception {
        long firstId = createDraft(creatorToken, "两道菜", "[101,102]");
        cancelAsCreator(firstId);
        createDraft(creatorToken, "大杂烩", "[104]");

        JsonNode records = getList(BASE + "/my", creatorToken);
        assertEquals(2, records.size());
        // 倒序：后创建的大杂烩在前
        assertEquals(1, records.get(0).path("menuCount").asInt());
        assertEquals("西红柿、鸡蛋、葱、黄瓜、蒜、辣椒…", records.get(0).path("ingredientSummary").asText());
        assertEquals(2, records.get(1).path("menuCount").asInt());
        assertEquals("西红柿、鸡蛋、葱、黄瓜、蒜", records.get(1).path("ingredientSummary").asText());
    }

    // ==================== 详情轮询协议 ====================

    @Test
    @DisplayName("详情轮询：knownVersion 一致 → unchanged=true 轻量；不一致 → 全量（菜单/选菜/参与者）")
    void detail_pollProtocol() throws Exception {
        long orderId = createDraft(creatorToken, "周五晚火锅", "[101,102]");
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
        assertNull(data.path("shoppingList").asText(null), "草稿期的单无购买清单快照");

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
        long orderId = createDraft(creatorToken, "凭证可见单", "[101,102]");
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
        long orderId = createDraft(creatorToken, "加入单", "[101]");
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
    @DisplayName("重置凭证：仅发起者 + 仅 DRAFT；旧 token 立即失效，新 token 可用")
    void resetJoinToken_flow() throws Exception {
        long orderId = createDraft(creatorToken, "重置单", "[101]");
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

        // 已提交单重置：拒绝（先放弃当前 DRAFT 以避开唯一闸门）
        cancelAsCreator(orderId);
        long finishedId = createFinishedOrder(creatorToken, "已提交重置单", "[101]");
        mockMvc.perform(post(BASE + "/{id}/join-token/reset", finishedId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，无需重置加入凭证"));
    }

    // ==================== 提交点单与购买清单 ====================

    @Test
    @DisplayName("提交点单：仅发起者；聚合本单菜单写快照，status→FINISHED、记录 finished_at，按菜视图无选菜人")
    void submit_success_writesSnapshot() throws Exception {
        long orderId = createDraft(creatorToken, "结束单", "[101,102]");
        joinOrder(joinerToken, orderId);
        int v = currentVersion(orderId);

        // 历史选菜记录（已停止写入，仅作遗留数据）：不得影响本单菜单聚合
        jdbcTemplate.update(
                "INSERT INTO dining_order_item(order_id, recipe_id, user_id, note) VALUES (?, 101, 8802, '不要辣')",
                orderId);

        String snapshot = submitAndGetSnapshot(orderId, creatorToken, v);
        JsonNode snapshotNode = objectMapper.readTree(snapshot);
        // 按食材：两道菜食材并集（西红柿、鸡蛋、葱 + 黄瓜、蒜）
        JsonNode ingredients = snapshotNode.path("ingredientNames");
        assertEquals(5, ingredients.size());
        assertEquals("西红柿", ingredients.get(0).asText());
        assertEquals("鸡蛋", ingredients.get(1).asText());
        assertEquals("葱", ingredients.get(2).asText());
        assertEquals("黄瓜", ingredients.get(3).asText());
        assertEquals("蒜", ingredients.get(4).asText());
        // 按菜视图：本单菜单全部入选，无选菜人（遗留选菜记录不产生 selectors）
        JsonNode perRecipe = snapshotNode.path("perRecipe");
        assertEquals(2, perRecipe.size());
        assertEquals("番茄炒蛋", perRecipe.get(0).path("recipeName").asText());
        assertEquals(0, perRecipe.get(0).path("selectors").size(), "新模型无选菜人");
        assertEquals("凉拌黄瓜", perRecipe.get(1).path("recipeName").asText());
        assertEquals(0, perRecipe.get(1).path("selectors").size());
        assertTrue(!snapshot.contains("不要辣"), "遗留选菜备注不得进入购买清单");

        // 落库核对：状态/快照/finished_at 一致；提交递增 version
        assertEquals("FINISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));
        assertEquals(snapshot, jdbcTemplate.queryForObject(
                "SELECT shopping_list FROM dining_order WHERE id = ?", String.class, orderId));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT finished_at FROM dining_order WHERE id = ?", LocalDateTime.class, orderId));
        assertEquals(v + 1, currentVersion(orderId));
    }

    @Test
    @DisplayName("提交点单：重复调用幂等——第二次返回同一快照，finished_at 不变")
    void submit_idempotent_repeat() throws Exception {
        long orderId = createDraft(creatorToken, "幂等单", "[101]");

        String first = submitAndGetSnapshot(orderId, creatorToken, currentVersion(orderId));
        LocalDateTime firstFinishedAt = jdbcTemplate.queryForObject(
                "SELECT finished_at FROM dining_order WHERE id = ?", LocalDateTime.class, orderId);
        String second = submitAndGetSnapshot(orderId, creatorToken, currentVersion(orderId));
        LocalDateTime secondFinishedAt = jdbcTemplate.queryForObject(
                "SELECT finished_at FROM dining_order WHERE id = ?", LocalDateTime.class, orderId);

        assertEquals(first, second, "重复提交必须返回同一快照");
        assertEquals(firstFinishedAt, secondFinishedAt, "重复提交不得刷新 finished_at");
    }

    @Test
    @DisplayName("提交点单：成员亦可提交；提交后加菜/移除/放弃均拒绝")
    void submit_guardsAndFinishedWriteRejection() throws Exception {
        long orderId = createDraft(creatorToken, "守卫单", "[101]");
        joinOrder(joinerToken, orderId);

        // 成员提交：成功（结束权=全体成员）
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"守卫单\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("FINISHED", jdbcTemplate.queryForObject(
                "SELECT status FROM dining_order WHERE id = ?", String.class, orderId));

        // 发起者重复提交：幂等返回（version 不变）
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"守卫单\",\"version\":0}"))
                .andExpect(jsonPath("$.code").value(200));

        int version = currentVersion(orderId);
        // 已提交后：加菜 / 移除 / 放弃 三类写接口均返回明确业务错误
        mockMvc.perform(post(BASE + "/{id}/dishes", orderId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":102,\"version\":" + version + "}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，不能编辑菜单"));
        mockMvc.perform(delete(BASE + "/{id}/dishes/{recipeId}?version=" + version, orderId, 101)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("点单已结束，不能编辑菜单"));
        mockMvc.perform(delete(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仅草稿可放弃"));
        assertEquals(version, currentVersion(orderId), "结束状态写拒绝不得递增 version");
    }

    // ==================== 站内信事件接入（12 号票） ====================

    @Test
    @DisplayName("加入事件：DINING_JOIN 定向通知落库——接收人=发起者（不含操作人），深链指向订单详情")
    void join_publishesDiningJoinNotice_toCreatorExcludingOperator() throws Exception {
        long orderId = createDraft(creatorToken, "加入通知单", "[101]");
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
        long orderId = createDraft(creatorToken, "多人加入单", "[101]");
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
    @DisplayName("提交事件：DINING_FINISH 定向通知落库——接收人=全体成员，深链指向订单详情")
    void submit_publishesDiningFinishNotice_toAllMembers() throws Exception {
        long orderId = createDraft(creatorToken, "结束通知单", "[101]");
        joinOrder(joinerToken, orderId);
        String token = jdbcTemplate.queryForObject(
                "SELECT join_token FROM dining_order WHERE id = ?", String.class, orderId);

        submitAndGetSnapshot(orderId, creatorToken, currentVersion(orderId));

        List<Long> receivers = jdbcTemplate.queryForList(
                "SELECT user_id FROM notice WHERE type = 'DINING_FINISH' AND title = ? ORDER BY user_id",
                Long.class, "「结束通知单」已结束点单");
        assertEquals(java.util.List.of(8801L, 8802L), receivers, "提交通知必须送达全体成员");
        String deepLink = jdbcTemplate.queryForObject(
                "SELECT deep_link FROM notice WHERE type = 'DINING_FINISH' AND user_id = 8801", String.class);
        assertEquals("smartwash://dining/order/" + orderId + "?t=" + token, deepLink);
    }

    @Test
    @DisplayName("提交事件：重复提交幂等——不重复发送 DINING_FINISH 通知")
    void submit_idempotent_doesNotDuplicateNotice() throws Exception {
        long orderId = createDraft(creatorToken, "幂等结束通知单", "[101]");
        joinOrder(joinerToken, orderId);

        submitAndGetSnapshot(orderId, creatorToken, currentVersion(orderId));
        submitAndGetSnapshot(orderId, creatorToken, currentVersion(orderId));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice WHERE type = 'DINING_FINISH' AND title = ?",
                Long.class, "「幂等结束通知单」已结束点单");
        assertEquals(2L, count, "重复提交不得重复通知（幂等返回路径不发布）");
    }

    // ==================== 辅助 ====================

    private String submitAndGetSnapshot(long orderId, String token, long version) throws Exception {
        // 不传标题：沿用库内标题（与端上「草稿页已编辑标题」口径一致）
        MvcResult result = mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
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

    /** 创建草稿（DRAFT，未提交） */
    /** 发起者放弃草稿（cancel→CANCELLED）：既有测试在需要再建第二张 DRAFT 前，用她把上一张推入终态 */
    private void cancelAsCreator(long orderId) throws Exception {
        mockMvc.perform(delete(BASE + "/{id}", orderId)
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private long createDraft(String token, String title, String recipeIdsJson) throws Exception {
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

    /** 创建并提交（FINISHED）：create + submit，返回 orderId */
    private long createFinishedOrder(String token, String title, String recipeIdsJson) throws Exception {
        long orderId = createDraft(token, title, recipeIdsJson);
        mockMvc.perform(post(BASE + "/{id}/submit", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"version\":" + currentVersion(orderId) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return orderId;
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("records");
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
