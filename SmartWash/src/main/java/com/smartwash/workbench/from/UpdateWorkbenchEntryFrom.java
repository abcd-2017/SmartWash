package com.smartwash.workbench.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新工作台入口请求 DTO。
 * 字段不传（null）即不修改；entry_key 为端上对齐标识，不可修改（如需换 key 请删除重建）。
 * badge / description / remark 传空字符串表示清空（落库为 NULL）。
 */
@Data
public class UpdateWorkbenchEntryFrom {

    /** 入口记录 id */
    @NotNull(message = "入口 id 不能为空")
    private Long id;

    /** 入口标题 */
    @Size(max = 32, message = "标题过长（上限32字符）")
    private String title;

    /** 副标题描述，空字符串=清空 */
    @Size(max = 128, message = "描述过长（上限128字符）")
    private String description;

    /** 图标 key（端上白名单枚举映射；gamepad 对应游戏中心种子入口） */
    @Pattern(regexp = "^(calendar|search|book|calculate|history|chat|link|gamepad|default)$", message = "图标 key 不在白名单内")
    private String icon;

    /** 图标色组 key */
    @Pattern(regexp = "^(green|blue|orange|pink|teal|purple)$", message = "色组 key 须为 green/blue/orange/pink/teal/purple")
    private String color;

    /** 跳转 URI：smartwash://... 或 https://... */
    @Size(max = 512, message = "跳转 URI 过长（上限512字符）")
    @Pattern(regexp = "^(smartwash://|https?://)\\S+$", message = "跳转 URI 须以 smartwash:// 或 http(s):// 开头且不含空白")
    private String target;

    /** 角标文案，空字符串=清空 */
    @Size(max = 16, message = "角标文案过长（上限16字符）")
    private String badge;

    /** 1启用 0停用 */
    @Min(value = 0, message = "enabled 只能为 0 或 1")
    @Max(value = 1, message = "enabled 只能为 0 或 1")
    private Integer enabled;

    /** 排序，越小越靠前 */
    @Min(value = 0, message = "排序不能为负数")
    private Integer sort;

    /** 备注，空字符串=清空 */
    @Size(max = 255, message = "备注过长（上限255字符）")
    private String remark;
}
