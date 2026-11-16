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

    private String title;

    private String description;

    private String icon;

    private String color;

    /** 卡片类型 key：tile/wide/row/notice */
    private String type;

    /** 分组标题：端上分组边界渲染区块标题 */
    private String sectionTitle;

    private String imageUrl;

    private String target;

    /** 角标文案，空则不显示 */
    private String badge;

    /** 1启用 0停用 */
    private Integer enabled;

    /** 排序，越小越靠前 */
    private Integer sort;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
