import { promptAction } from '@kit.ArkUI'

/**
 * 统一 toast 兜底封装（评审 #13）：
 * message 为 undefined/null/空串时不弹 "undefined"，改用兜底文案（默认"操作失败"）。
 * 页面代码中所有 `res.message` 直传的 toast 一律改用本函数。
 *
 * @param message 后端返回的提示文案，可能为 undefined/null
 * @param fallback 兜底文案，message 缺失时展示
 */
export function showToastSafety(message: string | null | undefined, fallback: string = "操作失败"): void {
  const text = message ?? ""
  promptAction.showToast({ message: text.length > 0 ? text : fallback })
}
