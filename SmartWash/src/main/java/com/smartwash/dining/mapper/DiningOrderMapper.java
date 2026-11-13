package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 点单订单 Mapper。
 * version 乐观并发走 LambdaUpdateWrapper 条件 UPDATE（WHERE id=? AND version=? SET version=version+1）。
 * 结束点单用 [selectForUpdate] 对订单行加锁：串行化「结束」与「选菜写入」，
 * 保证购买清单快照不丢并发窗口内落库的选菜。
 */
@Mapper
public interface DiningOrderMapper extends BaseMapper<DiningOrder> {

    /** 订单行排他锁读取（结束点单事务内使用，锁持有至事务提交） */
    @Select("SELECT * FROM dining_order WHERE id = #{id} FOR UPDATE")
    DiningOrder selectForUpdate(@Param("id") Long id);
}
