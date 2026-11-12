import { common } from '@kit.AbilityKit'
import { COLOR_RES } from './ColorResources'

/**
 * 资源色即时取值（供 Canvas / 非 build 上下文使用）。
 *
 * 走 base/dark 资源限定符：系统深浅色切换时返回值随之变化，无需手动回写令牌。
 * 每次调用即时求值，避免模块加载时冻结令牌值导致切换后仍拿旧色
 * （对齐安卓 Compose 运行时读取 ComposeColor 的做法）。
 *
 * @param name color.json 中的资源名（不含 app.color. 前缀）
 * @returns ARGB 数值色
 */
export function resColor(name: string): number {
  const res: Resource | undefined = COLOR_RES[name]
  if (res === undefined) {
    return 0xFF000000
  }
  const context: common.UIAbilityContext | undefined = getContext() as common.UIAbilityContext | undefined
  if (context === undefined) {
    // 模块加载期 context 未就绪：回落不透明黑（仅模块加载兜底路径触发）
    return 0xFF000000
  }
  return context.resourceManager.getColorSync(res)
}

/**
 * ResourceColor → 数值 ARGB（Canvas 绘制专用）。
 *
 * 组件颜色入参用 @Param + ResourceColor 声明（父组件 $r 直绑，随系统深浅色自动切换），
 * 但 Canvas 的 fillStyle/strokeStyle 不接受 Resource，绘制前经此方法按值解析：
 * number 原样返回；Resource 走资源管理器按当前配置解析。
 * 注意：解析发生在调用瞬间，画布重绘依赖 @Monitor/生命周期触发（ThemeRefresh 重建）。
 */
export function resolveArgb(color: ResourceColor): number {
  if (typeof color === 'number') {
    return color
  }
  const context: common.UIAbilityContext | undefined = getContext() as common.UIAbilityContext | undefined
  if (context === undefined) {
    return 0xFF000000
  }
  return context.resourceManager.getColorSync(color as Resource)
}
