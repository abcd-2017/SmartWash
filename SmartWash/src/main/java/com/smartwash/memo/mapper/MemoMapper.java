package com.smartwash.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.memo.entity.Memo;

/**
 * 速记手账 Mapper。
 * 列表分页、条件逻辑删除均走 MyBatis-Plus 内置方法 + Wrapper（条件 UPDATE 判影响行数），
 * 标签聚合在服务层对 tags 列拆分统计，无自定义 XML。
 */
public interface MemoMapper extends BaseMapper<Memo> {
}
