// src/api/assetTag.js
// 物品标签（一物一码 M2）API 层。
// 管理端治理删除走默认 http 实例（/admin/**，@Audited 审计）；
// 公开解析走同一实例的匿名请求（/web/tag/** 公开放行，无 token 时不注入 Authorization）。
import request from '@/utils/http';

// 管理端：治理删除标签（明细 + 访问明细 + 主表级联；物品信息对管理端不可见）
export function adminDeleteAssetTag(id) {
  return request({
    url: `/admin/toolbox/asset-tags/${id}`,
    method: 'delete',
  });
}

// 公开：标签解析（寻物页数据源；别名 L{lockerId} 同构的标签码即凭证）
// 业务 404 由默认拦截器转 reject(Error(message))，页面 catch 分支呈现
export function getAssetTagInfo(code) {
  return request({
    url: `/web/tag/${encodeURIComponent(code)}`,
    method: 'get',
  });
}
