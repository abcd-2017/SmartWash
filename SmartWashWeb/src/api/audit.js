// src/api/audit.js
// 操作审计管理端 API 层
// 接口路径与后端 AdminAuditLogController 对齐（/admin/audit-logs，ROLE_ADMIN）。
// 四端联动说明：本组为新增接口，消费方仅 Web 管理后台，Android/鸿蒙不消费。
import request from '@/utils/http';

// 分页查询审计日志（筛选：操作人/动作/目标类型/目标id/时间区间，按 id 倒序）
export function getAuditLogs(params) {
  return request({
    url: '/admin/audit-logs',
    method: 'get',
    params,
  });
}

// 校验审计链完整性（抽查最近 limit 条，后端默认 500、上限 5000）
export function verifyAuditLogs(limit) {
  return request({
    url: '/admin/audit-logs/verify',
    method: 'get',
    params: { limit },
  });
}
