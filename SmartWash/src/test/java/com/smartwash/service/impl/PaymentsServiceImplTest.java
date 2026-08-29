package com.smartwash.service.impl;

import com.smartwash.common.OrderStatus;
import com.smartwash.common.PayType;
import com.smartwash.common.PaymentStatus;
import com.smartwash.entity.Orders;
import com.smartwash.entity.Users;
import com.smartwash.entity.Payments;
import com.smartwash.entity.UserCoupon;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.payment.PaymentOrderFrom;
import com.smartwash.mapper.CouponMapper;
import com.smartwash.mapper.OrdersMapper;
import com.smartwash.mapper.PaymentsMapper;
import com.smartwash.mapper.UserCouponMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.PaymentGatewayService;
import com.smartwash.task.OrderTimeoutManager;
import com.smartwash.utils.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付流程 CAS 闸门与幂等回归测试（评审报告后端 P0 #3、#6）。
 * 闸门语义：
 * 1) paymentOrder + 桩网关同步回调 → handlePaymentSuccess 完整链路；
 * 2) markUsed 影响行数 0 → "优惠券已被使用"异常，禁止并发复用同一张券；
 * 3) handlePaymentSuccess 以 markSuccess（处理中→已支付）条件更新为幂等闸门，
 *    影响行数 0 时不得重复扣款/核销/改订单。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentsServiceImpl 支付幂等与优惠券核销闸门测试")
class PaymentsServiceImplTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long USER_COUPON_ID = 77L;
    private static final String OUT_TRADE_NO = "PAY20260829TEST0001";

    @Mock
    private UsersMapper usersMapper;
    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private CouponMapper couponMapper;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private OrderTimeoutManager orderTimeoutManager;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private PaymentsMapper paymentsMapper;

    private PaymentsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentsServiceImpl();
        ReflectionTestUtils.setField(service, "usersMapper", usersMapper);
        ReflectionTestUtils.setField(service, "ordersMapper", ordersMapper);
        ReflectionTestUtils.setField(service, "couponMapper", couponMapper);
        ReflectionTestUtils.setField(service, "userCouponMapper", userCouponMapper);
        ReflectionTestUtils.setField(service, "orderTimeoutManager", orderTimeoutManager);
        ReflectionTestUtils.setField(service, "paymentGatewayService", paymentGatewayService);
        // ServiceImpl 父类 baseMapper（save/selectOne/markSuccess 走它），构造注入不会填充，手动注入
        ReflectionTestUtils.setField(service, "baseMapper", paymentsMapper);
    }

    private LoginUser loginUser() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_user"));
        return new LoginUser(USER_ID, "user-13800138000", "pwd", "13800138000", "user", authorities);
    }

    private Users userWithBalance(String balance) {
        Users user = new Users();
        user.setUserId(USER_ID);
        user.setBalance(new BigDecimal(balance));
        return user;
    }

    private Orders pendingOrder() {
        Orders order = new Orders();
        order.setOrderId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getStatus());
        order.setTotalPrice(new BigDecimal("50"));
        order.setPayPrice(new BigDecimal("50"));
        order.setOrderNo("SNOW123");
        return order;
    }

    private Payments processingPayment(BigDecimal amount) {
        Payments payment = new Payments();
        payment.setOrderId(ORDER_ID);
        payment.setUserId(USER_ID);
        payment.setAmount(amount);
        payment.setOutTradeNo(OUT_TRADE_NO);
        payment.setStatus(PaymentStatus.PROCESSING.getStatus());
        return payment;
    }

    /** 让桩网关在 createPayment 时于同一调用栈同步回调"支付成功"，复现真实桩链路 */
    private void stubGatewaySyncCallback() {
        doAnswer(inv -> {
            PaymentGatewayService.PaymentResultCallback callback = inv.getArgument(4);
            callback.onPaymentSuccess(inv.getArgument(0, String.class));
            return "STUB_PREPAY_" + inv.getArgument(0, String.class);
        }).when(paymentGatewayService).createPayment(anyString(), any(BigDecimal.class), anyString(), anyString(), any());
    }

    // ==================== paymentOrder + 桩网关同步回调链路 ====================

    @Test
    @DisplayName("paymentOrder：桩网关同步回调后完整执行 闸门→扣款→订单流转→取消超时 链路")
    void paymentOrder_stubGatewaySyncCallback_completesFullChain() {
        Orders order = pendingOrder();
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(order);
        when(usersMapper.selectById(USER_ID)).thenReturn(userWithBalance("100"));
        stubGatewaySyncCallback();
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("50")));
        // outTradeNo 由 paymentOrder 内部生成（PAY+日期+雪花ID），测试无法预知具体值，用 anyString 匹配
        when(paymentsMapper.markSuccess(anyString())).thenReturn(1);
        when(usersMapper.decrUserBalance(USER_ID, new BigDecimal("50"))).thenReturn(1);

        Boolean result = service.paymentOrder(loginUser(), paymentFrom(null));

        assertTrue(result, "余额充足且无并发冲突时支付应成功");
        // 支付回调链路完整性断言：幂等闸门命中 1 次，且与传给网关的 outTradeNo 完全一致
        ArgumentCaptor<String> outTradeNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentGatewayService).createPayment(outTradeNoCaptor.capture(), any(BigDecimal.class), anyString(), anyString(), any());
        verify(paymentsMapper, times(1)).markSuccess(eq(outTradeNoCaptor.getValue()));
        verify(usersMapper, times(1)).decrUserBalance(USER_ID, new BigDecimal("50"));
        verify(userCouponMapper, never()).markUsed(anyLong(), anyLong(), anyLong());
        assertEquals(OrderStatus.PENDING_SHIPMENT.getStatus(), order.getStatus(), "支付成功后订单必须流转到待寄件");
        assertNotNull(order.getPickupCode(), "支付成功后必须生成取件码");
        verify(orderTimeoutManager, times(1)).cancelTimeout(ORDER_ID);

        // 支付流水断言：金额以后端计算为准、初始状态为处理中、outTradeNo 以 PAY 前缀落库
        ArgumentCaptor<Payments> paymentCaptor = ArgumentCaptor.forClass(Payments.class);
        verify(paymentsMapper).insert(paymentCaptor.capture());
        Payments saved = paymentCaptor.getValue();
        assertEquals(0, saved.getAmount().compareTo(new BigDecimal("50")), "支付金额必须取后端计算的应付金额");
        assertEquals(PaymentStatus.PROCESSING.getStatus(), saved.getStatus(), "两段式支付初始状态必须是处理中");
        assertTrue(saved.getOutTradeNo().startsWith("PAY"), "out_trade_no 必须以 PAY 前缀生成（幂等键）");
    }

    @Test
    @DisplayName("paymentOrder：markUsed 返回 0（优惠券并发已被使用）时抛\"优惠券已被使用\"且订单不流转")
    void paymentOrder_couponMarkUsedZero_throwsCouponUsed() {
        Orders order = pendingOrder();
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(order);
        when(usersMapper.selectById(USER_ID)).thenReturn(userWithBalance("100"));
        // 优惠券归属当前用户且未使用、未过期，门槛 20 < 总价 50，优惠 5 → 应付 45
        when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(ownerValidCoupon());
        com.smartwash.entity.Coupon coupon = new com.smartwash.entity.Coupon();
        coupon.setCouponId(8L);
        coupon.setThreshold(new BigDecimal("20"));
        coupon.setDiscount(new BigDecimal("5"));
        when(couponMapper.selectById(8L)).thenReturn(coupon);

        stubGatewaySyncCallback();
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("45")));
        when(paymentsMapper.markSuccess(anyString())).thenReturn(1);
        when(usersMapper.decrUserBalance(USER_ID, new BigDecimal("45"))).thenReturn(1);
        // 关键桩：核销优惠券条件更新影响 0 行（并发下该券已被另一笔订单使用）
        when(userCouponMapper.markUsed(USER_COUPON_ID, USER_ID, ORDER_ID)).thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.paymentOrder(loginUser(), paymentFrom(USER_COUPON_ID)));

        assertEquals("优惠券已被使用", ex.getMessage(), "同一张券被并发第二笔订单核销必须被闸门拒绝");
        // 支付记录 pre-gateway updateById（落应付金额）发生 1 次；回调内的订单流转更新不得执行
        verify(ordersMapper, times(1)).updateById(any(Orders.class));
        assertNotEquals(OrderStatus.PENDING_SHIPMENT.getStatus(), order.getStatus(),
                "核销失败时订单不得流转到待寄件（真实事务中整体回滚）");
        verify(orderTimeoutManager, never()).cancelTimeout(anyLong());
    }

    private UserCoupon ownerValidCoupon() {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserCouponId(USER_COUPON_ID);
        userCoupon.setUserId(USER_ID);
        userCoupon.setCouponId(8L);
        userCoupon.setIsUsed(false);
        userCoupon.setExpiredAt(LocalDateTime.now().plusDays(1));
        return userCoupon;
    }

    private PaymentOrderFrom paymentFrom(Long couponId) {
        PaymentOrderFrom from = new PaymentOrderFrom();
        from.setOrderId(ORDER_ID);
        // 支付方式必须在 PayType 白名单内（1-钱包/2-支付宝/3-微信），与 payments.payment_method 取值对齐
        from.setPaymentType(PayType.WECHAT_PAY.getType());
        from.setUserCouponId(couponId);
        return from;
    }

    @Test
    @DisplayName("paymentOrder：支付方式不在 PayType 白名单时直接拒绝，不落库、不调用网关")
    void paymentOrder_invalidPaymentType_rejected() {
        PaymentOrderFrom from = paymentFrom(null);
        from.setPaymentType("bitcoin");

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.paymentOrder(loginUser(), from));

        assertTrue(ex.getMessage().contains("不支持的支付方式"), "非法支付方式必须返回友好提示");
        // 关键回归点：白名单拦截发生在任何资金/落库操作之前
        verify(ordersMapper, never()).selectByIdForUpdate(anyLong());
        verify(paymentsMapper, never()).insert(any(Payments.class));
        verify(paymentGatewayService, never()).createPayment(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("paymentOrder：同步回调被拒（返回 false，订单已被取消）时必须抛异常，不得提示支付成功")
    void paymentOrder_callbackRejected_throwsInsteadOfSuccess() {
        Orders order = pendingOrder();
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(order);
        when(usersMapper.selectById(USER_ID)).thenReturn(userWithBalance("100"));
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("50")));
        // 网关同步回调走 handlePaymentSuccess；订单已被取消 → 回调拒绝入账返回 false（支付记录置失败）
        Orders canceled = pendingOrder();
        canceled.setStatus(OrderStatus.CANCELED.getStatus());
        doAnswer(inv -> {
            PaymentGatewayService.PaymentResultCallback callback = inv.getArgument(4);
            when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(canceled);
            callback.onPaymentSuccess(inv.getArgument(0, String.class));
            return "STUB_PREPAY_" + inv.getArgument(0, String.class);
        }).when(paymentGatewayService).createPayment(anyString(), any(BigDecimal.class), anyString(), anyString(), any());

        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> service.paymentOrder(loginUser(), paymentFrom(null)));

        assertEquals("支付失败：订单状态已变更，请刷新后重试", ex.getMessage(),
                "回调被拒时必须穿透失败结果，禁止无条件返回支付成功");
        verify(paymentsMapper, times(1)).markFail(anyString());
    }

    // ==================== handlePaymentSuccess 幂等 ====================

    @Test
    @DisplayName("handlePaymentSuccess：幂等闸门 0 行（并发回调已处理）时返回成功且绝不重复扣款")
    void handlePaymentSuccess_gateMiss_noDoubleCharge() {
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("50")));
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(pendingOrder());
        when(paymentsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(0);

        Boolean result = service.handlePaymentSuccess(OUT_TRADE_NO);

        assertTrue(result, "幂等重放应按已处理成功返回");
        verify(usersMapper, never()).decrUserBalance(anyLong(), any());
        verify(userCouponMapper, never()).markUsed(anyLong(), anyLong(), anyLong());
        verify(ordersMapper, never()).updateById(any(Orders.class));
        verify(orderTimeoutManager, never()).cancelTimeout(anyLong());
    }

    @Test
    @DisplayName("handlePaymentSuccess：支付记录已处于终态（重复投递快路径）时直接返回，不再触发闸门与扣款")
    void handlePaymentSuccess_terminalState_fastPath() {
        Payments done = processingPayment(new BigDecimal("50"));
        done.setStatus(PaymentStatus.SUCCESS.getStatus());
        when(paymentsMapper.selectOne(any())).thenReturn(done);

        Boolean result = service.handlePaymentSuccess(OUT_TRADE_NO);

        assertTrue(result);
        verify(paymentsMapper, never()).markSuccess(anyString());
        verify(ordersMapper, never()).selectByIdForUpdate(anyLong());
        verify(usersMapper, never()).decrUserBalance(anyLong(), any());
    }

    @Test
    @DisplayName("handlePaymentSuccess：订单已非待支付（已被取消/流转）时拒绝入账并将支付记录置失败")
    void handlePaymentSuccess_orderCanceled_marksFailAndRejects() {
        Orders canceled = pendingOrder();
        canceled.setStatus(OrderStatus.CANCELED.getStatus());
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("50")));
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(canceled);
        when(paymentsMapper.markFail(OUT_TRADE_NO)).thenReturn(1);

        Boolean result = service.handlePaymentSuccess(OUT_TRADE_NO);

        assertEquals(Boolean.FALSE, result, "订单已取消时回调必须拒绝入账");
        verify(paymentsMapper, times(1)).markFail(OUT_TRADE_NO);
        verify(paymentsMapper, never()).markSuccess(anyString());
        verify(usersMapper, never()).decrUserBalance(anyLong(), any());
    }

    @Test
    @DisplayName("handlePaymentSuccess：条件扣款影响 0 行（余额不足）时抛异常")
    void handlePaymentSuccess_insufficientBalance_throws() {
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("50")));
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(pendingOrder());
        when(paymentsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(1);
        when(usersMapper.decrUserBalance(USER_ID, new BigDecimal("50"))).thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.handlePaymentSuccess(OUT_TRADE_NO));

        assertEquals("余额不足或扣减失败", ex.getMessage());
        verify(ordersMapper, never()).updateById(any(Orders.class));
    }

    @Test
    @DisplayName("handlePaymentSuccess：订单使用优惠券时回调链路必须原子核销该券")
    void handlePaymentSuccess_withCoupon_marksUsedAtomically() {
        Orders order = pendingOrder();
        order.setUserCouponId(USER_COUPON_ID);
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("45")));
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(order);
        when(paymentsMapper.markSuccess(OUT_TRADE_NO)).thenReturn(1);
        when(usersMapper.decrUserBalance(USER_ID, new BigDecimal("45"))).thenReturn(1);
        when(userCouponMapper.markUsed(USER_COUPON_ID, USER_ID, ORDER_ID)).thenReturn(1);

        Boolean result = service.handlePaymentSuccess(OUT_TRADE_NO);

        assertTrue(result);
        verify(userCouponMapper, times(1)).markUsed(USER_COUPON_ID, USER_ID, ORDER_ID);
        assertEquals(OrderStatus.PENDING_SHIPMENT.getStatus(), order.getStatus());
        verify(orderTimeoutManager, times(1)).cancelTimeout(ORDER_ID);
    }

    @Test
    @DisplayName("并发重复回调同一 outTradeNo：仅幂等闸门赢家执行一次扣款与订单流转")
    void handlePaymentSuccess_concurrent_onlyWinnerCharges() throws Exception {
        when(paymentsMapper.selectOne(any())).thenReturn(processingPayment(new BigDecimal("50")));
        when(ordersMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(pendingOrder());
        // markSuccess 条件更新模拟 DB 行级原子性：全部并发回调中只有 1 个拿到影响行数 1
        AtomicInteger remainingHits = new AtomicInteger(1);
        when(paymentsMapper.markSuccess(anyString())).thenAnswer(inv -> remainingHits.compareAndSet(1, 0) ? 1 : 0);
        when(usersMapper.decrUserBalance(USER_ID, new BigDecimal("50"))).thenReturn(1);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    service.handlePaymentSuccess(OUT_TRADE_NO);
                } catch (CustomExceptions ignored) {
                    // 真实场景中输家不会走到扣款，此处仅兜底不干扰断言
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

        verify(usersMapper, times(1)).decrUserBalance(USER_ID, new BigDecimal("50"));
        verify(orderTimeoutManager, times(1)).cancelTimeout(ORDER_ID);
    }
}
