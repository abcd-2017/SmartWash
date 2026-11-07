import request from '@/utils/http';

export function getDashboardStats() {
  return request({
    url: '/admin/dashboard/stats',
    method: 'get',
  });
}

// 工作台趋势：近 days 天（7/30/90）逐日 { date, income, orderCount }，空日已由后端补零
export function getDashboardTrend(days) {
  return request({
    url: '/admin/dashboard/trend',
    method: 'get',
    params: { days },
  });
}
