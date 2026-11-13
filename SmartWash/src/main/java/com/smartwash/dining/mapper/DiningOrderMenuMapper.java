package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningOrderMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本单菜单 Mapper（内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface DiningOrderMenuMapper extends BaseMapper<DiningOrderMenu> {
}
