// src/utils/logger.js
// SmartWash 统一分级日志（不引第三方库）：
// - 开发环境：debug/info/warn/error 全量输出到对应 console 方法；
// - 生产环境：只输出 warn/error（分别走 console.warn/console.error，保留原生堆栈能力），
//   debug/info 直接短路不输出。
//
// 生产门控的 tree-shake 说明：Vite 构建会把 `import.meta.env.PROD` 静态替换为字面量
// true/false（define 注入，条件可静态分析），因此生产包中 debug/info 的输出代码位于
// `if (true) return` 之后的不可达分支，会被 esbuild 压缩器整体消除，不进产物。
//
// 敏感红线（调用方约定 + 本模块兜底）：
// - 字符串参数中的 11 位手机号自动打码（如 138****5678）；
// - Error 参数只保留 name/message/stack（message 同样打码），丢弃 config/response 等
//   附加属性，避免把 Authorization 头、完整请求/响应体带进日志；
// - 密码、token、验证码、完整请求/响应体一律不得作为日志参数（错误摘要只取 message/url/status）。

// 日志前缀，便于控制台过滤
const PREFIX = '[SmartWash]';

// 11 位大陆手机号。打码时要求匹配片段前后都不是数字，避免把长数字串（订单号等）的片段误判
const PHONE_RE = /1[3-9]\d{9}/g;

function maskPhones(text) {
  return text.replace(PHONE_RE, (match, offset, whole) => {
    const prev = offset > 0 ? whole[offset - 1] : '';
    const next = offset + match.length < whole.length ? whole[offset + match.length] : '';
    if (/\d/.test(prev) || /\d/.test(next)) {
      return match; // 与其他数字相连，不是独立手机号，不打码
    }
    return `${match.slice(0, 3)}****${match.slice(7)}`;
  });
}

// Error 参数脱敏：重建只含 name/message/stack 的普通 Error。
// 不直接透传原对象——axios 的 error 上挂有 config/response（含 Bearer 头与完整报文），不能进日志；
// cause 链可能引用同类对象，一并丢弃。
function sanitizeError(err) {
  const rawMessage = typeof err.message === 'string' ? err.message : String(err.message);
  const maskedMessage = maskPhones(rawMessage);
  const copy = new Error(maskedMessage);
  copy.name = err.name;
  if (typeof err.stack === 'string' && rawMessage) {
    // 堆栈首行含原始 message，替换为打码后的文本，其余堆栈帧原样保留
    copy.stack = err.stack.split(rawMessage).join(maskedMessage);
  }
  return copy;
}

function sanitize(args) {
  return args.map((arg) => {
    if (typeof arg === 'string') {
      return maskPhones(arg);
    }
    if (arg instanceof Error) {
      return sanitizeError(arg);
    }
    return arg;
  });
}

export function debug(...args) {
  // 生产构建：本条件被静态替换为 if (true)，下方输出代码不进产物
  if (import.meta.env.PROD) return;
  // eslint-disable-next-line no-console
  console.debug(PREFIX, ...sanitize(args));
}

export function info(...args) {
  // 生产构建：同上，输出代码不进产物
  if (import.meta.env.PROD) return;
  // eslint-disable-next-line no-console
  console.info(PREFIX, ...sanitize(args));
}

export function warn(...args) {
  console.warn(PREFIX, ...sanitize(args));
}

export function error(...args) {
  console.error(PREFIX, ...sanitize(args));
}

export default { debug, info, warn, error };
