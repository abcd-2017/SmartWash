/**
 * 统一点击/输入防抖工具（差异分析 01 §4.3，对齐安卓 ClickDebounce 的公共能力定位）。
 * 各页面此前各自手写 timerId + setTimeout 模板，收敛到本工具统一管理：
 * - debounce：延迟触发，重复调用自动取消上一次未触发的任务（输入搜索等场景）；
 * - 页面销毁前必须调用 cancel 清理未触发任务（定时器硬规则）。
 */
export class Debouncer {
  private timerId: number = -1
  private lastThrottleAt: number = 0

  /** 是否有待触发的防抖任务 */
  isPending(): boolean {
    return this.timerId !== -1
  }

  /**
   * 防抖触发：连续调用只保留最后一次，停顿 delayMs 后执行 action。
   * @param action 停顿后要执行的动作
   * @param delayMs 防抖间隔（毫秒）
   */
  debounce(action: () => void, delayMs: number): void {
    this.cancel()
    this.timerId = setTimeout(() => {
      this.timerId = -1
      action()
    }, delayMs)
  }

  /**
   * 节流触发：intervalMs 间隔内首次调用立即执行 action，其余忽略（立即执行语义，
   * 对齐安卓 rememberDebouncedClick，用于去支付/去取件等动作按钮连点防护）。
   * @param action 要执行的动作
   * @param intervalMs 节流间隔（毫秒）
   */
  throttle(action: () => void, intervalMs: number): void {
    const now = Date.now()
    if (now - this.lastThrottleAt < intervalMs) {
      return
    }
    this.lastThrottleAt = now
    action()
  }

  /** 取消未触发的任务（aboutToDisappear 必调，防页面销毁后回调） */
  cancel(): void {
    if (this.timerId !== -1) {
      clearTimeout(this.timerId)
      this.timerId = -1
    }
  }
}
