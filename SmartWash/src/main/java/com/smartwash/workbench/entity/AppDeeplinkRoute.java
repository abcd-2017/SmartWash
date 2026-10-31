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
 * App deeplink 路由字典。
 * 双重用途：1) 管理端配置 workbench_entry.target 的数据源（可选 host 下拉）；
 * 2) 经 /web/deeplink/routes 公开下发，端上冷启动拉取做未知 host 的元数据兜底。
 * host 是端上路由分发标识，创建后不可修改（改 host 等于改路由，端上必须发版支持）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("app_deeplink_route")
public class AppDeeplinkRoute implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** smartwash:// 的 host，如 login，uk_host 唯一索引裁决 */
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
