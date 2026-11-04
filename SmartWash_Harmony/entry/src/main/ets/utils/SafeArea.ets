/**
 * 安全区读取工具 — 沉浸式窗口配套（EntryAbility.setupImmersiveWindow 写入 AppStorage）。
 *
 * 窗口已 setWindowLayoutFullScreen(true)：页面背景色/渐变延伸到状态栏与底部导航条后方，
 * 内容由各页面根容器按本工具返回的 vp 值 padding 避让。写入时机在 loadContent 完成回调内，
 * 页面 build 时值已就绪；旋转/折叠经 windowSizeChange 重算。
 *
 * 用法（页面根容器，例如 NavDestination 或其背景承载层）：
 *   .padding({ top: safeTopVp(), bottom: safeBottomVp() })
 */

// 与 EntryAbility 约定的 AppStorage key（保持同步）
const SAFE_AREA_TOP_VP_KEY: string = 'safeAreaTopVp';
const SAFE_AREA_BOTTOM_VP_KEY: string = 'safeAreaBottomVp';

/** 状态栏高度（vp）；EntryAbility 尚未写入时兜底 0（内容顶到屏幕顶，视觉可接受） */
export function safeTopVp(): number {
  const value = AppStorage.get<number>(SAFE_AREA_TOP_VP_KEY)
  return value === undefined ? 0 : value
}

/** 底部导航条/指示器高度（vp） */
export function safeBottomVp(): number {
  const value = AppStorage.get<number>(SAFE_AREA_BOTTOM_VP_KEY)
  return value === undefined ? 0 : value
}
