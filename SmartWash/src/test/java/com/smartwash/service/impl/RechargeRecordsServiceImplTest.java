package com.smartwash.service.impl;

import com.smartwash.common.PaymentStatus;
import com.smartwash.entity.RechargeRecords;
import com.smartwash.entity.Users;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.recharge_records.UserRechargeFrom;
import com.smartwash.mapper.RechargeRecordsMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.PaymentGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充值回调幂等回归测试（评审报告后端 P0 #5）。
 * 闸门语义：handleRechargeSuccess 以 markSuccess（处理中→已到账）条件更新为幂等闸门，
 * 影响行数 0 说明重复回调/重放已处理过，必须直接返回且绝不重复 incrUserBalance 加钱。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RechargeRecordsServiceImpl 充值幂等闸门测试")
class RechargeRecordsServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final String OUT_TRADE_NO = "RCH20260829TEST0001";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private UsersMapper usersMapper;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private RechargeRecordsMapper rechargeRecordsMapper;

    private RechargeRecordsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RechargeRecordsServiceImpl(usersMapper, paymentGatewayService);
        // ServiceImpl 父类 baseMapper（save/selectOne/markSuccess 走它），构造注入不会填充，手动注入
        ReflectionTestUtils.setField(service, "baseMapper", rechargeRecordsMapper);
    }

    private RechargeRecords processingRecord() {
        RechargeRecords record = new RechargeRecords();
        record.setUserId(USER_ID);
        record.setAmount(AMOUNT);
        record.setStatus(PaymentStatus.PROCESSING.getStatus());
        record.setOutTradeNo(OUT_TRADE_NO);
        record.setRechargeType("1");
        return record;
    }

    // ==================== handleRechargeSuccess 幂等闸门 ====================

    @Test
    @DisplayName("handleRechargeSuccess：幂等闸门 0 行（重复回调/重放）时返回成功且绝不重复加余额")
    void handleRechargeSuccess_gateMiss_neverDoubleCredit() {
        when(rechargeRecordsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(0);

        Boolean result = service.handleRechargeSuccess(OUT_TRADE_NO);

        assertTrue(result, "幂等重放应按已处理成功返回，不影响网关重试语义");
        // 关键回归点：闸门未抢到时连记录反查都不应发生，更不允许加钱
        verify(rechargeRecordsMapper, never()).selectOne(any());
        verify(usersMapper, never()).incrUserBalance(anyLong(), any());
    }

    @Test
    @DisplayName("handleRechargeSuccess：首次回调（闸门 1 行）按库内流水金额加余额一次")
    void handleRechargeSuccess_firstCallback_creditsOnce() {
        when(rechargeRecordsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(1);
        when(rechargeRecordsMapper.selectOne(any())).thenReturn(processingRecord());
        when(usersMapper.incrUserBalance(USER_ID, AMOUNT)).thenReturn(1);

        Boolean result = service.handleRechargeSuccess(OUT_TRADE_NO);

        assertTrue(result, "首次回调应充值成功");
        verify(usersMapper, times(1)).incrUserBalance(USER_ID, AMOUNT);
    }

    @Test
    @DisplayName("handleRechargeSuccess：加余额影响 0 行（用户不存在）时抛异常，调用方事务回滚闸门更新")
    void handleRechargeSuccess_userMissing_throws() {
        when(rechargeRecordsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(1);
        when(rechargeRecordsMapper.selectOne(any())).thenReturn(processingRecord());
        when(usersMapper.incrUserBalance(USER_ID, AMOUNT)).thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.handleRechargeSuccess(OUT_TRADE_NO));

        assertEquals("充值入账失败，用户不存在", ex.getMessage());
    }

    @Test
    @DisplayName("handleRechargeSuccess：闸门命中但充值记录不存在/金额非法时抛\"充值记录异常\"")
    void handleRechargeSuccess_recordMissingOrIllegalAmount_throws() {
        when(rechargeRecordsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(1);
        when(rechargeRecordsMapper.selectOne(any())).thenReturn(null);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.handleRechargeSuccess(OUT_TRADE_NO));

        assertEquals("充值记录异常", ex.getMessage());
        verify(usersMapper, never()).incrUserBalance(anyLong(), any());
    }

    @Test
    @DisplayName("handleRechargeSuccess：记录金额为 0/负数时视为异常拒绝入账")
    void handleRechargeSuccess_zeroOrNegativeAmount_rejected() {
        RechargeRecords bad = processingRecord();
        bad.setAmount(BigDecimal.ZERO);
        when(rechargeRecordsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(1);
        when(rechargeRecordsMapper.selectOne(any())).thenReturn(bad);

        assertThrows(CustomExceptions.class, () -> service.handleRechargeSuccess(OUT_TRADE_NO));
        verify(usersMapper, never()).incrUserBalance(anyLong(), any());
    }

    @Test
    @DisplayName("并发重复回调同一 outTradeNo：仅幂等闸门赢家执行一次加余额")
    void handleRechargeSuccess_concurrent_onlyWinnerCredits() throws Exception {
        // markSuccess 条件更新模拟 DB 行级原子性：全部并发回调中只有 1 个拿到影响行数 1
        AtomicInteger remainingHits = new AtomicInteger(1);
        when(rechargeRecordsMapper.markSuccess(OUT_TRADE_NO)).thenAnswer(inv -> remainingHits.compareAndSet(1, 0) ? 1 : 0);
        when(rechargeRecordsMapper.selectOne(any())).thenReturn(processingRecord());
        when(usersMapper.incrUserBalance(USER_ID, AMOUNT)).thenReturn(1);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    service.handleRechargeSuccess(OUT_TRADE_NO);
                } catch (CustomExceptions ignored) {
                    // 输家不应走到加钱，此处仅兜底
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发回调应在 10 秒内全部结束");
        pool.shutdownNow();

        // 关键资金断言：无论多少次并发重复回调，加余额只允许发生一次
        verify(usersMapper, times(1)).incrUserBalance(USER_ID, AMOUNT);
    }

    // ==================== userRecharge + 桩网关同步回调链路 ====================

    @Test
    @DisplayName("userRecharge：桩网关同步回调后完整执行 插处理中记录→闸门→加余额 链路")
    void userRecharge_stubGatewaySyncCallback_completesFullChain() {
        Users user = new Users();
        user.setUserId(USER_ID);
        user.setBalance(BigDecimal.ZERO);
        when(usersMapper.selectById(USER_ID)).thenReturn(user);
        doAnswer(inv -> {
            PaymentGatewayService.PaymentResultCallback callback = inv.getArgument(4);
            callback.onPaymentSuccess(inv.getArgument(0, String.class));
            return "STUB_PREPAY_" + inv.getArgument(0, String.class);
        }).when(paymentGatewayService).createPayment(anyString(), any(BigDecimal.class), anyString(), anyString(), any());
        when(rechargeRecordsMapper.markSuccess(anyString())).thenReturn(1);
        when(rechargeRecordsMapper.selectOne(any())).thenReturn(processingRecord());
        when(usersMapper.incrUserBalance(USER_ID, AMOUNT)).thenReturn(1);

        Boolean result = service.userRecharge(rechargeFrom(AMOUNT), USER_ID);

        assertTrue(result, "充值全链路应成功");
        // 充值记录断言：金额服务端兜底、初始状态处理中、outTradeNo 以 RCH 前缀生成
        ArgumentCaptor<RechargeRecords> recordCaptor = ArgumentCaptor.forClass(RechargeRecords.class);
        verify(rechargeRecordsMapper).insert(recordCaptor.capture());
        RechargeRecords saved = recordCaptor.getValue();
        assertEquals(0, saved.getAmount().compareTo(AMOUNT), "到账金额必须以服务端校验金额为准");
        assertEquals(PaymentStatus.PROCESSING.getStatus(), saved.getStatus(), "两段式充值初始状态必须是处理中");
        assertTrue(saved.getOutTradeNo().startsWith("RCH"), "out_trade_no 必须以 RCH 前缀生成（幂等键）");
        verify(usersMapper, times(1)).incrUserBalance(USER_ID, AMOUNT);
    }

    @Test
    @DisplayName("userRecharge：金额为 0/负数时拒绝，且不得插入充值记录")
    void userRecharge_nonPositiveAmount_rejected() {
        Users user = new Users();
        user.setUserId(USER_ID);
        when(usersMapper.selectById(USER_ID)).thenReturn(user);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.userRecharge(rechargeFrom(BigDecimal.ZERO), USER_ID));

        assertEquals("充值金额必须大于0", ex.getMessage());
        verify(rechargeRecordsMapper, never()).insert(any(RechargeRecords.class));
        verify(paymentGatewayService, never()).createPayment(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("userRecharge：金额超过两位小数时拒绝（服务端金额兜底校验）")
    void userRecharge_threeDecimalScale_rejected() {
        Users user = new Users();
        user.setUserId(USER_ID);
        when(usersMapper.selectById(USER_ID)).thenReturn(user);

        assertThrows(CustomExceptions.class, () -> service.userRecharge(rechargeFrom(new BigDecimal("1.234")), USER_ID));
        verify(rechargeRecordsMapper, never()).insert(any(RechargeRecords.class));
    }

    @Test
    @DisplayName("userRecharge：金额超过单笔上限（10000 元）时拒绝，不插入记录、不调用网关")
    void userRecharge_overMaxAmount_rejected() {
        Users user = new Users();
        user.setUserId(USER_ID);
        when(usersMapper.selectById(USER_ID)).thenReturn(user);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.userRecharge(rechargeFrom(new BigDecimal("10000.01")), USER_ID));

        assertTrue(ex.getMessage().contains("单笔充值金额不能超过"), "超限充值必须返回含上限金额的友好提示");
        verify(rechargeRecordsMapper, never()).insert(any(RechargeRecords.class));
        verify(paymentGatewayService, never()).createPayment(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("userRecharge：用户不存在时拒绝")
    void userRecharge_userMissing_rejected() {
        when(usersMapper.selectById(USER_ID)).thenReturn(null);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.userRecharge(rechargeFrom(AMOUNT), USER_ID));

        assertEquals("用户不存在", ex.getMessage());
    }

    private UserRechargeFrom rechargeFrom(BigDecimal amount) {
        UserRechargeFrom from = new UserRechargeFrom();
        from.setAmount(amount);
        from.setRechargeType("1");
        return from;
    }
}
