package com.smartwash.points.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.points.entity.PointLedger;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分流水 Mapper（append-only 类型级收口）。
 * 刻意不继承 MyBatis-Plus BaseMapper —— 流水禁删禁改，本接口在类型系统上
 * 只暴露 insert 与只读查询，不存在 update/delete 入口（对齐资金类记录纪律）。
 */
public interface PointLedgerMapper {

    /**
     * 追加一条流水（唯一允许的写操作）
     */
    int insert(PointLedger ledger);

    /**
     * 个人最近流水（按时间倒序，idx_ledger_user_time 覆盖）
     */
    List<PointLedger> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 个人流水分页（按时间倒序，idx_ledger_user_time 覆盖）。
     * 由 MyBatis-Plus 分页插件追加 LIMIT 并执行 COUNT，XML 不手写分页。
     */
    Page<PointLedger> selectPageByUserId(Page<PointLedger> page, @Param("userId") Long userId);
}
