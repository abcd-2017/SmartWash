package com.smartwash.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.points.entity.PointAccount;
import com.smartwash.points.vo.LeaderboardUserVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分账户 Mapper。
 * 余额只允许经 {@link #addPoints} 条件 UPDATE 单语句自增，禁止"先查再改"。
 */
public interface PointAccountMapper extends BaseMapper<PointAccount> {

    /**
     * 按用户查询账户（懒创建前的存在性检查）
     */
    PointAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 条件 UPDATE 加分（单语句自增，InnoDB 行锁保证并发安全，不存在读改写竞态）。
     *
     * @param userId 用户 ID
     * @param delta  本次变动值（v1 仅签到入账为正）
     * @param earned 累计获得增量（与 delta 相等时为正向入账；预留负向出账场景传 0）
     * @return 影响行数（0 = 账户不存在，调用方必须先懒创建并视为异常回滚）
     */
    int addPoints(@Param("userId") Long userId, @Param("delta") int delta, @Param("earned") int earned);

    /**
     * 月榜展示用户信息批量查询（仅取脱敏所需列，禁止带出 password 等敏感字段）
     */
    List<LeaderboardUserVo> selectLeaderboardUsers(@Param("userIds") List<Long> userIds);
}
