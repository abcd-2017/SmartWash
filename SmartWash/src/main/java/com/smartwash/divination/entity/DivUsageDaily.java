package com.smartwash.divination.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日用量（task 定时聚合，管理端看板）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_usage_daily")
public class DivUsageDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private String method;

    private Integer recordCount;

    private Integer interpretCount;

    private Integer cacheHitCount;

    private Integer blockedCount;

    private Long tokensIn;

    private Long tokensOut;

    private Integer activeUsers;

    private LocalDateTime createdAt;
}
