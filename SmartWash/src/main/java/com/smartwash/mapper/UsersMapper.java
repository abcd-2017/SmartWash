package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.entity.Users;
import com.smartwash.vo.users.TransactionVo;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface UsersMapper extends BaseMapper<Users> {
    Users getUserByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    int decrUserBalance(Long userId, BigDecimal amount);

    /**
     * 交易流水分页查询（充值+支付 UNION ALL 合并，数据库层 LIMIT/OFFSET，评审报告后端 #24）。
     * 字段映射与 {@link TransactionVo} 现有语义一致：type/amount(消费为负)/description/transactionTime/status。
     *
     * @param userId        用户 ID
     * @param paymentStatus 支付流水的状态过滤值（PaymentStatus.SUCCESS）
     * @param offset        偏移量（(page-1)*pageSize）
     * @param limit         每页条数
     */
    List<TransactionVo> selectTransactionPage(@Param("userId") Long userId,
                                              @Param("paymentStatus") String paymentStatus,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit);

    /**
     * 交易流水总数（与 {@link #selectTransactionPage} 同口径）
     */
    long countTransactionHistory(@Param("userId") Long userId, @Param("paymentStatus") String paymentStatus);

    /**
     * 用户余额增加（退款/入账）：无条件累加，金额由调用方以库内流水计算，禁止信任前端传入
     *
     * @param userId 用户 ID
     * @param amount 增加金额（必须为非负）
     * @return 影响行数
     */
    int incrUserBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
