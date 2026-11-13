package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.dining.entity.DiningOrder;
import com.smartwash.dining.entity.DiningOrderItem;
import com.smartwash.dining.from.AddDiningOrderItemFrom;
import com.smartwash.dining.from.UpdateDiningOrderItemFrom;
import com.smartwash.dining.mapper.DiningOrderItemMapper;
import com.smartwash.dining.mapper.DiningOrderMapper;
import com.smartwash.dining.mapper.DiningOrderMemberMapper;
import com.smartwash.dining.mapper.DiningOrderMenuMapper;
import com.smartwash.exception.CustomExceptions;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 选菜服务测试（纯 Mockito）：version 乐观并发协议（CAS 0 行 → 409 业务码）、
 * 写入闸门次序（CAS 先行，校验失败不落库）、一人一菜一条（uk 兜底）、备注清空语义、取消选菜。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiningOrderItemServiceImpl 选菜并发测试")
class DiningOrderItemServiceImplTest {

    private static final Long USER = 101L;
    private static final Long ORDER_ID = 900L;
    private static final Long RECIPE_ID = 1L;

    @Mock
    private DiningOrderMapper orderMapper;

    @Mock
    private DiningOrderMemberMapper memberMapper;

    @Mock
    private DiningOrderMenuMapper menuMapper;

    @Mock
    private DiningOrderItemMapper itemMapper;

    private DiningOrderItemServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DiningOrder.class);
        TableInfoHelper.initTableInfo(assistant, DiningOrderItem.class);
    }

    @BeforeEach
    void setUp() {
        service = new DiningOrderItemServiceImpl(orderMapper, memberMapper, menuMapper);
        // ServiceImpl 的内置方法走父类 baseMapper 字段，注入同一 mock
        ReflectionTestUtils.setField(service, "baseMapper", itemMapper);
    }

    private AddDiningOrderItemFrom addFrom(Long version, String note) {
        AddDiningOrderItemFrom from = new AddDiningOrderItemFrom();
        from.setRecipeId(RECIPE_ID);
        from.setVersion(version);
        from.setNote(note);
        return from;
    }

    private void mockPassGuards() {
        // CAS 闸门放行
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        // 订单 ONGOING
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                new DiningOrder().setId(ORDER_ID).setStatus("ONGOING").setVersion(0));
        // 是成员
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        // 菜在菜单内
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
    }

    // ==================== 勾选（version 乐观并发） ====================

    @Test
    @DisplayName("勾选：version 匹配 CAS 递增成功后落库（带备注）")
    void addItem_success() {
        mockPassGuards();
        when(itemMapper.insert(any(DiningOrderItem.class))).thenReturn(1);

        service.addItem(ORDER_ID, addFrom(0L, "不要辣"), USER);

        // CAS：WHERE id=? AND version=? SET version=version+1
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<DiningOrder>> casCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(any(), casCaptor.capture());
        casCaptor.getValue().getSqlSegment(); // 物化惰性 SQL 段
        Map<String, Object> casParams = casCaptor.getValue().getParamNameValuePairs();
        assertFalse(!casParams.containsValue(0L), "已知 version 必须进入 CAS 条件");

        ArgumentCaptor<DiningOrderItem> captor = ArgumentCaptor.forClass(DiningOrderItem.class);
        verify(itemMapper).insert(captor.capture());
        assertEquals(RECIPE_ID, captor.getValue().getRecipeId());
        assertEquals(USER, captor.getValue().getUserId());
        assertEquals("不要辣", captor.getValue().getNote());
    }

    @Test
    @DisplayName("勾选：version 不匹配（CAS 影响行数 0）→ 业务错误码 409，不落库不触后续校验")
    void addItem_versionConflict_409() {
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.addItem(ORDER_ID, addFrom(5L, null), USER));

        assertEquals(409, ex.getCode());
        assertEquals("订单已被他人更新，请刷新后重试", ex.getMessage());
        verify(itemMapper, never()).insert(any(DiningOrderItem.class));
        verify(orderMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("勾选：version 缺失 → 业务拒绝")
    void addItem_versionMissing_rejected() {
        expectBusinessException("已知版本号 version 不能为空",
                () -> service.addItem(ORDER_ID, addFrom(null, null), USER));
        verify(orderMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("勾选：订单已结束（CAS 放行后状态校验拒绝）→ 不落库；事务回滚保证 version 不虚增")
    void addItem_finishedOrder_rejected() {
        when(orderMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                new DiningOrder().setId(ORDER_ID).setStatus("FINISHED").setVersion(3));

        expectBusinessException("点单已结束，不能修改选菜",
                () -> service.addItem(ORDER_ID, addFrom(3L, null), USER));
        verify(itemMapper, never()).insert(any(DiningOrderItem.class));
    }

    @Test
    @DisplayName("勾选：非成员拒绝")
    void addItem_notMember_rejected() {
        mockPassGuards();
        when(memberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        expectBusinessException("仅订单成员可以选菜",
                () -> service.addItem(ORDER_ID, addFrom(0L, null), USER));
        verify(itemMapper, never()).insert(any(DiningOrderItem.class));
    }

    @Test
    @DisplayName("勾选：菜不在本单菜单内拒绝（参与者只在发起者定的范围内选）")
    void addItem_notInMenu_rejected() {
        mockPassGuards();
        when(menuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        expectBusinessException("该菜不在本单菜单内",
                () -> service.addItem(ORDER_ID, addFrom(0L, null), USER));
        verify(itemMapper, never()).insert(any(DiningOrderItem.class));
    }

    @Test
    @DisplayName("勾选：并发撞 uk(order+recipe+user)（DuplicateKeyException）→ 重复勾选提示")
    void addItem_duplicateKey_rejected() {
        mockPassGuards();
        when(itemMapper.insert(any(DiningOrderItem.class))).thenThrow(new DuplicateKeyException("uk_order_recipe_user"));

        expectBusinessException("你已选过这道菜，可直接修改备注",
                () -> service.addItem(ORDER_ID, addFrom(0L, null), USER));
    }

    // ==================== 改备注 ====================

    @Test
    @DisplayName("改备注：更新本人记录；note 传 null 即清空")
    void updateNote_success() {
        mockPassGuards();
        DiningOrderItem existing = new DiningOrderItem().setId(5L)
                .setOrderId(ORDER_ID).setRecipeId(RECIPE_ID).setUserId(USER).setNote("旧备注");
        // getOne(wrapper, false) 的内部实现随 MP 版本走 selectOne/selectList 不同路径，多重 mock 兜住
        when(itemMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(itemMapper.selectOne(any(Wrapper.class), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(existing);
        org.mockito.Mockito.doReturn(java.util.List.of(existing)).when(itemMapper).selectList(any(Wrapper.class));
        when(itemMapper.updateById(any(DiningOrderItem.class))).thenReturn(1);

        UpdateDiningOrderItemFrom from = new UpdateDiningOrderItemFrom();
        from.setRecipeId(RECIPE_ID);
        from.setVersion(1L);
        from.setNote(null);
        service.updateItemNote(ORDER_ID, from, USER);

        ArgumentCaptor<DiningOrderItem> captor = ArgumentCaptor.forClass(DiningOrderItem.class);
        verify(itemMapper).updateById(captor.capture());
        assertNull(captor.getValue().getNote(), "note 传 null 必须清空备注");
    }

    @Test
    @DisplayName("改备注：本人未勾选该菜 → 拒绝")
    void updateNote_missingItem_rejected() {
        mockPassGuards();
        when(itemMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        UpdateDiningOrderItemFrom from = new UpdateDiningOrderItemFrom();
        from.setRecipeId(RECIPE_ID);
        from.setVersion(1L);
        expectBusinessException("你还没选这道菜", () -> service.updateItemNote(ORDER_ID, from, USER));
    }

    // ==================== 取消选菜 ====================

    @Test
    @DisplayName("取消选菜：删除本人该菜记录（CAS 先行）")
    void removeItem_success() {
        mockPassGuards();
        when(itemMapper.delete(any(Wrapper.class))).thenReturn(1);

        service.removeItem(ORDER_ID, RECIPE_ID, 2L, USER);

        verify(itemMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("取消选菜：本人未勾选该菜 → 拒绝")
    void removeItem_missing_rejected() {
        mockPassGuards();
        when(itemMapper.delete(any(Wrapper.class))).thenReturn(0);

        expectBusinessException("你还没选这道菜",
                () -> service.removeItem(ORDER_ID, RECIPE_ID, 2L, USER));
    }

    private void expectBusinessException(String expectedMessage, Runnable action) {
        CustomExceptions ex = assertThrows(CustomExceptions.class, action::run);
        assertEquals(expectedMessage, ex.getMessage());
    }
}
