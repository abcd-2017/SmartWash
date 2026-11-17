package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜谱配料关联 Mapper（MyBatis-Plus 内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface DiningRecipeIngredientMapper extends BaseMapper<DiningRecipeIngredient> {
}
