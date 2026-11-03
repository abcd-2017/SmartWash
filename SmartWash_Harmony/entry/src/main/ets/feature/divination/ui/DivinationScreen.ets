/**
 * 观象台页面根容器 —— 对齐安卓 ui/DivinationScreen.kt。
 *
 * 进入模块即切换宣纸/玄墨子主题（当前固定亮色模式，见 EntryAbility 决策 D1），
 * 背景为原型口径的三段竖向渐变（0 / 0.46 / 1）；可选滚动，摇卦页传 scrollable=false。
 *
 * 安全区策略（recap G1/G2 真机复核结论 + 本批次裁决）：
 * - EntryAbility 未调用 setWindowLayoutFullScreen，窗口内容默认已按系统安全区布局
 *   （NavDestination 内容不伸进系统状态栏/导航条），因此页头（DivPageHeader）
 *   **不加** statusBarsPadding / safeAreaPadding —— 加了反而双重避让。
 * - 需要沉浸延伸的画布页（卦盘 / 摇卦 / 观象台首页罗盘）经 immersive=true 把整页
 *   扩展到系统安全区外（对齐安卓 edge-to-edge 画布），背景铺满、内容布局不变。
 */

import { DivColor } from '../../../constant/DivTheme'

@Builder
@Builder
function emptyContent() {
}

@ComponentV2
export struct DivinationScreen {
  @Param scrollable: boolean = true
  // 沉浸画布：扩展到系统安全区外（TOP/BOTTOM）。仅在画布页开启，页头不再额外避让。
  @Param immersive: boolean = false
  @BuilderParam content: () => void = emptyContent

  build() {
    if (this.scrollable) {
      Scroll() {
        Column() {
          this.content()
        }
        .width('100%')
      }
      .width('100%')
      .height('100%')
      .align(Alignment.TopStart)
      .scrollBar(BarState.Off)
      .linearGradient({
        direction: GradientDirection.Bottom,
        colors: [[DivColor.BG_TOP_LIGHT, 0.0], [DivColor.BG_MID_LIGHT, 0.46], [DivColor.BG_BOTTOM_LIGHT, 1.0]]
      })
      .expandSafeArea(this.immersive ? [SafeAreaType.SYSTEM] : [],
        this.immersive ? [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM] : [])
    } else {
      Column() {
        this.content()
      }
      .width('100%')
      .height('100%')
      .linearGradient({
        direction: GradientDirection.Bottom,
        colors: [[DivColor.BG_TOP_LIGHT, 0.0], [DivColor.BG_MID_LIGHT, 0.46], [DivColor.BG_BOTTOM_LIGHT, 1.0]]
      })
      .expandSafeArea(this.immersive ? [SafeAreaType.SYSTEM] : [],
        this.immersive ? [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM] : [])
    }
  }
}
