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
 *
 * <p>平台零兜底：解读只消费用户 BYOK 配置，不存在平台默认/备用模型；
 * 本表仅承载功能总开关与每用户日限额。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("div_platform_setting")
public class DivPlatformSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 观星台 AI 解读总开关：0=客户端隐藏入口并拦截，1=按用户自配模型解读 */
    private Integer divUseEnabled;

    /** 每用户每日解读次数限额 */
    private Integer byokDailyLimit;

    private LocalDateTime updatedAt;
}
