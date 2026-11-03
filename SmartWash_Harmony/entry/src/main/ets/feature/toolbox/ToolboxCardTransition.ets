// 短链「一镜到底」近似转场 — 对齐安卓 sharedElement("shortcode-card-{id}")。
//
// 安卓侧列表卡与详情信息卡经共享元素平滑展开（ToolboxListPage.kt:75-77,268-281 +
// ShortCodeDetailPage.kt:113-115,200-210,226-235）。鸿蒙 ArkUI 无跨 NavDestination
// 的共享元素能力（customNavContentTransition 仅作用于 Navigation 容器，且拿不到对侧视图），
// 故此处用 NavDestination.customTransition 做「详情信息卡由列表卡展开」的近似：
// - 进入（PUSH / isEnter）：信息卡 scale 0.94 → 1 + fade 0 → 1，模拟卡片展开；
// - 退出（POP / isEnter）：信息卡反向收拢，模拟卡片回落。
//
// 时长按规范 §7 时长层级（screen 400ms，退出 ≈ 进入的 70%）。
// reduced motion：鸿蒙当前无系统级 reduce motion 开关（G3），故不做降级分支。

/** 进入时长（screen 档，规范 §7） */
export const CARD_TRANSITION_ENTER_MS: number = 360
/** 退出时长（≈ 进入的 70%） */
export const CARD_TRANSITION_EXIT_MS: number = 250

/**
 * 详情信息卡转场委托。
 * @param operation 导航操作（PUSH / POP）
 * @param isEnter 当前页是否为进入页
 * @param onEnter 进入时驱动信息卡展开的状态置位
 * @param onExit 退出时驱动信息卡收拢的状态置位
 */
export function toolboxCardTransition(
  operation: NavigationOperation,
  isEnter: boolean,
  onEnter: () => void,
  onExit: () => void
): Array<NavDestinationTransition> | undefined {
  if (operation === NavigationOperation.PUSH && isEnter) {
    return [{
      duration: CARD_TRANSITION_ENTER_MS,
      curve: Curve.EaseInOut,
      event: () => {
        onEnter()
      }
    }]
  }
  if (operation === NavigationOperation.POP && !isEnter) {
    return [{
      duration: CARD_TRANSITION_EXIT_MS,
      curve: Curve.EaseIn,
      event: () => {
        onExit()
      }
    }]
  }
  return undefined
}
