import { promptAction } from '@kit.ArkUI'
import { resString } from '../utils/ResourceString'

/**
 * 统一 toast 兜底封装（评审 #13）：
 * message 为 undefined/null/空串时不弹 "undefined"，改用兜底文案（默认"操作失败"）。
 * 页面代码中所有 `res.message` 直传的 toast 一律改用本函数。
 *
 * @param message 后端返回的提示文案，可能为 undefined/null
 * @param fallback 兜底文案，message 缺失时展示
 * @param duration 显示时长 ms，默认 1500（系统默认短时长）；错误类提示按规范 §8 传 3500
 *                 （对齐安卓 Toast.LENGTH_LONG，3–5s），成功类保持默认
 */
export function showToastSafety(message: string | null | undefined, fallback: string = resString('error_operation_failed'),
  duration: number = 1500): void {
  const text = message ?? ""
  promptAction.showToast({ message: text.length > 0 ? text : fallback, duration: duration })
}
