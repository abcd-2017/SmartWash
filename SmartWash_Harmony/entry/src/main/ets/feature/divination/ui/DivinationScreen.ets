/**
 * 观象台页面根容器 —— 对齐安卓 ui/DivinationScreen.kt。
 *
 * 进入模块即切换宣纸/玄墨子主题（当前固定亮色模式，见 EntryAbility 决策 D1），
 * 背景为原型口径的三段竖向渐变（0 / 0.46 / 1）；可选滚动，摇卦页传 scrollable=false。
 */

import { DivColor } from '../../../constant/DivTheme'

@Builder
@Builder
function emptyContent() {
}

@ComponentV2
export struct DivinationScreen {
  @Param scrollable: boolean = true
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
    }
  }
}
