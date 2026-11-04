/**
 * 观象台页面根容器 —— 对齐安卓 ui/DivinationScreen.kt。
 *
 * 进入模块即切换宣纸/玄墨子主题（当前固定亮色模式，见 EntryAbility 决策 D1），
 * 背景为原型口径的三段竖向渐变（0 / 0.46 / 1）；可选滚动，摇卦页传 scrollable=false。
 *
 * 安全区策略（沉浸式窗口后更新）：EntryAbility 已 setWindowLayoutFullScreen(true)，
 * 渐变背景随根容器铺满全屏（padding 不影响渐变绘制范围），内容按 safeTopVp/safeBottomVp
 * 避让系统栏。immersive=true 的画布页保留 expandSafeArea 兜底（窗口模式被改回时仍全屏）。
 */

import { DivColor } from '../../../constant/DivTheme'
import { safeBottomVp, safeTopVp } from '../../../utils/SafeArea'

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
        .padding({ bottom: safeBottomVp() })
      }
      .width('100%')
      .height('100%')
      .align(Alignment.TopStart)
      .scrollBar(BarState.Off)
      // 沉浸式（窗口 layoutFullScreen）：渐变背景铺满全屏，内容按状态栏高度避让；
      // padding 不影响背景绘制范围，宣纸渐变仍延伸到系统栏后方
      .padding({ top: safeTopVp() })
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
      // 沉浸式：渐变铺满全屏，内容避让状态栏与底部手势条
      .padding({ top: safeTopVp(), bottom: safeBottomVp() })
      .linearGradient({
        direction: GradientDirection.Bottom,
        colors: [[DivColor.BG_TOP_LIGHT, 0.0], [DivColor.BG_MID_LIGHT, 0.46], [DivColor.BG_BOTTOM_LIGHT, 1.0]]
      })
      .expandSafeArea(this.immersive ? [SafeAreaType.SYSTEM] : [],
        this.immersive ? [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM] : [])
    }
  }
}
