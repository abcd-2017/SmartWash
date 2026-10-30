package com.smartwash.workbench.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.workbench.entity.AppDeeplinkRoute;
import com.smartwash.workbench.from.AddDeeplinkRouteFrom;
import com.smartwash.workbench.from.SearchDeeplinkRouteFrom;
import com.smartwash.workbench.from.UpdateDeeplinkRouteFrom;
import com.smartwash.workbench.vo.DeeplinkRouteVo;

import java.util.List;

/**
 * App deeplink 路由字典服务。
 * 公开下发走 Redis 缓存（deeplinkRoutes），管理端增删改统一 @CacheEvict 失效。
 */
public interface DeeplinkRouteService extends IService<AppDeeplinkRoute> {

    /** 管理端：分页查询（host 模糊 + enabled 筛选，按 host 升序便于定位） */
    Page<DeeplinkRouteVo> adminPage(SearchDeeplinkRouteFrom from);

    /** 管理端：新增路由（host 重复校验 + 唯一索引兜底裁决） */
    void addRoute(AddDeeplinkRouteFrom from);

    /** 管理端：更新路由（host 不可修改；description 空串=清空） */
    void updateRoute(UpdateDeeplinkRouteFrom from);

    /** 管理端：批量物理删除（ids 逗号分隔） */
    void deleteRoutes(String ids);

    /** 用户端：启用中的路由字典列表（id 升序），走缓存；公开接口，端上冷启动解析兜底 */
    List<DeeplinkRouteVo> listEnabledRoutes();
}
