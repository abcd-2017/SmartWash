// src/utils/format.js
// 通用格式化工具（评审 #13：收敛 11 个列表页逐字重复的 formatTime）
import dayjs from 'dayjs';

// 默认时间格式
export const DEFAULT_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/**
 * 统一时间格式化
 * 空值（null/undefined/''）返回占位符 "-"，避免出现 "Invalid Date" 或被解析成当前时间
 * @param {String|Number|Date|null} time 时间值
 * @param {String} fmt dayjs 格式串，默认 YYYY-MM-DD HH:mm:ss
 * @returns {String}
 */
export function formatTime(time, fmt = DEFAULT_TIME_FORMAT) {
  if (time === null || time === undefined || time === '') {
    return '-';
  }
  return dayjs(time).format(fmt);
}

/**
 * 千分位整数格式化（笔数/数量等计数场景），空值或非法值返回 '0'
 * @param {Number|String|null} value 数值
 * @returns {String}
 */
export function formatThousands(value) {
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return '0';
  }
  return num.toLocaleString('en-US');
}

/**
 * 金额格式化：千分位 + 固定两位小数，空值或非法值返回占位符（默认 '0.00'）
 * 资金明细列可传 '-'，让 null 金额显示为 '-' 而非伪装成 ￥0.00
 * 与页面既有 ￥ 前缀配合使用（￥{{ formatAmount(x) }}）
 * @param {Number|String|null} value 金额
 * @param {String} placeholder 空值/非法值占位符，默认 '0.00'
 * @returns {String}
 */
export function formatAmount(value, placeholder = '0.00') {
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return placeholder;
  }
  return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * 文件大小人性化展示（字节 → B/KB/MB），用于游戏包体积列
 * 空值（null/undefined）返回占位符 "-"（外链等无体积场景）
 * @param {Number|null} bytes 字节数
 * @returns {String}
 */
export function formatFileSize(bytes) {
  if (bytes === null || bytes === undefined || bytes === '' || Number.isNaN(Number(bytes))) {
    return '-';
  }
  const size = Number(bytes);
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}
