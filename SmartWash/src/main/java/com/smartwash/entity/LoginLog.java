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
 * 登录日志（四端平台标识 + 项目渠道 + 登录结果）。
 *
 * <p>由登录/注册成功与失败路径写入，管理端分页查询；独立于 users 主档，避免污染高频更新表。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("login_log")
public class LoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录用户 ID：用户端 = users.user_id；管理端 = admin_users.admin_id；失败可空 */
    private Long userId;

    /** 登录账号：用户端 = 手机号；管理端 = 用户名；失败时记录尝试账号 */
    private String phone;

    /** 平台标识：android/ios/harmonyos/web/unknown */
    private String platform;

    /** 项目渠道：full/lite/unknown（同一 Android 工程的两个 product flavor） */
    private String appChannel;

    private String appVersion;

    /** User-Agent（截断至 500 字符） */
    private String ua;

    /** 网络类型：wifi/cellular/none/unknown */
    private String networkType;

    /** 登录来源 IP */
    private String ip;

    /** 登录结果：success/fail */
    private String result;

    /** 登录时间（插入时由 MetaObjectHandler 自动填充） */
    private LocalDateTime createdAt;
}
