package com.smartwash.service.impl;

import com.smartwash.entity.Coupon;
import com.smartwash.entity.UserCoupon;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.mapper.CouponMapper;
import com.smartwash.mapper.OrdersMapper;
import com.smartwash.mapper.UserCouponMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券领取原子性回归测试（评审报告后端 P0 #4）。
 * 闸门语义：
 * 1) 领取先走 increaseIssuedCount 原子限量递增，影响行数 0（已领完/券不存在）→ 抛"优惠券已领完"且不插入 user_coupon；
 * 2) 插入唯一索引冲突（DuplicateKeyException）→ 转友好异常"该优惠券已领取！"；
 * 3) 并发重复领取时插入只允许发生一次（限量 CAS + 唯一索引兜底）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserCouponServiceImpl 领取券限量与防重复领取测试")
class UserCouponServiceImplTest {

    private static final Long COUPON_ID = 5L;
    private static final Long USER_ID = 10L;

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private UserCouponMapper userCouponMapper;

    private UserCouponServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserCouponServiceImpl(couponMapper, ordersMapper);
        // ServiceImpl.save 走父类 baseMapper 字段，构造注入不会填充，手动注入
        ReflectionTestUtils.setField(service, "baseMapper", userCouponMapper);
    }

    private Coupon coupon(int validDays) {
        Coupon coupon = new Coupon();
        coupon.setCouponId(COUPON_ID);
        coupon.setTitle("新客立减");
        coupon.setDiscount(new BigDecimal("5"));
        coupon.setThreshold(new BigDecimal("20"));
        coupon.setValidDays(validDays);
        return coupon;
    }

    @Test
    @DisplayName("领取限量递增影响 0 行（已领完）时抛\"优惠券已领完\"且绝不插入 user_coupon")
    void receiveCoupon_increaseCountZero_rejectedWithoutInsert() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(30));
        when(couponMapper.increaseIssuedCount(COUPON_ID)).thenReturn(0);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.receiveCoupon(COUPON_ID, USER_ID));

        assertEquals("优惠券已领完", ex.getMessage());
        // 关键回归点：限量闸门未过不得插入领取记录（否则超发）
        verify(userCouponMapper, never()).insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("并发插入唯一索引冲突（DuplicateKeyException）转友好异常\"该优惠券已领取！\"")
    void receiveCoupon_duplicateKey_translatedToFriendlyException() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(30));
        when(couponMapper.increaseIssuedCount(COUPON_ID)).thenReturn(1);
        when(userCouponMapper.insert(any(UserCoupon.class))).thenThrow(new DuplicateKeyException("uk_user_coupon"));

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.receiveCoupon(COUPON_ID, USER_ID));

        assertEquals("该优惠券已领取！", ex.getMessage(), "唯一索引兜底冲突必须转为友好提示，不得泄漏 SQL 细节");
    }

    @Test
    @DisplayName("领取成功：限量递增一次、插入领取记录且过期时间为 now + validDays")
    void receiveCoupon_success_insertsWithExpiry() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(30));
        when(couponMapper.increaseIssuedCount(COUPON_ID)).thenReturn(1);
        when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);

        LocalDateTime before = LocalDateTime.now();
        Boolean result = service.receiveCoupon(COUPON_ID, USER_ID);
        LocalDateTime after = LocalDateTime.now();

        assertTrue(result, "首次领取应成功");
        ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponMapper).insert(captor.capture());
        UserCoupon inserted = captor.getValue();
        assertEquals(USER_ID, inserted.getUserId());
        assertEquals(COUPON_ID, inserted.getCouponId());
        // 过期时间 = 领取时间 + 券模板 validDays（取调用前后的时间夹逼验证）
        assertTrue(!inserted.getExpiredAt().isBefore(before.plusDays(30)) && !inserted.getExpiredAt().isAfter(after.plusDays(30)),
                "过期时间必须等于领取时间加券模板有效天数");
        verify(couponMapper, times(1)).increaseIssuedCount(COUPON_ID);
    }

    @Test
    @DisplayName("领取不存在的优惠券：直接拒绝，不触发限量递增")
    void receiveCoupon_couponMissing_rejected() {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(null);

        CustomExceptions ex = assertThrows(CustomExceptions.class, () -> service.receiveCoupon(COUPON_ID, USER_ID));

        assertEquals("优惠券不存在", ex.getMessage());
        verify(couponMapper, never()).increaseIssuedCount(any());
        verify(userCouponMapper, never()).insert(any(UserCoupon.class));
    }

    @Test
    @DisplayName("并发重复领取限量券：仅限量 CAS 赢家插入 1 条领取记录，其余全部抛\"优惠券已领完\"")
    void receiveCoupon_concurrent_onlyOneInsert() throws Exception {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon(30));
        // increaseIssuedCount 条件更新模拟 DB 行级原子性：限量 1，全部并发领取中只有 1 个拿到影响行数 1
        AtomicInteger remainingQuota = new AtomicInteger(1);
        when(couponMapper.increaseIssuedCount(eq(COUPON_ID))).thenAnswer(inv -> remainingQuota.compareAndSet(1, 0) ? 1 : 0);
        when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (service.receiveCoupon(COUPON_ID, USER_ID)) {
                        successCount.incrementAndGet();
                    }
                } catch (CustomExceptions e) {
                    if ("优惠券已领完".equals(e.getMessage())) {
                        soldOutCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发领取应在 10 秒内全部结束");
        pool.shutdownNow();

        assertEquals(1, successCount.get(), "限量 1 张的券并发领取只允许成功 1 次（防超发）");
        assertEquals(threads - 1, soldOutCount.get(), "其余请求必须全部被限量闸门以\"优惠券已领完\"拒绝");
        verify(userCouponMapper, times(1)).insert(any(UserCoupon.class));
    }
}
