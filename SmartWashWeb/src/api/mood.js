import request from '@/utils/http';

// 心情汇总（管理端）：dateFrom/dateTo（yyyy-MM-dd，可选）
// → { recordCount, userCount, byEmotion: [{ emotion, count }], byDate: [{ date, count }] }
// byDate 为连续补零序列，可直接供趋势图渲染
export function getMoodSummary(params) {
  return request({
    url: '/admin/mood/summary',
    method: 'get',
    params,
  });
}
