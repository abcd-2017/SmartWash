// 短链变更信号 — 创建 / 续期 / 删除成功后置位，列表页 onShown 消费并清除。
//
// 对齐安卓 savedStateHandle 的 CHANGED_KEY 信号（ToolboxListPage.kt:84-92）：
// 列表页不再「每次可见即重拉」，只在有真实变更时刷新，避免详情返回时的无谓请求。
// 模块级布尔即可（列表在 onShown 事件里同步消费，无需驱动重渲染）。

let shortCodeChanged: boolean = false

/** 置位变更信号（创建 / 续期 / 删除成功后调用） */
export function markShortCodeChanged(): void {
  shortCodeChanged = true
}

/**
 * 消费变更信号。
 * @returns 本次 onShown 是否需要重拉列表（true = 有未消费的变更）
 */
export function consumeShortCodeChanged(): boolean {
  if (!shortCodeChanged) {
    return false
  }
  shortCodeChanged = false
  return true
}

// 物品标签变更信号 — 语义同 shortCodeChanged（创建/状态流转/删除回列表重拉）。
// 域内二组信号互不干扰（标签列表只消费标签信号，短码列表只消费短码信号）。

let assetTagChanged: boolean = false

/** 置位标签变更信号（创建 / 状态流转 / 删除成功后调用） */
export function markAssetTagChanged(): void {
  assetTagChanged = true
}

/**
 * 消费标签变更信号。
 * @returns 本次 onShown 是否需要重拉标签列表（true = 有未消费的变更）
 */
export function consumeAssetTagChanged(): boolean {
  if (!assetTagChanged) {
    return false
  }
  assetTagChanged = false
  return true
}
