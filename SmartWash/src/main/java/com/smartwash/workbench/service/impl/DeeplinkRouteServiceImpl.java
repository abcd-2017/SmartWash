package com.smartwash.workbench.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.workbench.entity.AppDeeplinkRoute;
import com.smartwash.workbench.from.AddDeeplinkRouteFrom;
import com.smartwash.workbench.from.SearchDeeplinkRouteFrom;
import com.smartwash.workbench.from.UpdateDeeplinkRouteFrom;
import com.smartwash.workbench.mapper.AppDeeplinkRouteMapper;
import com.smartwash.workbench.service.DeeplinkRouteService;
import com.smartwash.workbench.vo.DeeplinkRouteVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * App deeplink 路由字典服务实现。
 * 缓存模式与 SchoolsServiceImpl 一致：读走 @Cacheable 的公共方法（经 self 自注入调用绕过 AOP 代理失效），
 * 写操作 @CacheEvict(allEntries) 全量失效；host 唯一性以 uk_host 唯一索引兜底并发。
 */
@Slf4j
@Service
public class DeeplinkRouteServiceImpl extends ServiceImpl<AppDeeplinkRouteMapper, AppDeeplinkRoute>
        implements DeeplinkRouteService {

    /** 路由字典下发缓存空间名（TTL 见 RedisConfig） */
    public static final String CACHE_NAME = "deeplinkRoutes";

    /** 启用状态：1启用 */
    private static final int ENABLED_TRUE = 1;

    /** 自注入：@Cacheable 方法须经代理调用才生效，同类内部直调会绕过 AOP */
    @Lazy
    @Autowired
    private DeeplinkRouteServiceImpl self;

    // ==================== 管理端 ====================

    @Override
    public Page<DeeplinkRouteVo> adminPage(SearchDeeplinkRouteFrom from) {
        LambdaQueryWrapper<AppDeeplinkRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(from.getHost()), AppDeeplinkRoute::getHost, from.getHost());
        wrapper.eq(from.getEnabled() != null, AppDeeplinkRoute::getEnabled, from.getEnabled());
        // 字典类数据按 host 升序，便于管理端定位
        wrapper.orderByAsc(AppDeeplinkRoute::getHost);

        Page<AppDeeplinkRoute> page = this.page(new Page<>(from.getPage(), from.getSize()), wrapper);
        Page<DeeplinkRouteVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::buildVo).toList());
        return voPage;
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void addRoute(AddDeeplinkRouteFrom from) {
        // 先查重给业务侧友好提示；并发窗口由 uk_host 唯一索引最终裁决
        if (this.getByHost(from.getHost()) != null) {
            throw new CustomExceptions("host 已存在");
        }
        AppDeeplinkRoute route = new AppDeeplinkRoute();
        BeanUtils.copyProperties(from, route);
        try {
            this.save(route);
        } catch (DuplicateKeyException e) {
            throw new CustomExceptions("host 已存在");
        }
        log.info("新增 deeplink 路由, host: {}, name: {}, platform: {}", route.getHost(), route.getName(), route.getPlatform());
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void updateRoute(UpdateDeeplinkRouteFrom from) {
        AppDeeplinkRoute route = this.getById(from.getId());
        if (route == null) {
            throw new CustomExceptions("路由不存在");
        }
        // 仅覆盖传入字段（MyBatis-Plus updateById 跳过 null 列）；host 为端上路由分发标识，不允许修改
        if (StringUtils.hasText(from.getName())) {
            route.setName(from.getName());
        }
        if (from.getPlatform() != null) {
            route.setPlatform(from.getPlatform());
        }
        if (from.getRequireLogin() != null) {
            route.setRequireLogin(from.getRequireLogin());
        }
        if (from.getDescription() != null) {
            route.setDescription(from.getDescription().isEmpty() ? null : from.getDescription());
        }
        if (from.getEnabled() != null) {
            route.setEnabled(from.getEnabled());
        }
        this.updateById(route);
        log.info("更新 deeplink 路由, id: {}, host: {}", route.getId(), route.getHost());
    }

    @Override
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteRoutes(String ids) {
        log.info("删除 deeplink 路由, ids: {}", ids);
        for (String id : ids.split(",")) {
            this.removeById(Long.parseLong(id.trim()));
        }
    }

    // ==================== 用户端（公开） ====================

    @Override
    public List<DeeplinkRouteVo> listEnabledRoutes() {
        return self.enabledRoutesFromCache();
    }

    /**
     * 启用中的路由字典列表（缓存命中路径）。
     * 仅 enabled=1，按 id 升序；公开接口，端上冷启动拉取做未知 host 的元数据兜底。
     */
    @Cacheable(value = CACHE_NAME, key = "'all'")
    public List<DeeplinkRouteVo> enabledRoutesFromCache() {
        LambdaQueryWrapper<AppDeeplinkRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppDeeplinkRoute::getEnabled, ENABLED_TRUE);
        wrapper.orderByAsc(AppDeeplinkRoute::getId);
        return this.list(wrapper).stream().map(this::buildVo).toList();
    }

    // ==================== 内部方法 ====================

    private AppDeeplinkRoute getByHost(String host) {
        // 直查 baseMapper（与 toolbox 内核一致），单测 mock 路径确定
        return this.getBaseMapper().selectOne(new LambdaQueryWrapper<AppDeeplinkRoute>()
                .eq(AppDeeplinkRoute::getHost, host));
    }

    private DeeplinkRouteVo buildVo(AppDeeplinkRoute route) {
        DeeplinkRouteVo vo = new DeeplinkRouteVo();
        BeanUtils.copyProperties(route, vo);
        return vo;
    }
}
