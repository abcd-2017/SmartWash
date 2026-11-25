package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.entity.DiningOrderItem;
import com.smartwash.dining.entity.DiningOrderMember;
import com.smartwash.dining.entity.DiningOrderMenu;
import com.smartwash.dining.from.AddDiningOrderDishFrom;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.from.SubmitDiningOrderFrom;
import com.smartwash.dining.mapper.DiningOrderItemMapper;
import com.smartwash.dining.mapper.DiningOrderMapper;
import com.smartwash.dining.mapper.DiningOrderMemberMapper;
import com.smartwash.dining.mapper.DiningOrderMenuMapper;
import com.smartwash.dining.service.DiningRecipeGate;
import com.smartwash.dining.stream.DiningOrderStreamer;
import com.smartwash.dining.vo.DiningOrderActiveVo;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.BaseSearchFrom;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.notice.constant.NoticeType;
import com.smartwash.notice.service.NoticeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

/**
 * 点单订单服务测试（纯 Mockito，对齐 DiningCategoryServiceImplTest 先例）。
 * 覆盖外部行为契约：创建（创建者即成员/菜单写入/fromOrderId 复用与参数冲突/上架把关透传）、
 * 加入三类失败与 uk 兜底、重置凭证权限、详情轮询协议（unchanged 轻量 vs 全量组装）、brief、列表合并。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiningOrderServiceImpl 点单订单测试")
class DiningOrderServiceImplTest {

    private static final Long CREATOR = 101L;
    private static final Long JOINER = 102L;
    private static final Long ORDER_ID = 900L;

    @Mock
    private DiningOrderMapper orderMapper;

    @Mock
    private DiningOrderMemberMapper memberMapper;

    @Mock
    private DiningOrderMenuMapper menuMapper;

    @Mock
    private DiningOrderItemMapper itemMapper;

    @Mock
    private DiningRecipeGate recipeGate;

    @Mock
    private UsersMapper usersMapper;

    @Mock
    private NoticeService noticeService;

    @Mock
    private com.smartwash.config.MinioConfig minioConfig;

    @Mock
    private DiningOrderStreamer streamer;

    @Mock
    private com.smartwash.dining.mapper.DiningOrderInviteMapper inviteMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private com.smartwash.dining.service.DietaryPreferenceService dietaryPreferenceService;

    private DiningOrderServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DiningOrder.class);
        TableInfoHelper.initTableInfo(assistant, DiningOrderMember.class);
        TableInfoHelper.initTableInfo(assistant, DiningOrderMenu.class);
        TableInfoHelper.initTableInfo(assistant, DiningOrderItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new DiningOrderServiceImpl(memberMapper, menuMapper, itemMapper, recipeGate, usersMapper,
                orderMapper, noticeService, minioConfig, streamer, inviteMapper, redisTemplate,
                dietaryPreferenceService);
        // 成员忌口聚合：默认空 Map（无忌口），避免详情组装 NPE
        when(dietaryPreferenceService.getAllergensByUserIds(any())).thenReturn(java.util.Map.of());
        // 快照封面直链：mock 按「端点/桶/键」拼（测试只断言字段存在与格式）
        when(minioConfig.urlOf(any())).thenAnswer(inv -> "http://minio.local/smartwash/" + inv.getArgument(0));
        // 在线状态批量读：mock ValueOperations.multiGet 返回空（无在线成员），避免 opsForValue() 为 null
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(any())).thenReturn(java.util.List.of());
        ReflectionTestUtils.setField(service, "baseMapper", orderMapper);
    }

    private DiningOrder order(Long id, String status, String token, Integer version, Long creator) {
        return new DiningOrder().setId(id).setTitle("周五晚火锅").setStatus(status)
                .setJoinToken(token).setVersion(version).setCreatorUserId(creator);
    }

    private CreateDiningOrderFrom createFrom(String title, List<Long> recipeIds, Long fromOrderId) {
        CreateDiningOrderFrom from = new CreateDiningOrderFrom();
        from.setTitle(title);
        from.setRecipeIds(recipeIds);
        from.setFromOrderId(fromOrderId);
        return from;
    }

    // ==================== 创建 ====================

    @Test
    @DisplayName("创建：写入 DRAFT 草稿（随机 token/version=0），创建者即首个成员，菜单逐条落库")
    void create_success_writesOrderMemberAndMenu() {
        when(orderMapper.insert(any(DiningOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, DiningOrder.class).setId(ORDER_ID);
            return 1;
        });
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);
        when(menuMapper.insert(any(DiningOrderMenu.class))).thenReturn(1);

        var vo = service.create(createFrom("周五晚火锅", List.of(1L, 2L, 1L), null), CREATOR);

        assertEquals(ORDER_ID, vo.getOrderId());
        assertNotNull(vo.getJoinToken());

        ArgumentCaptor<DiningOrder> orderCaptor = ArgumentCaptor.forClass(DiningOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        DiningOrder saved = orderCaptor.getValue();
        assertEquals("DRAFT", saved.getStatus());
        assertEquals(0, saved.getVersion());
        assertNotNull(saved.getJoinToken());

        // 创建者即首个成员
        ArgumentCaptor<DiningOrderMember> memberCaptor = ArgumentCaptor.forClass(DiningOrderMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertEquals(CREATOR, memberCaptor.getValue().getUserId());

        // 入参重复菜谱去重保序：1,2,1 → 只落 1,2
        ArgumentCaptor<DiningOrderMenu> menuCaptor = ArgumentCaptor.forClass(DiningOrderMenu.class);
        verify(menuMapper, org.mockito.Mockito.times(2)).insert(menuCaptor.capture());
        assertEquals(List.of(1L, 2L),
                menuCaptor.getAllValues().stream().map(DiningOrderMenu::getRecipeId).toList());
    }

    @Test
    @DisplayName("创建：标题与菜单均可空 → 落一张空 DRAFT（不校验上架、不写菜单行）")
    void create_emptyDraft_success() {
        when(orderMapper.insert(any(DiningOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, DiningOrder.class).setId(ORDER_ID);
            return 1;
        });
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);

        var vo = service.create(new CreateDiningOrderFrom(), CREATOR);

        assertEquals(ORDER_ID, vo.getOrderId());
        ArgumentCaptor<DiningOrder> orderCaptor = ArgumentCaptor.forClass(DiningOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertEquals("DRAFT", orderCaptor.getValue().getStatus());
        assertEquals("", orderCaptor.getValue().getTitle(), "空标题落空串（DB title NOT NULL）");
        verify(recipeGate, never()).assertAllOnShelf(any());
        verify(menuMapper, never()).insert(any(DiningOrderMenu.class));
    }

    @Test
    @DisplayName("创建：菜谱上架把关透传（gate 拒绝即整体拒绝，不落库）")
    void create_gateRejects_rejected() {
        org.mockito.Mockito.doThrow(new CustomExceptions("部分菜谱不存在或已下架，无法加入本单菜单"))
                .when(recipeGate).assertAllOnShelf(any());

        expectBusinessException("部分菜谱不存在或已下架，无法加入本单菜单",
                () -> service.create(createFrom("周五晚火锅", List.of(1L, 404L), null), CREATOR));

        verify(orderMapper, never()).insert(any(DiningOrder.class));
        verify(memberMapper, never()).insert(any(DiningOrderMember.class));
    }

    @Test
    @DisplayName("创建：fromOrderId 复用历史菜单（须为该单成员），复用菜谱同样过上架把关")
    void create_fromOrderId_reusesMenu() {
        when(orderMapper.selectById(880L)).thenReturn(order(880L, "FINISHED", "t", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        DiningOrderMenu menu1 = new DiningOrderMenu().setOrderId(880L).setRecipeId(5L);
        DiningOrderMenu menu2 = new DiningOrderMenu().setOrderId(880L).setRecipeId(6L);
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(menu1, menu2));
        when(orderMapper.insert(any(DiningOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, DiningOrder.class).setId(ORDER_ID);
            return 1;
        });
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);
        when(menuMapper.insert(any(DiningOrderMenu.class))).thenReturn(1);

        service.create(createFrom("复用单", null, 880L), CREATOR);

        // 复用的菜谱同样要过上架校验（历史菜单可能含已下架菜）
        verify(recipeGate).assertAllOnShelf(List.of(5L, 6L));
    }

    @Test
    @DisplayName("创建：recipeIds 与 fromOrderId 同时给出 → 拒绝（二选一语义明确）")
    void create_conflictParams_rejected() {
        expectBusinessException("recipeIds 与 fromOrderId 只能二选一",
                () -> service.create(createFrom("标题", List.of(1L), 880L), CREATOR));
    }

    @Test
    @DisplayName("创建：fromOrderId 订单不存在 → 拒绝")
    void create_fromOrderMissing_rejected() {
        when(orderMapper.selectById(404L)).thenReturn(null);
        expectBusinessException("要复用的点单不存在",
                () -> service.create(createFrom("标题", null, 404L), CREATOR));
    }

    @Test
    @DisplayName("创建：非源订单成员不可复用历史菜单")
    void create_notMemberOfSource_rejected() {
        when(orderMapper.selectById(880L)).thenReturn(order(880L, "DRAFT", "t", 1, 999L));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        expectBusinessException("仅订单成员可复用历史菜单",
                () -> service.create(createFrom("标题", null, 880L), JOINER));
    }

    @Test
    @DisplayName("创建：显式 recipeIds 为空列表 → 合法空草稿（不再拒绝）")
    void create_emptyMenu_allowedAsDraft() {
        when(orderMapper.insert(any(DiningOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, DiningOrder.class).setId(ORDER_ID);
            return 1;
        });
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);

        assertNotNull(service.create(createFrom("标题", List.of(), null), CREATOR));
        verify(menuMapper, never()).insert(any(DiningOrderMenu.class));
    }

    // ==================== 提交点单（submit） ====================

    private SubmitDiningOrderFrom submitFrom(String title, Long version) {
        SubmitDiningOrderFrom from = new SubmitDiningOrderFrom();
        from.setTitle(title);
        from.setVersion(version);
        return from;
    }

    /** 桩：本单菜单两道菜 + 食材/菜名，供聚合断言 */
    private void mockMenuAndRecipes() {
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L),
                new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(2L)));
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(
                1L, List.of("西红柿", "鸡蛋", "葱"),
                2L, List.of("黄瓜", "蒜")));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(Map.of(
                1L, new DiningRecipeGate.RecipeBrief(1L, "番茄炒蛋", 11L, null),
                2L, new DiningRecipeGate.RecipeBrief(2L, "凉拌黄瓜", 11L, null)));
    }

    @Test
    @DisplayName("submit：DRAFT→FINISHED 条件 UPDATE 成功，聚合本单菜单写快照，标题随请求落库")
    void submit_success() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        mockMenuAndRecipes();
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        DiningOrderFinishVo vo = service.submit(ORDER_ID, submitFrom("定稿标题", 0L), CREATOR);

        assertEquals(ORDER_ID, vo.getId());
        assertEquals("FINISHED", vo.getStatus());
        assertEquals("定稿标题", vo.getTitle());
        assertNotNull(vo.getFinishedAt());
        // 菜单即已选清单：两道菜食材并集进清单，按菜视图无选菜人
        assertTrue(vo.getShoppingList().contains("西红柿"));
        assertTrue(vo.getShoppingList().contains("葱"));
        assertTrue(vo.getShoppingList().contains("黄瓜"));
        assertTrue(vo.getShoppingList().contains("番茄炒蛋"));
        assertTrue(vo.getShoppingList().contains("凉拌黄瓜"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，WHERE/SET 参数才进入参数表
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue("FINISHED"), "状态必须置为 FINISHED");
        assertTrue(params.containsValue("DRAFT"), "条件 UPDATE 必须以 DRAFT 为闸门");
        assertTrue(params.containsValue(0L), "条件 UPDATE 必须以客户端已知 version 为闸门");
        assertTrue(params.containsValue("定稿标题"), "请求携带的标题必须落库");
    }

    @Test
    @DisplayName("submit：请求不传标题则沿用库内标题")
    void submit_fallsBackToStoredTitle() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        mockMenuAndRecipes();
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.submit(ORDER_ID, submitFrom(null, 0L), CREATOR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，WHERE/SET 参数才进入参数表
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("周五晚火锅"));
    }

    @Test
    @DisplayName("submit：标题必填（库内空 + 请求空/纯空白均拒绝，且不落库）")
    void submit_blankTitle_rejected() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(
                new DiningOrder().setId(ORDER_ID).setTitle("").setStatus("DRAFT")
                        .setJoinToken("tok").setVersion(0).setCreatorUserId(CREATOR));

        expectBusinessException("订单标题不能为空", () -> service.submit(ORDER_ID, submitFrom(null, 0L), CREATOR));
        expectBusinessException("订单标题不能为空", () -> service.submit(ORDER_ID, submitFrom("   ", 0L), CREATOR));
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("submit：菜单为空拒绝（服务端二次校验，防止提交前菜单被清空）")
    void submit_emptyMenu_rejected() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        expectBusinessException("本单菜单不能为空，无法提交点单",
                () -> service.submit(ORDER_ID, submitFrom("标题", 0L), CREATOR));
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("submit：仅发起者可提交；非发起者拒绝")
    void submit_notCreator_rejected() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        expectBusinessException("仅发起者可提交点单",
                () -> service.submit(ORDER_ID, submitFrom("标题", 0L), JOINER));
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("submit：脏数据状态（非 DRAFT/FINISHED）拒绝")
    void submit_illegalStatus_rejected() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "LEGACY", "tok", 1, CREATOR));
        expectBusinessException("点单状态异常，无法提交",
                () -> service.submit(ORDER_ID, submitFrom("标题", 1L), CREATOR));
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("submit：version 冲突返回 409")
    void submit_versionConflict_409() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        mockMenuAndRecipes();
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        // 服务端已递增到 1：判定为并发冲突
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 1, CREATOR));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.submit(ORDER_ID, submitFrom("标题", 0L), CREATOR));
        assertEquals(409, ex.getCode());
        assertEquals("订单已被他人更新，请刷新后重试", ex.getMessage());
    }

    @Test
    @DisplayName("submit：并发窗口条件 UPDATE 影响 0 行但订单已被提交 → 幂等返回其快照")
    void submit_concurrentFinished_idempotent() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        mockMenuAndRecipes();
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        DiningOrder finished = order(ORDER_ID, "FINISHED", "tok", 1, CREATOR);
        finished.setShoppingList("{\"ingredientNames\":[\"西红柿\"]}");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(finished);

        DiningOrderFinishVo vo = service.submit(ORDER_ID, submitFrom("标题", 0L), CREATOR);

        assertEquals("{\"ingredientNames\":[\"西红柿\"]}", vo.getShoppingList());
        assertEquals("FINISHED", vo.getStatus());
    }

    @Test
    @DisplayName("submit：已 FINISHED 幂等返回库内原快照，不聚合不更新")
    void submit_alreadyFinished_idempotent() {
        DiningOrder finished = order(ORDER_ID, "FINISHED", "tok", 1, CREATOR);
        finished.setShoppingList("{\"ingredientNames\":[\"西红柿\"]}");
        finished.setFinishedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(finished);

        DiningOrderFinishVo vo = service.submit(ORDER_ID, submitFrom("标题", 1L), CREATOR);

        assertEquals("{\"ingredientNames\":[\"西红柿\"]}", vo.getShoppingList(), "必须返回原快照");
        assertEquals("FINISHED", vo.getStatus());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), vo.getFinishedAt());
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
        verify(menuMapper, never()).selectList(any(Wrapper.class));
        verify(recipeGate, never()).getIngredients(anyCollection());
        verify(noticeService, never()).publishTargeted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("submit：订单不存在拒绝")
    void submit_orderMissing_rejected() {
        when(orderMapper.selectForUpdate(404L)).thenReturn(null);
        expectBusinessException("点单不存在", () -> service.submit(404L, submitFrom("标题", 0L), CREATOR));
    }

    // ==================== 草稿加菜 / 移除 ====================

    private AddDiningOrderDishFrom addDishFrom(Long recipeId, Long version) {
        AddDiningOrderDishFrom from = new AddDiningOrderDishFrom();
        from.setRecipeId(recipeId);
        from.setVersion(version);
        return from;
    }

    @Test
    @DisplayName("加菜：DRAFT + 成员 + 上架 → 递增 version 后落菜单行")
    void addDish_success() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(menuMapper.insert(any(DiningOrderMenu.class))).thenReturn(1);

        service.addDish(ORDER_ID, addDishFrom(7L, 3L), CREATOR);

        verify(recipeGate).assertAllOnShelf(List.of(7L));
        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，WHERE/SET 参数才进入参数表
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(3L),
                "version 闸门必须使用客户端已知版本");
        ArgumentCaptor<DiningOrderMenu> menuCaptor = ArgumentCaptor.forClass(DiningOrderMenu.class);
        verify(menuMapper).insert(menuCaptor.capture());
        assertEquals(7L, menuCaptor.getValue().getRecipeId());
    }

    @Test
    @DisplayName("加菜：已存在按幂等成功（不重复落库、不递增 version）")
    void addDish_idempotent() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        service.addDish(ORDER_ID, addDishFrom(7L, 3L), CREATOR);

        verify(orderMapper, never()).update(any(), any(Wrapper.class));
        verify(menuMapper, never()).insert(any(DiningOrderMenu.class));
    }

    @Test
    @DisplayName("加菜：并发撞 uk_order_recipe 按幂等成功处理（不向上抛）")
    void addDish_duplicateKey_idempotent() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(menuMapper.insert(any(DiningOrderMenu.class)))
                .thenThrow(new DuplicateKeyException("uk_order_recipe"));

        service.addDish(ORDER_ID, addDishFrom(7L, 3L), CREATOR);
    }

    @Test
    @DisplayName("加菜：非 DRAFT / 非成员 / 下架菜 / version 缺失 / version 冲突均拒绝")
    void addDish_guards() {
        // 非 DRAFT（已结束）
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "FINISHED", "tok", 3, CREATOR));
        expectBusinessException("点单已结束，不能编辑菜单",
                () -> service.addDish(ORDER_ID, addDishFrom(7L, 3L), CREATOR));

        // 非成员
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        expectBusinessException("仅订单成员可以加菜",
                () -> service.addDish(ORDER_ID, addDishFrom(7L, 3L), JOINER));

        // 下架/不存在
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        org.mockito.Mockito.doThrow(new CustomExceptions("部分菜谱不存在或已下架，无法加入本单菜单"))
                .when(recipeGate).assertAllOnShelf(any());
        expectBusinessException("部分菜谱不存在或已下架，无法加入本单菜单",
                () -> service.addDish(ORDER_ID, addDishFrom(404L, 3L), CREATOR));

        // 后续用例不再受上架桩影响
        org.mockito.Mockito.doNothing().when(recipeGate).assertAllOnShelf(any());

        // version 缺失
        expectBusinessException("已知版本号 version 不能为空",
                () -> service.addDish(ORDER_ID, addDishFrom(7L, null), CREATOR));

        // version 冲突
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addDish(ORDER_ID, addDishFrom(7L, 99L), CREATOR));
        assertEquals(409, ex.getCode());
        verify(menuMapper, never()).insert(any(DiningOrderMenu.class));
    }

    @Test
    @DisplayName("移除菜：全部成员可移除任意菜（含发起者加的），递增 version 后删行")
    void removeDish_success() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(menuMapper.delete(any(Wrapper.class))).thenReturn(1);

        // JOINER 并非该菜的「归属人」——草稿期不校验归属（拍板②）
        service.removeDish(ORDER_ID, 7L, 3L, JOINER);

        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，WHERE/SET 参数才进入参数表
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(3L));
        verify(menuMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("移除菜：不存在按幂等成功（不递增 version、不删行）")
    void removeDish_idempotent() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.removeDish(ORDER_ID, 7L, 3L, CREATOR);

        verify(orderMapper, never()).update(any(), any(Wrapper.class));
        verify(menuMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("移除菜：非 DRAFT / 非成员 / version 缺失 / version 冲突均拒绝")
    void removeDish_guards() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "FINISHED", "tok", 3, CREATOR));
        expectBusinessException("点单已结束，不能编辑菜单",
                () -> service.removeDish(ORDER_ID, 7L, 3L, CREATOR));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        expectBusinessException("仅订单成员可以移除菜",
                () -> service.removeDish(ORDER_ID, 7L, 3L, JOINER));

        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        expectBusinessException("已知版本号 version 不能为空",
                () -> service.removeDish(ORDER_ID, 7L, null, CREATOR));

        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.removeDish(ORDER_ID, 7L, 99L, CREATOR));
        assertEquals(409, ex.getCode());
        verify(menuMapper, never()).delete(any(Wrapper.class));
    }

    // ==================== 放弃草稿（cancel，置 CANCELLED） ====================

    @Test
    @DisplayName("放弃草稿：仅发起者的 DRAFT → 条件 UPDATE 置 CANCELLED + cancelled_at，不清子表")
    void cancel_success() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancel(ORDER_ID, CREATOR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，WHERE/SET 参数才进入参数表
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("DRAFT"),
                "状态迁移必须以 DRAFT 为条件，防止误改已定稿单");
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("CANCELLED"));
        // 不再物理删除：订单与菜单/成员子表全部保留，参与者仍可见「已取消」
        verify(menuMapper, never()).delete(any(Wrapper.class));
        verify(memberMapper, never()).delete(any(Wrapper.class));
        verify(itemMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("变更广播：放弃草稿后发布 SSE 信号（无事务上下文时立即执行）")
    void cancel_publishesChange() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancel(ORDER_ID, CREATOR);

        verify(streamer).publish(ORDER_ID);
    }

    @Test
    @DisplayName("放弃草稿：非发起者 / 非 DRAFT（已结束/已取消）/ 并发状态变更均拒绝")
    void cancel_guards() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        expectBusinessException("仅发起者可放弃草稿", () -> service.cancel(ORDER_ID, JOINER));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "FINISHED", "tok", 1, CREATOR));
        expectBusinessException("仅草稿可放弃", () -> service.cancel(ORDER_ID, CREATOR));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "CANCELLED", "tok", 1, CREATOR));
        expectBusinessException("点单已取消，无需重复操作", () -> service.cancel(ORDER_ID, CREATOR));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(orderMapper.update(any(), any())).thenReturn(0);
        expectBusinessException("草稿状态已变更，请刷新后重试", () -> service.cancel(ORDER_ID, CREATOR));
    }

    // ==================== 退出点单 ====================

    @Test
    @DisplayName("退出：参与者退出 DRAFT 删成员行；发起者/已结束/已取消拒绝；非成员幂等")
    void leave_guards_and_idempotent() {
        // 非成员：幂等直接成功，不删行
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        service.leave(ORDER_ID, JOINER);
        verify(memberMapper, never()).delete(any(Wrapper.class));

        // 成员：删成员行（订单本体保留）
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(memberMapper.delete(any(Wrapper.class))).thenReturn(1);
        service.leave(ORDER_ID, JOINER);
        verify(memberMapper).delete(any(Wrapper.class));

        // 发起者不能退出（走放弃）
        expectBusinessException("发起者不能退出，如需结束请使用放弃草稿",
                () -> service.leave(ORDER_ID, CREATOR));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "FINISHED", "tok", 1, CREATOR));
        expectBusinessException("点单已结束，无法退出", () -> service.leave(ORDER_ID, JOINER));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "CANCELLED", "tok", 1, CREATOR));
        expectBusinessException("点单已取消，无法退出", () -> service.leave(ORDER_ID, JOINER));
    }

    // ==================== 我的进行中点单 ====================

    @Test
    @DisplayName("active：取本人最新 DRAFT（orderId/title/status）；无则 null")
    void getMyActiveOrder_latestDraftOrNull() {
        when(orderMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(order(ORDER_ID, "DRAFT", "tok", 2, CREATOR)));
        DiningOrderActiveVo vo = service.getMyActiveOrder(CREATOR);
        assertNotNull(vo);
        assertEquals(ORDER_ID, vo.getOrderId());
        assertEquals("DRAFT", vo.getStatus());

        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertNull(service.getMyActiveOrder(CREATOR));
    }

    @Test
    @DisplayName("创建：已有本人进行中的 DRAFT → 唯一闸门拒绝（40901，message 带既有单标题）")
    void create_activeExists_rejected40901() {
        when(orderMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(new DiningOrder().setId(1L).setTitle("周五晚火锅").setStatus("DRAFT")));

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.create(createFrom("新点单", null, null), CREATOR));
        assertEquals(40901, ex.getCode());
        assertTrue(ex.getMessage().contains("周五晚火锅"));
        verify(orderMapper, never()).insert(any(DiningOrder.class));
    }

    // ==================== 加入 ====================

    @Test
    @DisplayName("加入：token 匹配且 DRAFT → 成员落库")
    void join_success() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);

        JoinDiningOrderFrom from = new JoinDiningOrderFrom();
        from.setJoinToken("tok");
        service.join(ORDER_ID, from, JOINER);

        ArgumentCaptor<DiningOrderMember> captor = ArgumentCaptor.forClass(DiningOrderMember.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals(ORDER_ID, captor.getValue().getOrderId());
        assertEquals(JOINER, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("加入：DRAFT 草稿同样可加入（边点边邀），成员落库并发通知")
    void join_draftAllowed() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(new DiningOrderMember().setOrderId(ORDER_ID).setUserId(CREATOR)));
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);

        JoinDiningOrderFrom from = new JoinDiningOrderFrom();
        from.setJoinToken("tok");
        service.join(ORDER_ID, from, JOINER);

        verify(memberMapper).insert(any(DiningOrderMember.class));
        verify(noticeService).publishTargeted(eq(NoticeType.DINING_JOIN), any(), any(), any(), any());
    }

    @Test
    @DisplayName("加入：发布 DINING_JOIN 定向通知——接收人=发起者+既有参与者（不含操作人），深链指向订单详情")
    void join_publishesDiningJoinNotice_excludingOperator() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        // 既有参与者：发起者 + 103（操作人 102 尚未加入）
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMember().setOrderId(ORDER_ID).setUserId(CREATOR),
                new DiningOrderMember().setOrderId(ORDER_ID).setUserId(103L)));
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);

        JoinDiningOrderFrom from = new JoinDiningOrderFrom();
        from.setJoinToken("tok");
        service.join(ORDER_ID, from, JOINER);

        // 发布时机：成员落库之后（同事务，通知随业务写一起提交）
        InOrder inOrder = inOrder(memberMapper, noticeService);
        inOrder.verify(memberMapper).insert(any(DiningOrderMember.class));
        inOrder.verify(noticeService).publishTargeted(
                eq(NoticeType.DINING_JOIN), any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> receiversCaptor = ArgumentCaptor.forClass(List.class);
        verify(noticeService).publishTargeted(
                eq(NoticeType.DINING_JOIN),
                eq("有人加入了「周五晚火锅」"),
                any(),
                eq("smartwash://dining/order/900?t=tok"),
                receiversCaptor.capture());
        assertEquals(List.of(CREATOR, 103L), receiversCaptor.getValue(),
                "接收人必须是发起者+既有参与者，且不含操作人");
    }

    @Test
    @DisplayName("加入：发布失败向上抛出（不吞异常），业务写随事务回滚")
    void join_noticeFailure_propagates() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(new DiningOrderMember().setOrderId(ORDER_ID).setUserId(CREATOR)));
        when(memberMapper.insert(any(DiningOrderMember.class))).thenReturn(1);
        org.mockito.Mockito.doThrow(new CustomExceptions("定向发送参数不完整"))
                .when(noticeService)
                .publishTargeted(any(), any(), any(), any(), any());

        JoinDiningOrderFrom from = new JoinDiningOrderFrom();
        from.setJoinToken("tok");
        expectBusinessException("定向发送参数不完整", () -> service.join(ORDER_ID, from, JOINER));
    }

    @Test
    @DisplayName("加入三类失败：token 错误 / 订单已结束 / 重复加入，均拒绝且不落库")
    void join_failurePaths_rejected() {
        JoinDiningOrderFrom wrongToken = new JoinDiningOrderFrom();
        wrongToken.setJoinToken("bad-token");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        expectBusinessException("加入凭证无效或已重置", () -> service.join(ORDER_ID, wrongToken, JOINER));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "FINISHED", "tok", 5, CREATOR));
        JoinDiningOrderFrom goodToken = new JoinDiningOrderFrom();
        goodToken.setJoinToken("tok");
        expectBusinessException("点单已结束，无法加入", () -> service.join(ORDER_ID, goodToken, JOINER));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        expectBusinessException("你已在该点单中", () -> service.join(ORDER_ID, goodToken, JOINER));

        verify(memberMapper, never()).insert(any(DiningOrderMember.class));
        verify(noticeService, never()).publishTargeted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("加入：并发撞 uk_order_user（DuplicateKeyException）仍按防重复拒绝")
    void join_duplicateKeyFallback_rejected() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(memberMapper.insert(any(DiningOrderMember.class))).thenThrow(new DuplicateKeyException("uk_order_user"));

        JoinDiningOrderFrom from = new JoinDiningOrderFrom();
        from.setJoinToken("tok");
        expectBusinessException("你已在该点单中", () -> service.join(ORDER_ID, from, JOINER));
        verify(noticeService, never()).publishTargeted(any(), any(), any(), any(), any());
    }

    // ==================== 重置加入凭证 ====================

    @Test
    @DisplayName("重置凭证：仅发起者；生成新 token 并精确 SET（不触碰 version）")
    void resetJoinToken_creatorOnly() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "old", 0, CREATOR));
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        expectBusinessException("仅发起者可重置加入凭证", () -> service.resetJoinToken(ORDER_ID, JOINER));

        String tokenByCreator = service.resetJoinToken(ORDER_ID, CREATOR);
        assertNotNull(tokenByCreator);
        assertTrue(tokenByCreator.length() >= 16, "新 token 应为随机串");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), captor.capture());
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertFalse(params.containsValue(0), "重置凭证不得写入 version");
    }

    @Test
    @DisplayName("重置凭证：订单已结束拒绝")
    void resetJoinToken_finishedOrder_rejected() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "FINISHED", "old", 5, CREATOR));
        expectBusinessException("点单已结束，无需重置加入凭证",
                () -> service.resetJoinToken(ORDER_ID, CREATOR));
    }

    // ==================== 详情轮询协议 ====================

    @Test
    @DisplayName("详情轮询：knownVersion 与服务端一致 → unchanged=true 轻量响应，不组装菜单/成员/选菜")
    void detail_knownVersionMatches_lightweight() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 3, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        DiningOrderDetailVo vo = service.detail(ORDER_ID, 3L, CREATOR);

        assertTrue(vo.getUnchanged());
        assertEquals(3L, vo.getVersion());
        assertNull(vo.getTitle());
        assertNull(vo.getMenu());
        assertNull(vo.getItems());
        assertNull(vo.getMembers());
        verify(menuMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("详情全量：knownVersion 不匹配 → 全量组装（菜单/选菜/参与者/脱敏手机号/isCreator）")
    void detail_versionChanged_fullAssembly() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 5, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        DiningOrderMenu menu = new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L);
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(menu));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(Map.of(
                1L, new DiningRecipeGate.RecipeBrief(1L, "番茄炒蛋", 11L, null)));
        DiningOrderItem item = new DiningOrderItem().setOrderId(ORDER_ID).setRecipeId(1L)
                .setUserId(JOINER).setNote("不要辣");
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        DiningOrderMember member1 = new DiningOrderMember().setOrderId(ORDER_ID).setUserId(CREATOR);
        DiningOrderMember member2 = new DiningOrderMember().setOrderId(ORDER_ID).setUserId(JOINER);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member1, member2));
        Users creatorUser = new Users().setUserId(CREATOR).setPhoneNumber("13900000001");
        Users joinerUser = new Users().setUserId(JOINER).setPhoneNumber("13900000002");
        when(usersMapper.selectBatchIds(anyCollection())).thenReturn(List.of(creatorUser, joinerUser));

        DiningOrderDetailVo vo = service.detail(ORDER_ID, 0L, CREATOR);

        assertFalse(vo.getUnchanged());
        assertEquals(5L, vo.getVersion());
        assertEquals("周五晚火锅", vo.getTitle());
        assertEquals("番茄炒蛋", vo.getMenu().get(0).getName());
        assertEquals(11L, vo.getMenu().get(0).getCategoryId());
        assertEquals("不要辣", vo.getItems().get(0).getNote());
        assertEquals(2, vo.getMembers().size());
        assertTrue(vo.getMembers().get(0).getIsCreator(), "发起者成员带 isCreator 标记");
        assertEquals("139****0001", vo.getMembers().get(0).getPhoneMasked(), "手机号必须脱敏");
        assertFalse(vo.getMembers().get(1).getIsCreator());
        assertEquals("139****0002", vo.getMembers().get(1).getPhoneMasked());
    }

    @Test
    @DisplayName("详情：非成员 / 订单不存在 → 拒绝")
    void detail_accessControl() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        expectBusinessException("仅订单成员可查看点单详情", () -> service.detail(ORDER_ID, null, JOINER));

        when(orderMapper.selectById(404L)).thenReturn(null);
        expectBusinessException("点单不存在", () -> service.detail(404L, null, CREATOR));
    }

    // ==================== brief ====================

    @Test
    @DisplayName("brief：凭 token 换标题/发起者脱敏手机号/菜品数/joined 标记")
    void brief_success() {
        // getOne(wrapper, false) 的内部实现随 MP 版本走 selectOne/selectList 不同路径，三重 mock 兜住
        DiningOrder found = order(ORDER_ID, "DRAFT", "tok", 0, CREATOR);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(found);
        when(orderMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(found);
        doReturn(List.of(found)).when(orderMapper).selectList(any(Wrapper.class));
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(usersMapper.selectById(CREATOR)).thenReturn(new Users().setUserId(CREATOR).setPhoneNumber("13900000001"));

        var vo = service.brief("tok", JOINER);

        assertEquals(ORDER_ID, vo.getOrderId());
        assertEquals("周五晚火锅", vo.getTitle());
        assertEquals("DRAFT", vo.getStatus());
        assertEquals("139****0001", vo.getCreatorName());
        assertEquals(2L, vo.getRecipeCount());
        assertFalse(vo.getJoined());
    }

    @Test
    @DisplayName("brief：无效 token（单不存在或已重置）→ 统一业务文案")
    void brief_invalidToken_rejected() {
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(orderMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        doReturn(List.of()).when(orderMapper).selectList(any(Wrapper.class));
        expectBusinessException("加入凭证无效或已重置", () -> service.brief("bad", JOINER));
    }

    // ==================== 我的订单列表 ====================

    @Test
    @DisplayName("列表分页：透传 page/size，转换 VO 保持库内倒序并按 isCreator 标记（我发起/我参与）")
    void pageMy_mapsVoWithIsCreator() {
        // JOINER 参与 881（他人发起）、发起 880；合并与倒序由 mapper SQL 负责，服务层透传并转换
        DiningOrder joinedNewer = order(881L, "DRAFT", "t1", 2, CREATOR);
        DiningOrder createdOrder = order(880L, "DRAFT", "t2", 0, JOINER);
        Page<DiningOrder> entityPage = new Page<>(1, 10, 2);
        entityPage.setRecords(List.of(joinedNewer, createdOrder));
        when(orderMapper.selectMyOrderPage(any(Page.class), eq(JOINER))).thenReturn(entityPage);
        // 批量摘要：一次查两份单的菜单，一次取食材名
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMenu().setOrderId(881L).setRecipeId(1L),
                new DiningOrderMenu().setOrderId(880L).setRecipeId(2L)));
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(
                1L, List.of("西红柿", "鸡蛋"),
                2L, List.of("黄瓜")));

        Page<DiningOrderListVo> result = service.pageMy(new BaseSearchFrom(), JOINER);

        assertEquals(2L, result.getTotal());
        // 转换保持库内倒序：参与的新单在前，发起的旧单在后
        assertEquals(881L, result.getRecords().get(0).getId());
        assertFalse(result.getRecords().get(0).getIsCreator());
        assertEquals(880L, result.getRecords().get(1).getId());
        assertTrue(result.getRecords().get(1).getIsCreator());
        // 列表摘要：菜单菜数 + 食材名并集（批量组装，无 N+1）
        assertEquals(1, result.getRecords().get(0).getMenuCount());
        assertEquals("西红柿、鸡蛋", result.getRecords().get(0).getIngredientSummary());
        assertEquals(1, result.getRecords().get(1).getMenuCount());
        assertEquals("黄瓜", result.getRecords().get(1).getIngredientSummary());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<DiningOrder>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(orderMapper).selectMyOrderPage(pageCaptor.capture(), eq(JOINER));
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());
        verify(menuMapper, org.mockito.Mockito.times(1)).selectList(any(Wrapper.class));
        verify(recipeGate, org.mockito.Mockito.times(1)).getIngredients(anyCollection());
    }

    @Test
    @DisplayName("列表摘要：食材名超过 6 个截断加「…」；无菜单时菜数 0、摘要空串")
    void pageMy_summaryTruncatesAndEmpty() {
        DiningOrder many = order(882L, "DRAFT", "t3", 0, JOINER);
        DiningOrder emptyMenu = order(883L, "DRAFT", "t4", 0, JOINER);
        Page<DiningOrder> entityPage = new Page<>(1, 10, 2);
        entityPage.setRecords(List.of(many, emptyMenu));
        when(orderMapper.selectMyOrderPage(any(Page.class), eq(JOINER))).thenReturn(entityPage);
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMenu().setOrderId(882L).setRecipeId(1L)));
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(
                1L, List.of("西红柿", "鸡蛋", "葱", "黄瓜", "蒜", "辣椒", "花椒")));

        Page<DiningOrderListVo> result = service.pageMy(new BaseSearchFrom(), JOINER);

        assertEquals(1, result.getRecords().get(0).getMenuCount());
        assertEquals("西红柿、鸡蛋、葱、黄瓜、蒜、辣椒…",
                result.getRecords().get(0).getIngredientSummary(), "超过 6 个须截断并加省略号");
        assertEquals(0, result.getRecords().get(1).getMenuCount());
        assertEquals("", result.getRecords().get(1).getIngredientSummary());
    }

    @Test
    @DisplayName("列表分页：无任何订单时返回空页（total=0）")
    void pageMy_empty() {
        Page<DiningOrder> empty = new Page<>(1, 10, 0);
        empty.setRecords(List.of());
        when(orderMapper.selectMyOrderPage(any(Page.class), eq(JOINER))).thenReturn(empty);

        Page<DiningOrderListVo> result = service.pageMy(new BaseSearchFrom(), JOINER);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // ==================== 提交通知与 v2 快照 ====================

    @Test
    @DisplayName("submit：发布 DINING_FINISH 定向通知——接收人=全体成员，深链指向订单详情")
    void submit_publishesDiningFinishNotice_toAllMembers() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L)));
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(1L, List.of("西红柿")));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(
                Map.of(1L, new DiningRecipeGate.RecipeBrief(1L, "番茄炒蛋", 11L, null)));
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        // 全体成员：发起者 + 参与者
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMember().setOrderId(ORDER_ID).setUserId(CREATOR),
                new DiningOrderMember().setOrderId(ORDER_ID).setUserId(JOINER)));

        service.submit(ORDER_ID, submitFrom("周五晚火锅", 0L), CREATOR);

        // 发布时机：状态条件 UPDATE 成功之后（同事务，通知随业务写一起提交）
        InOrder inOrder = inOrder(orderMapper, noticeService);
        inOrder.verify(orderMapper).update(any(), any(Wrapper.class));
        inOrder.verify(noticeService).publishTargeted(
                eq(NoticeType.DINING_FINISH), any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> receiversCaptor = ArgumentCaptor.forClass(List.class);
        verify(noticeService).publishTargeted(
                eq(NoticeType.DINING_FINISH),
                eq("「周五晚火锅」已结束点单"),
                any(),
                eq("smartwash://dining/order/900?t=tok"),
                receiversCaptor.capture());
        assertEquals(List.of(CREATOR, JOINER), receiversCaptor.getValue(), "提交通知接收人必须是全体成员");
    }

    @Test
    @DisplayName("submit v2：配料明细与热量进快照（ingredientTotals 归一求和/totalCalories/未知计数），无明细老菜谱回落名列表")
    void submit_v2_snapshotIncludesTotalsAndCalories() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "DRAFT", "tok", 0, CREATOR));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L),
                new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(2L)));
        // 菜 1 有关联明细，菜 2 为老菜谱（仅 ingredients JSON 名列表，无关联明细、无营养）
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(
                1L, List.of("西红柿", "鸡蛋"),
                2L, List.of("黄瓜")));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(Map.of(
                1L, new DiningRecipeGate.RecipeBrief(1L, "西红柿炒蛋", 11L, null),
                2L, new DiningRecipeGate.RecipeBrief(2L, "凉拌黄瓜", 11L, null)));
        when(recipeGate.getIngredientAmounts(anyCollection())).thenReturn(Map.of(
                1L, List.of(new DiningRecipeGate.IngredientDetail("鸡蛋", new java.math.BigDecimal("575"), "g"),
                        new DiningRecipeGate.IngredientDetail("西红柿", new java.math.BigDecimal("1.8"), "kg"))));
        when(recipeGate.getServingCalories(anyCollection())).thenReturn(Map.of(
                1L, new java.math.BigDecimal("1051")));
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        DiningOrderFinishVo vo = service.submit(ORDER_ID, submitFrom("周五晚火锅", 0L), CREATOR);

        com.fasterxml.jackson.databind.JsonNode snapshot = com.smartwash.utils.JsonUtils.readTree(vo.getShoppingList());
        // 配料聚合：kg→g 归一求和（1.8kg → 1800g），老菜谱的黄瓜只进名并集不进数量聚合
        assertEquals(2, snapshot.path("ingredientTotals").size());
        assertEquals("鸡蛋", snapshot.path("ingredientTotals").get(0).path("name").asText());
        assertEquals(0, new java.math.BigDecimal("575").compareTo(
                new java.math.BigDecimal(snapshot.path("ingredientTotals").get(0).path("totalValue").asText())));
        assertEquals("g", snapshot.path("ingredientTotals").get(0).path("unit").asText());
        assertEquals("西红柿", snapshot.path("ingredientTotals").get(1).path("name").asText());
        assertEquals(0, new java.math.BigDecimal("1800").compareTo(
                new java.math.BigDecimal(snapshot.path("ingredientTotals").get(1).path("totalValue").asText())));
        assertTrue(snapshot.path("ingredientNames").toString().contains("黄瓜"), "老菜谱名列表回落进名并集");
        // 热量：已知 1051 + 未知 1（凉拌黄瓜无热量）
        assertEquals(0, new java.math.BigDecimal("1051").compareTo(
                new java.math.BigDecimal(snapshot.path("totalCalories").asText())));
        assertEquals(1, snapshot.path("calorieKnownDishCount").asInt());
        assertEquals(1, snapshot.path("calorieUnknownDishCount").asInt());
        assertEquals("西红柿炒蛋", snapshot.path("perDishCalories").get(0).path("recipeName").asText());
        // v1 字段不丢：名并集与按菜视图仍在
        assertTrue(snapshot.path("ingredientNames").size() >= 3);
        assertEquals(2, snapshot.path("perRecipe").size());
    }

    /** 断言抛出业务异常且消息匹配 */
    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }
}
