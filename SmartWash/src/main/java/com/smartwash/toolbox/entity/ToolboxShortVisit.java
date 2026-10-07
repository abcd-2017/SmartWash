package com.smartwash.toolbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具箱短码访问明细，与主表解耦（无外键，主表删除后明细保留，统计历史不丢）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("toolbox_short_visit")
public class ToolboxShortVisit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 短码（冗余，不随主表删除级联） */
    private String code;

    /** IP 摘要前 8 位，不存明文 */
    private String ipHash;

    private String userAgent;

    private String referer;

    private LocalDateTime createdAt;
}
