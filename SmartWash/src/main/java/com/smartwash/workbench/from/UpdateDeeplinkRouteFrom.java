package com.smartwash.workbench.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 deeplink 路由字典请求 DTO。
 * 字段不传（null）即不修改；host 为端上路由分发标识，不可修改（改 host 等于改路由，端上须发版支持）。
 * description 传空字符串表示清空（落库为 NULL）。
 */
@Data
public class UpdateDeeplinkRouteFrom {

    /** 路由记录 id */
    @NotNull(message = "路由 id 不能为空")
    private Long id;

    /** 页面名称（管理端展示） */
    @Size(max = 64, message = "页面名称过长（上限64字符）")
    private String name;

    /** 支持平台：1 Android 2 HarmonyOS 3 双端 */
    @Min(value = 1, message = "platform 只能为 1/2/3")
    @Max(value = 3, message = "platform 只能为 1/2/3")
    private Integer platform;

    /** 1需要登录 0不需要 */
    @Min(value = 0, message = "requireLogin 只能为 0 或 1")
    @Max(value = 1, message = "requireLogin 只能为 0 或 1")
    private Integer requireLogin;

    /** 说明，空字符串=清空 */
    @Size(max = 255, message = "说明过长（上限255字符）")
    private String description;

    /** 1启用 0停用 */
    @Min(value = 0, message = "enabled 只能为 0 或 1")
    @Max(value = 1, message = "enabled 只能为 0 或 1")
    private Integer enabled;
}
