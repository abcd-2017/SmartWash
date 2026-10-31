package com.smartwash.workbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 移动端工作台入口配置。
 * 管理端配置入口卡片（标题/图标/色组/跳转 URI/角标/排序），
 * 用户端经 /web/auth/workbench/entries 下发启用项；entry_key 是端上兜底列表对齐的业务唯一标识，创建后不可修改。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("workbench_entry")
public class WorkbenchEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务唯一标识：端上兜底列表与下发数据对齐用，uk_entry_key 唯一索引裁决 */
    private String entryKey;

    /** 入口标题 */
    private String title;

    /** 副标题描述 */
    private String description;

    /** 图标 key，端上白名单枚举映射，未知值显示默认图标 */
    private String icon;

    /** 图标色组 key：green/blue/orange/pink/teal/purple */
    private String color;

    /** 跳转 URI：smartwash://... 或 https://...（deeplink 路由字典是合法 host 的数据源） */
    private String target;

    /** 角标文案（如 新/热），空则不显示 */
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
