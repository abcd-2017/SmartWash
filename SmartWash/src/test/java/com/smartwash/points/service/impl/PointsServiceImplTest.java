package com.smartwash.points.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.points.config.PointsProperties;
import com.smartwash.points.entity.PointLedger;
import com.smartwash.points.from.SearchPointLedgerFrom;
import com.smartwash.points.mapper.PointAccountMapper;
import com.smartwash.points.mapper.PointLedgerMapper;
import com.smartwash.points.mapper.PointSignInMapper;
import com.smartwash.points.vo.PointLedgerItemVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分流水分页回归测试：
 * 1) size 超过 50 必须截断为 50（防大页拉取打满内存）；
 * 2) 分页 total 必须透传（端上判断 hasMore 的依据）；
 * 3) 实体 → VO 映射口径与 /me 最近流水一致（reasonDesc 走枚举翻译）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PointsServiceImpl 积分流水分页测试")
class PointsServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private PointAccountMapper accountMapper;
    @Mock
    private PointLedgerMapper ledgerMapper;
    @Mock
    private PointSignInMapper signInMapper;
    @Mock
    private StringRedisTemplate redisTemplate;

    private PointsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PointsServiceImpl(accountMapper, ledgerMapper, signInMapper, redisTemplate, new PointsProperties());
    }

    private PointLedger ledger(int delta, String reason) {
        PointLedger entity = new PointLedger();
        entity.setLedgerId(1L);
        entity.setUserId(USER_ID);
        entity.setDelta(delta);
        entity.setReason(reason);
        entity.setRefDate(LocalDate.of(2026, 11, 16));
        entity.setBalanceAfter(15);
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 16, 10, 0));
        return entity;
    }

    @Test
    @DisplayName("size 超上限截断为 50，total 透传，字段映射正确")
    void pageLedger_clampsSizeAndMapsFields() {
        SearchPointLedgerFrom from = new SearchPointLedgerFrom();
        from.setPage(2);
        from.setSize(500);

        Page<PointLedger> page = new Page<>(2, 50, 3);
        page.setRecords(List.of(ledger(5, "SIGN_IN")));
        when(ledgerMapper.selectPageByUserId(any(), eq(USER_ID))).thenReturn(page);

        Page<PointLedgerItemVo> result = service.pageLedger(USER_ID, from);

        ArgumentCaptor<Page<PointLedger>> captor = ArgumentCaptor.forClass(Page.class);
        verify(ledgerMapper).selectPageByUserId(captor.capture(), eq(USER_ID));
        assertEquals(2, captor.getValue().getCurrent(), "页码应原样透传");
        assertEquals(50, captor.getValue().getSize(), "size 超上限必须截断为 50");

        assertEquals(3, result.getTotal(), "total 必须透传，供端上判断 hasMore");
        assertEquals(50, result.getSize());
        assertEquals(1, result.getRecords().size());
        PointLedgerItemVo item = result.getRecords().get(0);
        assertEquals(5, item.getDelta());
        assertEquals("SIGN_IN", item.getReason());
        assertEquals("每日签到", item.getReasonDesc());
        assertEquals(15, item.getBalanceAfter());
    }

    @Test
    @DisplayName("空页：records 为空列表、total 透传，不抛异常")
    void pageLedger_emptyPage() {
        SearchPointLedgerFrom from = new SearchPointLedgerFrom();
        from.setPage(1);
        from.setSize(10);

        Page<PointLedger> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());
        when(ledgerMapper.selectPageByUserId(any(), eq(USER_ID))).thenReturn(page);

        Page<PointLedgerItemVo> result = service.pageLedger(USER_ID, from);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }
}
