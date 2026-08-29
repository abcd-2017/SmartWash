package com.smartwash.divination.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台全局设置（单例 id=1，管理端维护）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_platform_setting")
public class DivPlatformSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** div_model_config.id */
    private Long defaultModelId;

    private Long fallbackModelId;

    /** 是否允许用户自带 key */
    private Integer byokEnabled;

    private Integer byokDailyLimit;

    private Integer platformDailyLimit;

    private LocalDateTime updatedAt;
}
