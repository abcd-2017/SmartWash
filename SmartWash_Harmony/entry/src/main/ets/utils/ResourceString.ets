import { common } from '@kit.AbilityKit'
import { STRING_RES } from './StringResources'
import { STRING_FALLBACK } from './StringFallback'

/**
 * 资源文案即时取值（供静态文案表 / 非 build 上下文使用）。
 *
 * 与 Color 一样走资源文件：文案集中收在 string.json，便于统一管理与后续多语言扩展。
 * 每次调用即时求值，避免模块加载时冻结文案。
 *
 * 无 UIAbility 上下文的环境（hypium 本地单测、模块加载期）取不到资源，
 * 回落 STRING_FALLBACK 字面值，保证逻辑层默认文案可用。
 *
 * @param name string.json 中的资源名（不含 app.string. 前缀）
 * @returns 文案
 */
export function resString(name: string): string {
  const context: common.UIAbilityContext | undefined = getContext() as common.UIAbilityContext | undefined
  if (context !== undefined) {
    const res: Resource | undefined = STRING_RES[name]
    if (res !== undefined) {
      return context.resourceManager.getStringSync(res)
    }
  }
  const fallback: string | undefined = STRING_FALLBACK[name]
  return fallback !== undefined ? fallback : name
}
