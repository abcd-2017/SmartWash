// src/api/lockerTag.js
// 储物柜资产码（一物一码 M3）API 层。
// 生成端走管理端 http 实例（/admin/**，@Audited 审计）；公开解析走同一实例的匿名请求
// （/web/locker-tags/** 公开放行，无 token 时拦截器不注入 Authorization）。
import request from '@/utils/http';

// 管理端：为单个柜子生成柜码（活码别名 L{lockerId} 幂等；返回 ShortCodeVo 含 shortUrl）
export function generateLockerTag(lockerId) {
  return request({
    url: `/admin/toolbox/locker-tags/locker/${lockerId}`,
    method: 'post',
  });
}

// 管理端：按校批量生成（逐柜幂等），返回 [{lockerId, lockerNumber, code, shortUrl, created}]
export function generateLockerTagsForSchool(schoolId) {
  return request({
    url: `/admin/toolbox/locker-tags/school/${schoolId}`,
    method: 'post',
  });
}

// 公开：柜码解析（扫码落地页数据源；别名 L{lockerId} 校验后返回柜子信息卡）
// 业务 404 由默认拦截器转 reject(Error(message))，落地页 catch 分支呈现
export function getLockerTagInfo(code) {
  return request({
    url: `/web/locker-tags/${encodeURIComponent(code)}`,
    method: 'get',
  });
}
