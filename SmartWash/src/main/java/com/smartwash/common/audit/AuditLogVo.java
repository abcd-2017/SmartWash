package com.smartwash.common.audit;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询视图对象（管理端展示，含链哈希便于人工核对）。
 */
@Data
public class AuditLogVo {

    private Long id;

    private String adminUser;

    private String action;

    private String targetType;

    private String targetId;

    private String beforeJson;

    private String afterJson;

    private String ip;

    private String prevHash;

    private String hash;

    private LocalDateTime createdAt;
}
