package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.entity.DiningOrderItem;
import com.smartwash.dining.entity.DiningOrderMember;
import com.smartwash.dining.entity.DiningOrderMenu;
import com.smartwash.dining.from.CreateDiningOrderFrom;
import com.smartwash.dining.from.JoinDiningOrderFrom;
import com.smartwash.dining.mapper.DiningOrderItemMapper;
import com.smartwash.dining.mapper.DiningOrderMapper;
import com.smartwash.dining.mapper.DiningOrderMemberMapper;
import com.smartwash.dining.mapper.DiningOrderMenuMapper;
import com.smartwash.dining.service.DiningRecipeGate;
import com.smartwash.dining.vo.DiningOrderDetailVo;
import com.smartwash.dining.vo.DiningOrderFinishVo;
import com.smartwash.dining.vo.DiningOrderListVo;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
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
                orderMapper, noticeService);
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
    @DisplayName("创建：写入订单（ONGOING/随机 token/version=0），创建者即首个成员，菜单逐条落库")
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
        assertEquals("ONGOING", saved.getStatus());
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
        when(orderMapper.selectById(880L)).thenReturn(order(880L, "ONGOING", "t", 1, 999L));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        expectBusinessException("仅订单成员可复用历史菜单",
                () -> service.create(createFrom("标题", null, 880L), JOINER));
    }

    @Test
    @DisplayName("创建：显式 recipeIds 为空 → 拒绝")
    void create_emptyMenu_rejected() {
        expectBusinessException("本单菜单不能为空",
                () -> service.create(createFrom("标题", List.of(), null), CREATOR));
    }

    // ==================== 加入 ====================

    @Test
    @DisplayName("加入：token 匹配且 ONGOING → 成员落库")
    void join_success() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
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
    @DisplayName("加入：发布 DINING_JOIN 定向通知——接收人=发起者+既有参与者（不含操作人），深链指向订单详情")
    void join_publishesDiningJoinNotice_excludingOperator() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
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
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
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
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
        expectBusinessException("加入凭证无效或已重置", () -> service.join(ORDER_ID, wrongToken, JOINER));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "FINISHED", "tok", 5, CREATOR));
        JoinDiningOrderFrom goodToken = new JoinDiningOrderFrom();
        goodToken.setJoinToken("tok");
        expectBusinessException("点单已结束，无法加入", () -> service.join(ORDER_ID, goodToken, JOINER));

        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        expectBusinessException("你已在该点单中", () -> service.join(ORDER_ID, goodToken, JOINER));

        verify(memberMapper, never()).insert(any(DiningOrderMember.class));
        verify(noticeService, never()).publishTargeted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("加入：并发撞 uk_order_user（DuplicateKeyException）仍按防重复拒绝")
    void join_duplicateKeyFallback_rejected() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
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
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "old", 0, CREATOR));
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
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 3, CREATOR));
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
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 5, CREATOR));
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        DiningOrderMenu menu = new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L);
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(menu));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(Map.of(
                1L, new DiningRecipeGate.RecipeBrief(1L, "番茄炒蛋", 11L)));
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
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
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
        DiningOrder found = order(ORDER_ID, "ONGOING", "tok", 0, CREATOR);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(found);
        when(orderMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(found);
        doReturn(List.of(found)).when(orderMapper).selectList(any(Wrapper.class));
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(usersMapper.selectById(CREATOR)).thenReturn(new Users().setUserId(CREATOR).setPhoneNumber("13900000001"));

        var vo = service.brief("tok", JOINER);

        assertEquals(ORDER_ID, vo.getOrderId());
        assertEquals("周五晚火锅", vo.getTitle());
        assertEquals("ONGOING", vo.getStatus());
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
    @DisplayName("列表：我发起 + 我参与合并，按创建时间倒序，带 isCreator 标记")
    void listMy_mergesCreatedAndJoined() {
        // JOINER 参与 881（他人发起）、发起 880（创建即成员，成员表兜底直查覆盖）
        DiningOrderMember membership = new DiningOrderMember().setOrderId(881L).setUserId(JOINER);
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(membership));
        DiningOrder createdOrder = order(880L, "ONGOING", "t2", 0, JOINER);
        createdOrder.setCreatedAt(java.time.LocalDateTime.of(2026, 1, 1, 10, 0));
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(createdOrder));

        DiningOrder joinedNewer = order(881L, "ONGOING", "t1", 2, CREATOR);
        joinedNewer.setCreatedAt(java.time.LocalDateTime.of(2026, 1, 2, 10, 0));
        doReturn(List.of(joinedNewer, createdOrder)).when(orderMapper).selectBatchIds(anyCollection());

        List<DiningOrderListVo> result = service.listMy(JOINER);

        assertEquals(2, result.size());
        // 倒序：参与的新单（1月2日）在前，发起的旧单（1月1日）在后
        assertEquals(881L, result.get(0).getId());
        assertFalse(result.get(0).getIsCreator());
        assertEquals(880L, result.get(1).getId());
        assertTrue(result.get(1).getIsCreator());
    }

    @Test
    @DisplayName("列表：无任何订单时返回空列表")
    void listMy_empty() {
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertTrue(service.listMy(JOINER).isEmpty());
    }

    // ==================== 结束点单 ====================

    @Test
    @DisplayName("结束点单：仅发起者；聚合食材并集 + 按菜视图写快照，status→FINISHED 且记录 finished_at")
    void finish_success_aggregatesSnapshot() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 3, CREATOR));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L),
                new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(2L)));
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(
                1L, List.of("西红柿", "鸡蛋", "葱"),
                2L, List.of("黄瓜", "蒜")));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(Map.of(
                1L, new DiningRecipeGate.RecipeBrief(1L, "番茄炒蛋", 11L),
                2L, new DiningRecipeGate.RecipeBrief(2L, "凉拌黄瓜", 11L)));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderItem().setOrderId(ORDER_ID).setRecipeId(1L).setUserId(JOINER).setNote("不要辣"),
                new DiningOrderItem().setOrderId(ORDER_ID).setRecipeId(2L).setUserId(CREATOR).setNote(null)));
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        DiningOrderFinishVo vo = service.finish(ORDER_ID, CREATOR);

        assertEquals(ORDER_ID, vo.getId());
        assertEquals("FINISHED", vo.getStatus());
        assertNotNull(vo.getFinishedAt());
        // 按食材：两道菜食材并集（西红柿、鸡蛋、葱、黄瓜、蒜）
        assertTrue(vo.getShoppingList().contains("西红柿"), "购买清单须含番茄炒蛋的食材");
        assertTrue(vo.getShoppingList().contains("葱"));
        assertTrue(vo.getShoppingList().contains("黄瓜"));
        // 按菜视图：菜名 + 选菜人备注
        assertTrue(vo.getShoppingList().contains("番茄炒蛋"), "按菜视图须含菜名");
        assertTrue(vo.getShoppingList().contains("不要辣"), "按菜视图须含选菜人备注");

        // 状态条件 UPDATE：SET FINISHED + 快照 + finished_at
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 物化惰性 SQL 段，WHERE 条件参数才进入参数表
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue("FINISHED"), "状态必须置为 FINISHED");
        assertTrue(params.containsValue("ONGOING"), "条件 UPDATE 必须以 ONGOING 为闸门");
    }

    @Test
    @DisplayName("结束点单：发布 DINING_FINISH 定向通知——接收人=全体成员，深链指向订单详情")
    void finish_publishesDiningFinishNotice_toAllMembers() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 3, CREATOR));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L)));
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(1L, List.of("西红柿")));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(
                Map.of(1L, new DiningRecipeGate.RecipeBrief(1L, "番茄炒蛋", 11L)));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        // 全体成员：发起者 + 参与者
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                new DiningOrderMember().setOrderId(ORDER_ID).setUserId(CREATOR),
                new DiningOrderMember().setOrderId(ORDER_ID).setUserId(JOINER)));

        service.finish(ORDER_ID, CREATOR);

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
        assertEquals(List.of(CREATOR, JOINER), receiversCaptor.getValue(), "结束通知接收人必须是全体成员") ;
    }

    @Test
    @DisplayName("结束点单：已 FINISHED 幂等返回库内原快照，不聚合不更新")
    void finish_alreadyFinished_idempotent() {
        DiningOrder finished = order(ORDER_ID, "FINISHED", "tok", 3, CREATOR);
        finished.setShoppingList("{\"ingredientNames\":[\"西红柿\"]}");
        finished.setFinishedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(finished);

        DiningOrderFinishVo vo = service.finish(ORDER_ID, CREATOR);

        assertEquals("{\"ingredientNames\":[\"西红柿\"]}", vo.getShoppingList(), "必须返回原快照");
        assertEquals("FINISHED", vo.getStatus());
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), vo.getFinishedAt());
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
        verify(menuMapper, never()).selectList(any(Wrapper.class));
        verify(recipeGate, never()).getIngredients(anyCollection());
        verify(noticeService, never()).publishTargeted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("结束点单：非发起者拒绝")
    void finish_notCreator_rejected() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
        expectBusinessException("仅发起者可结束点单", () -> service.finish(ORDER_ID, JOINER));
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
        verify(noticeService, never()).publishTargeted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("结束点单：订单不存在拒绝")
    void finish_orderMissing_rejected() {
        when(orderMapper.selectForUpdate(404L)).thenReturn(null);
        expectBusinessException("点单不存在", () -> service.finish(404L, CREATOR));
    }

    @Test
    @DisplayName("结束点单：并发窗口条件 UPDATE 影响 0 行但订单已被结束 → 幂等返回其快照")
    void finish_concurrentFinished_idempotent() {
        when(orderMapper.selectForUpdate(ORDER_ID)).thenReturn(order(ORDER_ID, "ONGOING", "tok", 0, CREATOR));
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(new DiningOrderMenu().setOrderId(ORDER_ID).setRecipeId(1L)));
        when(recipeGate.getIngredients(anyCollection())).thenReturn(Map.of(1L, List.of("西红柿")));
        when(recipeGate.getBriefs(anyCollection())).thenReturn(
                Map.of(1L, new DiningRecipeGate.RecipeBrief(1L, "番茄炒蛋", 11L)));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        DiningOrder finished = order(ORDER_ID, "FINISHED", "tok", 0, CREATOR);
        finished.setShoppingList("{\"ingredientNames\":[\"西红柿\"]}");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(finished);

        DiningOrderFinishVo vo = service.finish(ORDER_ID, CREATOR);

        assertEquals("{\"ingredientNames\":[\"西红柿\"]}", vo.getShoppingList());
        assertEquals("FINISHED", vo.getStatus());
    }

    /** 断言抛出业务异常且消息匹配 */
    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }
}
