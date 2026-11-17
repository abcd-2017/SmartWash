package com.smartwash.dining.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.dining.entity.DietaryPreference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户饮食偏好 Mapper（MyBatis-Plus 内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface DietaryPreferenceMapper extends BaseMapper<DietaryPreference> {
}
