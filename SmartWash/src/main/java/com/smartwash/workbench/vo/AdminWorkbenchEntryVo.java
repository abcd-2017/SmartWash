package com.smartwash.workbench.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 工作台入口管理端视图对象。
 * 相比用户端 Vo 额外暴露 id/enabled/remark/时间戳，供 Web 后台列表与治理操作使用。
 */
@Data
@Accessors(chain = true)
public class AdminWorkbenchEntryVo {

    private Long id;

    /** 业务唯一标识 */
    private String entryKey;

    /** 入口标题 */
    private String title;

    /** 副标题描述 */
    private String description;

    /** 图标 key */
    private String icon;

    /** 图标色组 key */
    private String color;

    /** 跳转 URI */
    private String target;

    /** 角标文案，空则不显示 */
    private String badge;

    /** 1启用 0停用 */
    private Integer enabled;

    /** 排序，越小越靠前 */
    private Integer sort;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
