package com.smartwash.service.impl;

import com.smartwash.entity.Lockers;
import com.smartwash.from.locker.BatchAddLockerFrom;
import com.smartwash.mapper.LockersMapper;
import com.smartwash.vo.locker.BatchAddLockerVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 批量新增寄存柜行为测试。
 *
 * <p>seam：{@link LockersServiceImpl#batchAddLockers}。
 * 覆盖：编号冲突自动跳过、生成编号不重复。
 * 不覆盖：Controller 层 @Valid 校验、学校存在性校验（在 Controller）与 Web 交互。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LockersServiceImpl 批量新增寄存柜测试")
class LockersServiceImplTest {

    @Mock
    private LockersMapper lockersMapper;

    private LockersServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new LockersServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", lockersMapper);
        // saveBatch 走 SqlHelper，纯单元测试环境未初始化 TableInfo，直接 mock 掉；
        // 纯逻辑用例不会触发，用 lenient 避免 UnnecessaryStubbing
        lenient().doReturn(true).when(service).saveBatch(anyList());
    }

    private BatchAddLockerFrom from(Long schoolId, Integer startNumber, Integer count) {
        BatchAddLockerFrom f = new BatchAddLockerFrom();
        f.setSchoolId(schoolId);
        f.setStartNumber(startNumber);
        f.setCount(count);
        return f;
    }

    @Test
    @DisplayName("无冲突：从起始号连续生成 count 个编号")
    void batchAdd_noConflict_generatesConsecutiveNumbers() {
        when(lockersMapper.selectList(any())).thenReturn(List.of());

        BatchAddLockerVo vo = service.batchAddLockers(from(1L, 1, 3));

        assertEquals(3, vo.getAddedCount(), "应新增 3 个");
        assertEquals(1, vo.getFirstNumber(), "起始号应为 1");
        assertEquals(3, vo.getLastNumber(), "结束号应为 3");
    }

    @Test
    @DisplayName("部分编号已占用：跳过占用号，取 count 个可用编号")
    void batchAdd_partialConflict_skipsOccupiedNumbers() {
        Lockers l1 = new Lockers();
        l1.setLockerNumber(1);
        Lockers l2 = new Lockers();
        l2.setLockerNumber(2);
        when(lockersMapper.selectList(any())).thenReturn(List.of(l1, l2));

        BatchAddLockerVo vo = service.batchAddLockers(from(1L, 1, 3));

        assertEquals(3, vo.getAddedCount(), "应新增 3 个");
        assertEquals(3, vo.getFirstNumber(), "应跳过 1、2，从 3 开始");
        assertEquals(5, vo.getLastNumber(), "结束号应为 5");
    }

    @Test
    @DisplayName("起始区间全部占用：从下一个可用号继续")
    void batchAdd_allOccupied_continuesAfterOccupied() {
        Lockers l1 = new Lockers();
        l1.setLockerNumber(1);
        Lockers l2 = new Lockers();
        l2.setLockerNumber(2);
        Lockers l3 = new Lockers();
        l3.setLockerNumber(3);
        when(lockersMapper.selectList(any())).thenReturn(List.of(l1, l2, l3));

        BatchAddLockerVo vo = service.batchAddLockers(from(1L, 1, 3));

        assertEquals(3, vo.getAddedCount(), "应新增 3 个");
        assertEquals(4, vo.getFirstNumber(), "应从 4 开始");
        assertEquals(6, vo.getLastNumber(), "结束号应为 6");
    }

    @Test
    @DisplayName("编号解析：从起始号跳过占用号，取够 count 个")
    void resolveAvailableNumbers_skipsOccupied() {
        assertEquals(List.of(3, 4, 6), service.resolveAvailableNumbers(Set.of(1, 2, 5), 1, 3));
    }

    @Test
    @DisplayName("编号解析：无占用时连续取值")
    void resolveAvailableNumbers_noOccupied() {
        assertEquals(List.of(1, 2, 3), service.resolveAvailableNumbers(Set.of(), 1, 3));
    }

    @Test
    @DisplayName("编号解析：占用号连续排在最前时也能取满")
    void resolveAvailableNumbers_allFrontOccupied() {
        assertEquals(List.of(4, 5, 6), service.resolveAvailableNumbers(Set.of(1, 2, 3), 1, 3));
    }
}
