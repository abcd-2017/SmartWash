// src/api/secret.js
// 阅后即焚（私密文本）公开查看页 API 层，路由 /s/:code（免登录匿名访问）。
// 接口路径与后端 ToolboxSecretController 对齐（/web/s/**，匿名公开段，码即凭证）。
//
// 与 delivery.js 同款约定：不走默认 http 实例（其对 code !== 200 一律 reject 并丢失业务码），
// 走 envelopeHttp 拿完整信封 { code, message, data }，页面按业务码分支：
// 404（不存在/非私密文本）、410（已焚毁/已过期）、403（口令错误，未焚毁可重试）。
import { envelopeHttp } from '@/utils/http';

// 业务码常量（信封 code 字段，非 HTTP 状态码）
export const SECRET_CODE = {
  OK: 200,
  WRONG_PASSWORD: 403, // 口令错误（响应故意延迟约 500ms 防爆破；不焚毁）
  NOT_FOUND: 404, // 短码不存在或非私密文本
  GONE: 410, // 已焚毁 / 已过期（终态）
};

// 统一执行：HTTP 200 时返回完整信封 { code, message, data }；
// 网络层错误（超时/断网等）抛 Error，由页面按「网络异常」提示
async function request(config) {
  const res = await envelopeHttp.request(config);
  return res.data;
}

// 元信息（只读，绝不焚毁）：返回 { requiresPassword }；404/410 由页面分支
export function getSecretMeta(code) {
  return request({
    url: `/web/s/${encodeURIComponent(code)}/meta`,
    method: 'get',
  });
}

// 一次性揭示（必须用户点击触发）：成功即焚毁并返回 { content, expireAt }；
// 口令错误 403 且不焚毁；已焚毁/已过期 410。有口令传 body，无口令不带 body
export function viewSecret(code, password) {
  return request({
    url: `/web/s/${encodeURIComponent(code)}/view`,
    method: 'post',
    ...(password ? { data: { password } } : {}),
  });
}
