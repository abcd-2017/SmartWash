package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 选菜记录 Mapper（内置方法即可覆盖，无自定义 SQL）。
 * uk(order+recipe+user) 冲突由服务层捕获 DuplicateKeyException 转业务提示。
 */
@Mapper
public interface DiningOrderItemMapper extends BaseMapper<DiningOrderItem> {
}
