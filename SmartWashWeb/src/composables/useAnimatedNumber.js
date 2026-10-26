// src/composables/useAnimatedNumber.js
// 数字滚动动效（评审 T4）：从 0（或上一个值）tween 到目标值，全程 requestAnimationFrame。
// 由真实数据变化触发；尊重 prefers-reduced-motion，关闭时直接落定终值。
import { readonly, ref, watch } from 'vue';

// 数字滚动时长（呼应 --motion-slow，与样式 Token 对齐）
const DURATION_MS = 320;

/**
 * @param {Number|Function} targetSource  目标值来源：可直接给 ref 或一个返回值的 getter
 * @param {Object} [options]
 * @param {Number} [options.duration=320] 滚动时长（ms）
 * @returns {{ value: Ref<Number>, set: (n:Number)=>void }} 当前滚动显示值
 */
export function useAnimatedNumber(targetSource, options = {}) {
  const duration = options.duration ?? DURATION_MS;
  const current = ref(0);
  // 存上一次目标的末尾值，用于小范围差值滚动
  let from = 0;
  let rafId = 0;

  const preferReduced = () =>
    typeof window !== 'undefined' &&
    window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const set = (next) => {
    from = current.value;
    // 动效关闭或无变化：直接落定，避免跳帧闪烁
    if (preferReduced() || Number.isNaN(Number(next)) || Number(from) === Number(next)) {
      current.value = Number(next || 0);
      return;
    }
    const target = Number(next || 0);
    const start = performance.now();
    cancelAnimationFrame(rafId);
    const tick = (now) => {
      const p = Math.min((now - start) / duration, 1);
      // ease-out 进场减速曲线，避免匀速滚动显生硬
      const eased = 1 - Math.pow(1 - p, 3);
      current.value = Math.round(from + (target - from) * eased);
      if (p < 1) {
        rafId = requestAnimationFrame(tick);
      } else {
        current.value = target;
      }
    };
    rafId = requestAnimationFrame(tick);
  };

  // 目标源变化（ref 或 getter）自动触发滚动；首次也触发一次
  watch(
    () => (typeof targetSource === 'function' ? targetSource() : targetSource.value),
    (v) => set(v),
    { immediate: true }
  );

  return { value: readonly(current), set };
}
