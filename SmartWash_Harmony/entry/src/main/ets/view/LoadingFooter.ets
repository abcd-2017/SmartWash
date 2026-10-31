// 分页「加载更多」尾部指示 — 对齐安卓分页 footer（OrderPage.kt:319-342 等）：
// 仅 spinner，无文字（安卓 footer 同样只有 spinner，颜色 primary）。
// reduced motion 降级三点灰未接入：鸿蒙无系统动画时长开关可读（divReduceMotion 同决策），
// 见 .claude/ui-alignment-checklist.md「暂不对齐」节。
import { DesignColor } from '../constant/DesignSystem'

@ComponentV2
export struct LoadingFooter {
  @Param isLoading: boolean = false;

  build() {
    if (!!this.isLoading) {
      Row() {
        LoadingProgress()
          .height(28)
          .width(28)
          .color(DesignColor.PRIMARY)
      }
      .width("100%")
      .height(56)
      .justifyContent(FlexAlign.Center)
    }
  }
}
