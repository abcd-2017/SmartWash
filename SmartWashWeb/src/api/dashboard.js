import request from '@/utils/http';

export function getDashboardStats() {
  return request({
    url: '/admin/dashboard/stats',
    method: 'get',
  });
}
