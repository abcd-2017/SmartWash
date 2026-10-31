package com.smartwash.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.workbench.entity.AppDeeplinkRoute;
import org.apache.ibatis.annotations.Mapper;

/**
 * App deeplink 路由字典 Mapper（MyBatis-Plus 内置方法即可覆盖，无自定义 SQL）。
 */
@Mapper
public interface AppDeeplinkRouteMapper extends BaseMapper<AppDeeplinkRoute> {
}
