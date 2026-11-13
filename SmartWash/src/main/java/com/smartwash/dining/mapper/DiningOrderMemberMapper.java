package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningOrderMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点单订单成员 Mapper（内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface DiningOrderMemberMapper extends BaseMapper<DiningOrderMember> {
}
