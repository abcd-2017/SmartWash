package com.smartwash.workbench.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * deeplink 路由字典视图对象。
 * 用户端公开接口与管理端列表复用：端上冷启动拉取做未知 host 的元数据兜底
 * （name/requireLogin 可用于展示与登录前置判断），id 供管理端定位记录。
 */
@Data
@Accessors(chain = true)
public class DeeplinkRouteVo {

    private Long id;

    /** smartwash:// 的 host，如 login */
    private String host;

    /** 页面名称（管理端展示） */
    private String name;

    /** 1 Android 2 HarmonyOS 3 双端 */
    private Integer platform;

    /** 1需要登录 0不需要 */
    private Integer requireLogin;

    /** 说明（如 web/t 的 scheme 用法） */
    private String description;

    /** 1启用 0停用 */
    private Integer enabled;
}
