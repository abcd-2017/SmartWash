package com.smartwash.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.PaymentStatus;
import com.smartwash.config.MinioConfig;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.ISchoolsService;
import com.smartwash.vo.users.TransactionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交易流水分页化回归测试（评审报告后端 #24）。
 * 闸门语义：充值+支付合并排序、LIMIT/OFFSET 全部下沉到数据库（UsersMapper UNION ALL），
 * Service 只负责分页参数兜底与 Page 组装，不允许再出现两表全量拉进内存的实现。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsersServiceImpl 交易流水分页测试")
class UsersServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final String SUCCESS_STATUS = PaymentStatus.SUCCESS.getStatus();

    @Mock
    private ISchoolsService schoolsService;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private UsersMapper usersMapper;

    private UsersServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UsersServiceImpl(schoolsService, minioConfig);
        // ServiceImpl 父类 baseMapper（交易流水 UNION 分页/count 走它），构造注入不会填充，手动注入
        ReflectionTestUtils.setField(service, "baseMapper", usersMapper);
    }

    @Test
    @DisplayName("默认分页：按 offset=0/limit=10 查询并组装 Page（records/total/current/size）")
    void getTransactionHistory_defaultPagination_assemblesPage() {
        when(usersMapper.countTransactionHistory(USER_ID, SUCCESS_STATUS)).thenReturn(2L);
        when(usersMapper.selectTransactionPage(USER_ID, SUCCESS_STATUS, 0L, 10))
                .thenReturn(List.of(rechargeVo("50.00"), paymentVo("-12.50")));

        Page<TransactionVo> result = service.getTransactionHistory(USER_ID, 1, 10);

        assertEquals(1L, result.getCurrent());
        assertEquals(10L, result.getSize());
        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals("recharge", result.getRecords().get(0).getType());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getRecords().get(0).getAmount()));
        assertEquals("payment", result.getRecords().get(1).getType());
        // 消费流水必须为负数金额（数据库层 -amount 取负，语义不回归）
        assertEquals(0, new BigDecimal("-12.50").compareTo(result.getRecords().get(1).getAmount()));
        // 传参断言：状态过滤用 PaymentStatus.SUCCESS，首页 offset=0
        verify(usersMapper).selectTransactionPage(eq(USER_ID), eq(SUCCESS_STATUS), eq(0L), eq(10));
    }

    @Test
    @DisplayName("参数兜底：page=0 回退第 1 页，pageSize=999 封顶 50（防变相全量拉取）")
    void getTransactionHistory_illegalParams_clamped() {
        when(usersMapper.countTransactionHistory(USER_ID, SUCCESS_STATUS)).thenReturn(1L);
        when(usersMapper.selectTransactionPage(USER_ID, SUCCESS_STATUS, 0L, 50))
                .thenReturn(List.of(rechargeVo("1.00")));

        Page<TransactionVo> result = service.getTransactionHistory(USER_ID, 0, 999);

        assertEquals(1L, result.getCurrent(), "非法页码应回退为 1");
        assertEquals(50L, result.getSize(), "pageSize 必须封顶 50");
        verify(usersMapper).selectTransactionPage(eq(USER_ID), eq(SUCCESS_STATUS), eq(0L), eq(50));
    }

    @Test
    @DisplayName("深页 offset 计算：page=3、pageSize=20 时 offset=40 传入数据库")
    void getTransactionHistory_deepPage_offsetComputed() {
        when(usersMapper.countTransactionHistory(USER_ID, SUCCESS_STATUS)).thenReturn(100L);
        when(usersMapper.selectTransactionPage(USER_ID, SUCCESS_STATUS, 40L, 20))
                .thenReturn(List.of(paymentVo("-1.00")));

        Page<TransactionVo> result = service.getTransactionHistory(USER_ID, 3, 20);

        assertEquals(3L, result.getCurrent());
        assertEquals(20L, result.getSize());
        verify(usersMapper).selectTransactionPage(eq(USER_ID), eq(SUCCESS_STATUS), eq(40L), eq(20));
    }

    @Test
    @DisplayName("无流水：total=0 时不再下探分页查询，直接返回空 records")
    void getTransactionHistory_emptyTotal_skipsPageQuery() {
        when(usersMapper.countTransactionHistory(USER_ID, SUCCESS_STATUS)).thenReturn(0L);

        Page<TransactionVo> result = service.getTransactionHistory(USER_ID, 1, 10);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(usersMapper, never()).selectTransactionPage(any(), any(), anyLong(), anyInt());
    }

    private TransactionVo rechargeVo(String amount) {
        TransactionVo vo = new TransactionVo();
        vo.setType("recharge");
        vo.setAmount(new BigDecimal(amount));
        vo.setDescription("账户充值");
        vo.setTransactionTime(LocalDateTime.of(2026, 8, 29, 12, 0));
        vo.setStatus("success");
        return vo;
    }

    private TransactionVo paymentVo(String amount) {
        TransactionVo vo = new TransactionVo();
        vo.setType("payment");
        vo.setAmount(new BigDecimal(amount));
        vo.setDescription("洗衣服务消费");
        vo.setTransactionTime(LocalDateTime.of(2026, 8, 28, 9, 30));
        vo.setStatus("success");
        return vo;
    }
}
