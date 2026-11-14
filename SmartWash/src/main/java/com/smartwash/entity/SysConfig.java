package com.smartwash.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统参数配置（一行一个参数键，管理端可实时修改并落库，重启不丢）。
 *
 * <p>承载此前硬编码在 Java 常量里的业务参数（订单支付超时、登录锁定阈值等），
 * 修改后通过管理端接口写库并同步失效进程内缓存，业务方下次读取即生效。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_config")
public class SysConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 参数键，唯一（如 order.payTimeoutMinutes） */
    private String configKey;

    /** 参数值（字符串承载，数值类参数由服务层校验合法性） */
    private String configValue;

    /** 备注说明（面向管理员的参数用途描述） */
    private String remark;

    private LocalDateTime updatedAt;
}
