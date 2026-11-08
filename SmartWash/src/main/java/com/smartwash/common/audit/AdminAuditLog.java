package com.smartwash.common.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端操作审计日志实体（admin_audit_log）。
 *
 * <p>结构变更统一由根目录 smart_wash.sql 管理。哈希链：
 * {@code hash = HMAC-SHA256(secret, prev_hash + 规范化payload)}，
 * 首条 prev_hash 取固定 genesis 值（见 {@link AuditHashService#GENESIS_HASH}）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("admin_audit_log")
public class AdminAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人管理员用户名；无登录态兜底为 unknown */
    private String adminUser;

    /** 动作标识，如 LAUNDRY_ITEM_UPDATE */
    private String action;

    /** 目标对象类型，如 laundry_item */
    private String targetType;

    /** 目标对象 id；批量操作为逗号分隔串；无法解析时为空 */
    private String targetId;

    /** 操作前摘要（入参 JSON，敏感字段掩码，超 2KB 截断） */
    private String beforeJson;

    /** 操作后摘要（返回值 JSON，超 2KB 截断） */
    private String afterJson;

    /** 操作来源 IP（IPv6 最长 45；无请求上下文为空） */
    private String ip;

    /** 链上前一条 hash（首条为 genesis） */
    private String prevHash;

    /** HMAC-SHA256 链哈希 */
    private String hash;

    /** 操作时刻（秒级精度，参与规范化 payload，入库后须可复算） */
    private LocalDateTime createdAt;
}
