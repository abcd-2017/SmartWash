// src/api/delivery.js
// 设备投递对端侧（电脑浏览器公开页 /d/:code）API 层
// 接口路径与后端 ToolboxDeliveryPublicController 对齐（/web/delivery/**，匿名公开段，码即凭证）。
// 契约：docs/delivery-api.md §三（对端侧接口）。
//
// 本文件不走默认 http 实例：该实例对 code !== 200 一律 reject 并丢失业务码，
// 而投递语义要求页面区分 403（密码错误）/ 404（会话失效）/ 409（被占用/已取走）/ 413（超限），
// 且该组业务码不得触发全局 401 登出（公开页本就无 token）。故统一走 envelopeHttp
// 拿完整响应信封 { code, message, data } 交由页面按业务码消费。
import { envelopeHttp } from '@/utils/http';

// 业务码常量（契约 §一：信封 code 字段，非 HTTP 状态码）
export const DELIVERY_CODE = {
  OK: 200,
  WRONG_PASSWORD: 403, // 进入密码错误（响应故意延迟约 500ms 防爆破）
  GONE: 404, // 会话/条目不存在、已过期、已关闭、进入次数达上限
  CONFLICT: 409, // 会话已被其他设备占用 / 条目已被取走 / 文件未 take 就请求 content
  TOO_LARGE: 413, // 条目数 > 50 / 单文件 > 20MB / 会话总字节 > 200MB
};

// 单文件上限 20MB（413 语义），发布前前端预校验，避免白传大文件
export const DELIVERY_MAX_FILE_SIZE = 20 * 1024 * 1024;

// 统一执行：HTTP 200 时返回完整信封 { code, message, data }；
// 网络层错误（超时/断网等）抛 Error，由页面按「网络异常」提示
async function request(config) {
  const res = await envelopeHttp.request(config);
  return res.data;
}

// 进入会话：可选密码（也可不带 body）；密码错误 403、被占 409、失效/达上限 404；
// 成功返回会话 Vo（code/expireAt/hasPassword/itemCount/pendingCount/clickCount/enterUrl/createdAt）
export function enterDelivery(code, password) {
  return request({
    url: `/web/delivery/${encodeURIComponent(code)}/enter`,
    method: 'post',
    // 有密码会话传 {"password":...}；无密码会话不带 body（后端 @RequestBody required=false）
    ...(password ? { data: { password } } : {}),
  });
}

// 条目流：对端视角全量条目（含已焚毁占位行），createdAt 正序，2-3s 轮询消费
export function getDeliveryItems(code) {
  return request({
    url: `/web/delivery/${encodeURIComponent(code)}/items`,
    method: 'get',
  });
}

// 发布文本条目：textContent 上限 10 万字符，title 可选（缺省后端截取正文前 30 字）；
// direction 固定 2（电脑→手机），返回条目 Vo
export function publishDeliveryText(code, textContent, title) {
  return request({
    url: `/web/delivery/${encodeURIComponent(code)}/items`,
    method: 'post',
    data: { textContent, ...(title ? { title } : {}) },
  });
}

// 发布文件/图片条目（multipart）：kind 2 图片 / 3 文件，单文件 ≤20MB
export function publishDeliveryFile(code, kind, file) {
  const form = new FormData();
  form.append('kind', String(kind));
  form.append('file', file);
  return request({
    url: `/web/delivery/${encodeURIComponent(code)}/items/file`,
    method: 'post',
    data: form, // FormData 由 axios 自动设置带 boundary 的 multipart Content-Type
  });
}

// 取走条目（原子认领）：
// - 文本条目：认领 + 焚毁一步完成，textContent 随响应返回（此后内容不可再读）；
// - 文件条目：仅认领，返回 contentUrl（对端 content 下载路径）；
// - 已被取走 409；会话/条目失效 404
export function takeDeliveryItem(code, itemId) {
  return request({
    url: `/web/delivery/${encodeURIComponent(code)}/items/${itemId}/take`,
    method: 'post',
  });
}

// 文件条目下载：take 认领后走 content 流式转发（二进制体，不走信封）。
// 服务端在流正常完成后即焚毁 MinIO 对象；浏览器中断下载时后端自动释放认领，可重新 take。
// 成功返回 { blob, filename }；错误分支（未认领 409 / 失效 404）后端以 JSON 信封返回，解析后抛业务 Error
export async function downloadDeliveryFile(code, contentUrl, fallbackName) {
  const res = await envelopeHttp.request({
    url: contentUrl,
    method: 'get',
    responseType: 'blob',
  });

  const blob = res.data;
  // 后端错误以 JSON 信封返回（HTTP 200）：识别后读出业务码与 message 转成异常，
  // 与 Android 端「错误分支按 Content-Type=json 识别后转译」的处理对齐
  if (blob && blob.type && blob.type.includes('json')) {
    const text = await blob.text();
    let envelope;
    try {
      envelope = JSON.parse(text);
    } catch {
      throw new Error('下载失败，请稍后重试');
    }
    const error = new Error(envelope.message || '下载失败');
    error.businessCode = envelope.code;
    throw error;
  }

  return {
    blob,
    // 优先解析 Content-Disposition 的 filename*（RFC 5987）；解析失败退回条目标题
    filename: parseFilename(res.headers?.['content-disposition']) || fallbackName || 'download',
  };
}

// 从 Content-Disposition 解析 UTF-8 文件名：inline; filename*=UTF-8''xxx
function parseFilename(disposition) {
  if (!disposition) return '';
  const star = disposition.match(/filename\*=(?:UTF-8'')?([^;]+)/i);
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''));
    } catch {
      return star[1].trim().replace(/^"|"$/g, '');
    }
  }
  const plain = disposition.match(/filename="?([^";]+)"?/i);
  return plain?.[1]?.trim() || '';
}
