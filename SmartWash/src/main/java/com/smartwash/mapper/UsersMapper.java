package com.smartwash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.entity.Users;
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
    List<Users> getUsersByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    Users getUserByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    void addUserBalance(@Param("userId")Long userId, @Param("balance")BigDecimal balance);
}
