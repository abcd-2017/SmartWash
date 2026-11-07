import request from '@/utils/http';

// 订阅汇总（无参数）→ { activeCount, expiredCount, expiringIn7Days }
export function getSubscriptionSummary() {
  return request({
    url: '/admin/subscription/summary',
    method: 'get',
  });
}

// 订阅列表：page/size/status/dateFrom/dateTo
// → 分页信封 { records, total, current, size }（分页参数与仓库其他列表一致，经 BaseSearchFrom 绑定）
export function getSubscriptionList(params) {
  return request({
    url: '/admin/subscription/list',
    method: 'get',
    params,
  });
}
