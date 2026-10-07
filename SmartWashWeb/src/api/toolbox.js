// src/api/toolbox.js
// 工具箱管理端 API 层
// 接口路径与后端 ToolboxAdminController 对齐（/admin/toolbox/short-codes，ROLE_ADMIN）。
import request from '@/utils/http';

// 短码全局分页：page/size + code（精确）/ ownerPhone（精确）/ contentType（1-5，可空），
// 返回 IPage（records/total/current/size），owner 手机号由后端脱敏后下发
export function getShortCodeList(params) {
  return request({
    url: '/admin/toolbox/short-codes',
    method: 'get',
    params,
  });
}

// 短码详情：比列表多 recentVisits（最近 10 条访问：ipHash/userAgent/referer/createdAt）
export function getShortCodeDetail(id) {
  return request({
    url: `/admin/toolbox/short-codes/${id}`,
    method: 'get',
  });
}

// 删除短码（治理用途，后端物理删除并清对应缓存）
export function deleteShortCode(id) {
  return request({
    url: `/admin/toolbox/short-codes/${id}`,
    method: 'delete',
  });
}
