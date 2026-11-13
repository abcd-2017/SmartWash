package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningRecipe;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜谱 Mapper：MyBatis-Plus BaseMapper，内置方法覆盖全部当前查询（分页筛选走 LambdaQueryWrapper）。
 */
@Mapper
public interface DiningRecipeMapper extends BaseMapper<DiningRecipe> {
}
