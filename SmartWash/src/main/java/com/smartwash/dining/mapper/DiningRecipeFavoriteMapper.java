package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DiningRecipeFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 吃啥-菜谱收藏 Mapper（MyBatis-Plus 内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface DiningRecipeFavoriteMapper extends BaseMapper<DiningRecipeFavorite> {
}
