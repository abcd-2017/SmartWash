package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.entity.Users;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

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
     * 用户余额增加（退款/入账）：无条件累加，金额由调用方以库内流水计算，禁止信任前端传入
     *
     * @param userId 用户 ID
     * @param amount 增加金额（必须为非负）
     * @return 影响行数
     */
    int incrUserBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
