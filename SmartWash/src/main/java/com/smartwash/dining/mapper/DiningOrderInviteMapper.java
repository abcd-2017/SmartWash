package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningOrderInvite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点单定向邀请 Mapper（内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface DiningOrderInviteMapper extends BaseMapper<DiningOrderInvite> {
}
