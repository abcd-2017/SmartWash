package com.smartwash.mapper;

import com.smartwash.entity.Payments;
import com.smartwash.entity.RechargeRecords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 资金流水逻辑删除回归测试（payments / recharge_records）。
 *
 * <p>背景：资金流水原为物理删除语义（删除入口已摘除）。落地 {@code @TableLogic} 后：
 * <ul>
 *   <li>插入默认 is_delete = 0；</li>
 *   <li>{@code deleteById} 必须转为 {@code UPDATE is_delete = 1}，物理行保留（审计凭证不可物理删除）；</li>
 *   <li>MyBatis-Plus 查询自动追加 {@code is_delete = 0}，已删流水不可见；</li>
 *   <li>原生 SQL 统计口径（Dashboard 收入、用户交易流水）必须同步排除已删流水。</li>
 * </ul>
 * 测试使用 test profile（H2 + schema.sql），{@code @Transactional} 保证用例回滚不污染共享内存库。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("资金流水逻辑删除回归测试（payments/recharge_records）")
class FinancialLogicalDeleteIntegrationTest {

    private static final Long USER_ID = 9101L;

    @Autowired
    private PaymentsMapper paymentsMapper;
    @Autowired
    private RechargeRecordsMapper rechargeRecordsMapper;
    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private DashboardMapper dashboardMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Payments payment(Long orderId, String amount, String status, String outTradeNo) {
        Payments p = new Payments();
        p.setOrderId(orderId);
        p.setUserId(USER_ID);
        p.setAmount(new BigDecimal(amount));
        p.setPaymentMethod("2");
        p.setOutTradeNo(outTradeNo);
        p.setStatus(status);
        p.setPaidAt(LocalDateTime.now());
        paymentsMapper.insert(p);
        return p;
    }

    private RechargeRecords recharge(String amount, String outTradeNo) {
        RechargeRecords r = new RechargeRecords();
        r.setUserId(USER_ID);
        r.setAmount(new BigDecimal(amount));
        r.setRechargeType("1");
        r.setStatus("1");
        r.setOutTradeNo(outTradeNo);
        rechargeRecordsMapper.insert(r);
        return r;
    }

    @Test
    @DisplayName("payments：新增默认 is_delete=0；deleteById 转逻辑删除，查询不可见但物理行保留")
    void payments_deleteById_logicalDelete_keepsPhysicalRow() {
        Payments p = payment(9001L, "12.34", "1", "PAY-LOGICAL-TEST-0001");

        assertNotNull(p.getPaymentId());
        Payments loaded = paymentsMapper.selectById(p.getPaymentId());
        assertNotNull(loaded, "新增流水必须可按 ID 查询");
        assertEquals(0, loaded.getIsDelete(), "新增流水默认 is_delete=0");

        assertEquals(1, paymentsMapper.deleteById(p.getPaymentId()), "deleteById 应命中 1 行");

        assertNull(paymentsMapper.selectById(p.getPaymentId()), "逻辑删除后按 ID 查询不可见");
        List<Payments> remaining = paymentsMapper.selectList(null);
        assertFalse(remaining.stream().anyMatch(x -> p.getPaymentId().equals(x.getPaymentId())),
                "逻辑删除后列表查询不可见");

        Integer rawFlag = jdbcTemplate.queryForObject(
                "select is_delete from payments where payment_id = ?", Integer.class, p.getPaymentId());
        assertEquals(1, rawFlag, "物理行必须保留且 is_delete=1（资金流水禁止物理删除）");
    }

    @Test
    @DisplayName("recharge_records：deleteById 转逻辑删除，查询不可见但物理行保留")
    void rechargeRecords_deleteById_logicalDelete_keepsPhysicalRow() {
        RechargeRecords r = recharge("45.67", "RCH-LOGICAL-TEST-0001");

        assertNotNull(r.getRecordId());
        assertEquals(0, rechargeRecordsMapper.selectById(r.getRecordId()).getIsDelete(),
                "新增充值流水默认 is_delete=0");

        assertEquals(1, rechargeRecordsMapper.deleteById(r.getRecordId()), "deleteById 应命中 1 行");

        assertNull(rechargeRecordsMapper.selectById(r.getRecordId()), "逻辑删除后按 ID 查询不可见");
        assertFalse(rechargeRecordsMapper.selectList(null).stream()
                        .anyMatch(x -> r.getRecordId().equals(x.getRecordId())),
                "逻辑删除后列表查询不可见");

        Integer rawFlag = jdbcTemplate.queryForObject(
                "select is_delete from recharge_records where record_id = ?", Integer.class, r.getRecordId());
        assertEquals(1, rawFlag, "物理行必须保留且 is_delete=1（资金流水禁止物理删除）");
    }

    @Test
    @DisplayName("Dashboard 今日收入：已逻辑删除的支付流水不计入统计")
    void dashboardIncome_excludesLogicallyDeletedPayment() {
        String todayStart = LocalDate.now().atStartOfDay().toString();
        BigDecimal before = dashboardMapper.sumTodayIncome(todayStart);

        Payments p = payment(9002L, "55.55", "1", "PAY-LOGICAL-TEST-0002");
        BigDecimal afterInsert = dashboardMapper.sumTodayIncome(todayStart);

        paymentsMapper.deleteById(p.getPaymentId());
        BigDecimal afterDelete = dashboardMapper.sumTodayIncome(todayStart);

        assertEquals(0, new BigDecimal("55.55").compareTo(afterInsert.subtract(before)),
                "未删除时应计入收入");
        assertEquals(0, before.compareTo(afterDelete), "逻辑删除后收入统计必须回落，不得计入已删流水");
    }

    @Test
    @DisplayName("用户交易流水：已逻辑删除的充值/支付流水不计入总数与分页")
    void transactionHistory_excludesLogicallyDeletedRecords() {
        // 使用独占 userId，保证该用户的流水仅由本用例构造，断言不受其它数据干扰
        Long userId = 990001L;
        RechargeRecords r = new RechargeRecords();
        r.setUserId(userId);
        r.setAmount(new BigDecimal("30.00"));
        r.setRechargeType("1");
        r.setStatus("1");
        r.setOutTradeNo("RCH-LOGICAL-TEST-0002");
        rechargeRecordsMapper.insert(r);

        Payments p = new Payments();
        p.setOrderId(9003L);
        p.setUserId(userId);
        p.setAmount(new BigDecimal("20.00"));
        p.setPaymentMethod("2");
        p.setOutTradeNo("PAY-LOGICAL-TEST-0003");
        p.setStatus("1");
        p.setPaidAt(LocalDateTime.now());
        paymentsMapper.insert(p);

        long afterInsert = usersMapper.countTransactionHistory(userId, "1");
        List<com.smartwash.vo.users.TransactionVo> pageBefore =
                usersMapper.selectTransactionPage(userId, "1", 0L, 100);

        rechargeRecordsMapper.deleteById(r.getRecordId());
        paymentsMapper.deleteById(p.getPaymentId());

        long afterDelete = usersMapper.countTransactionHistory(userId, "1");
        List<com.smartwash.vo.users.TransactionVo> pageAfter =
                usersMapper.selectTransactionPage(userId, "1", 0L, 100);

        assertEquals(2, afterInsert - afterDelete, "已删的 1 条充值 + 1 条支付必须从交易流水总数中剔除");
        assertEquals(2, pageBefore.size(), "删除前该用户应有 2 条流水");
        assertEquals(0, pageAfter.size(), "删除后分页结果不得包含已逻辑删除的流水");
    }
}
