package com.smartwash.workbench.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 deeplink 路由字典请求 DTO。
 * host 是端上路由分发标识：小写字母开头，2-64 位小写字母/数字/连字符，创建后不可修改。
 */
@Data
public class AddDeeplinkRouteFrom {

    /** smartwash:// 的 host，如 login */
    @NotBlank(message = "host 不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$", message = "host 须为小写字母开头、2-64位小写字母/数字/连字符")
    private String host;

    /** 页面名称（管理端展示） */
    @NotBlank(message = "页面名称不能为空")
    @Size(max = 64, message = "页面名称过长（上限64字符）")
    private String name;

    /** 支持平台：1 Android 2 HarmonyOS 3 双端 */
    @NotNull(message = "支持平台不能为空")
    @Min(value = 1, message = "platform 只能为 1/2/3")
    @Max(value = 3, message = "platform 只能为 1/2/3")
    private Integer platform = 3;

    /** 1需要登录 0不需要 */
    @Min(value = 0, message = "requireLogin 只能为 0 或 1")
    @Max(value = 1, message = "requireLogin 只能为 0 或 1")
    private Integer requireLogin = 0;

    /** 说明（如 web/t 的 scheme 用法） */
    @Size(max = 255, message = "说明过长（上限255字符）")
    private String description;

    /** 1启用 0停用 */
    @Min(value = 0, message = "enabled 只能为 0 或 1")
    @Max(value = 1, message = "enabled 只能为 0 或 1")
    private Integer enabled = 1;
}
