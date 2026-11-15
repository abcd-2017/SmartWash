// src/api/loginLog.js
// 登录日志管理端 API 层
// 接口路径与后端登录日志分页接口对齐（/admin/login-logs/page，ROLE_ADMIN）。
// 四端联动说明：本组为新增接口，消费方仅 Web 管理后台，Android/鸿蒙不消费。
// 契约（后端并行实现中）：入参 page/size + platform/result/startTime/endTime 可选筛选，
// 返回 { records, total }；记录字段 platform/app_version/ua/network_type/ip/result/created_at/phone/user_id。
import request from '@/utils/http';

// 分页查询登录日志（按时间倒序；筛选：平台/结果/时间区间）
export function getLoginLogs(params) {
  return request({
    url: '/admin/login-logs/page',
    method: 'get',
    params,
  });
}
