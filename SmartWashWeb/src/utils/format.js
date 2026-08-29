// src/utils/format.js
// 通用格式化工具（评审 #13：收敛 11 个列表页逐字重复的 formatTime）
import dayjs from 'dayjs'

// 默认时间格式
export const DEFAULT_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'

/**
 * 统一时间格式化
 * 空值（null/undefined/''）返回占位符 "-"，避免出现 "Invalid Date" 或被解析成当前时间
 * @param {String|Number|Date|null} time 时间值
 * @param {String} fmt dayjs 格式串，默认 YYYY-MM-DD HH:mm:ss
 * @returns {String}
 */
export function formatTime(time, fmt = DEFAULT_TIME_FORMAT) {
  if (time === null || time === undefined || time === '') {
    return '-'
  }
  return dayjs(time).format(fmt)
}
