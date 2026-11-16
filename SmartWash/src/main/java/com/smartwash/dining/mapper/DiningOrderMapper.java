package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.dining.entity.DiningOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 点单订单 Mapper。
 * version 乐观并发走 LambdaUpdateWrapper 条件 UPDATE（WHERE id=? AND version=? SET version=version+1）。
 * 结束点单用 [selectForUpdate] 对订单行加锁：串行化「结束」与「选菜写入」，
 * 保证购买清单快照不丢并发窗口内落库的选菜。
 * 「我的订单」分页走 [selectMyOrderPage]（XML）：单条 SQL 同时覆盖「我发起 + 我参与」，
 * 避免先全量反查 orderId 再内存排序。
 */
@Mapper
public interface DiningOrderMapper extends BaseMapper<DiningOrder> {

    /** 订单行排他锁读取（结束点单事务内使用，锁持有至事务提交） */
    @Select("SELECT * FROM dining_order WHERE id = #{id} FOR UPDATE")
    DiningOrder selectForUpdate(@Param("id") Long id);

    /**
     * 我的订单分页（我发起的 + 我参与的，按创建时间倒序、id 倒序兜底稳定分页）。
     * 成员反查用 EXISTS 走 idx_user；发起者直查走 idx_creator，两者并集覆盖历史脏数据
     * （创建者因异常未写入成员表时仍能在列表出现）。
     */
    Page<DiningOrder> selectMyOrderPage(Page<DiningOrder> page, @Param("userId") Long userId);
}
