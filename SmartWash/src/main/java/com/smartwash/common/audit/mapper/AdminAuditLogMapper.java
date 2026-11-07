package com.smartwash.common.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.common.audit.AdminAuditLog;

/**
 * 管理端审计日志 Mapper（MyBatis-Plus BaseMapper，无自定义 SQL）。
 * 独立子包便于 @MapperScan 精确扫描（避免把同包 Service 接口误注册为 Mapper）。
 */
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {
}
