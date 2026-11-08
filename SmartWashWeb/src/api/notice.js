import request from '@/utils/http';

// 站内信 API 层（/admin/notices，ROLE_ADMIN）
// 接口已与后端 NoticeAdminController 对齐：
// 字段名以 AddNoticeFrom / SearchNoticeFrom / NoticeVo / NoticeReadStatVo 为准（驼峰）。
// 发送人 creatorAdminId 由后端从登录上下文写入，前端不传。

// 发送站内信：title/content/type/deepLink（deepLink 可空=纯文案公告）
// 返回新建公告 id
export function publishNotice(data) {
  return request({
    url: '/admin/notices',
    method: 'post',
    data,
  });
}

// 已发公告分页：page/size + title（模糊）/ type（SYSTEM/ANNOUNCEMENT，可空）
// → 分页信封 { records, total, current, size }（按创建时间倒序）
export function getNoticeList(params) {
  return request({
    url: '/admin/notices',
    method: 'get',
    params,
  });
}

// 送达统计：→ { totalUsers, readCount, readRateText }
export function getNoticeReadStat(id) {
  return request({
    url: `/admin/notices/${id}/reads`,
    method: 'get',
  });
}
