package com.smartwash.workbench.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 工作台入口视图对象（用户端下发）。
 * 不下发 enabled/remark 等管理字段，下发面最小化；端上按 sort 升序渲染，
 * entryKey 与端上兜底列表对齐，icon/color 为端上白名单枚举 key，未知值显示默认样式。
 */
@Data
@Accessors(chain = true)
public class WorkbenchEntryVo {

    /** 业务唯一标识，端上兜底列表对齐用 */
    private String entryKey;

    /** 入口标题 */
    private String title;

    /** 副标题描述 */
    private String description;

    /** 图标 key（端上白名单枚举映射） */
    private String icon;

    /** 图标色组 key：green/blue/orange/pink/teal/purple */
    private String color;

    /** 跳转 URI：smartwash://... 或 https://... */
    private String target;

    /** 角标文案（如 新/热），空则不显示 */
    private String badge;

    /** 排序，越小越靠前 */
    private Integer sort;
}
