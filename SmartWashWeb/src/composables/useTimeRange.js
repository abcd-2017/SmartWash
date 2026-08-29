// src/composables/useTimeRange.js
// 时间范围筛选的双向绑定 computed（评审 #14）：
// 消除 4 个列表页（Order/Recharge/Payment/Locker）逐字重复的 timeRange computed。
import { computed } from 'vue';

/**
 * @param {Object} query 响应式查询对象（须含起始/结束时间两个字段）
 * @param {String} [startKey='startTime'] 起始时间字段名
 * @param {String} [endKey='endTime'] 结束时间字段名
 * @returns {import('vue').ComputedRef} 供 el-date-picker v-model 使用
 */
export function useTimeRange(query, startKey = 'startTime', endKey = 'endTime') {
  return computed({
    get: () => [query[startKey], query[endKey]],
    set: (val) => {
      query[startKey] = val?.[0] || null;
      query[endKey] = val?.[1] || null;
    },
  });
}
