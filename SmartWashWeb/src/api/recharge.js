import request from '@/utils/http';

export function getRechargeList(params) {
  return request({
    url: '/admin/rechargeRecords/all',
    method: 'get',
    params,
  });
}

// 充值汇总：dateFrom/dateTo（yyyy-MM-dd，可选）→ { totalCount, successCount, totalAmount, successAmount }
export function getRechargeSummary(params) {
  return request({
    url: '/admin/recharge/summary',
    method: 'get',
    params,
  });
}

// 充值记录导出（xlsx 文件流，与订单导出同模式）：
// 响应为 blob，不走统一信封（http.js 已对 responseType='blob' 放行）；
// 后端错误分支以 JSON 信封返回，此处按 Content-Type 识别后转成业务 Error，
// 与 delivery.js 的 downloadDeliveryFile 错误处理对齐
export async function exportRechargeRecords(params) {
  const res = await request({
    url: '/admin/recharge/export',
    method: 'get',
    params,
    responseType: 'blob',
  });

  const blob = res.data;
  if (blob && blob.type && blob.type.includes('json')) {
    const text = await blob.text();
    let envelope;
    try {
      envelope = JSON.parse(text);
    } catch {
      throw new Error('导出失败，请稍后重试');
    }
    throw new Error(envelope.message || '导出失败');
  }
  return blob;
}
