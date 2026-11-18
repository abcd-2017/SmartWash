import { resColor } from '../utils/ResourceColor'
// 分页「加载更多」尾部指示 — 对齐安卓分页 footer（OrderPage.kt:319-342 等）：
// 仅 spinner，无文字（安卓 footer 同样只有 spinner，颜色 primary）。
// reduced motion 降级（无限旋转 → 静止圆环）未接入： LoadingProgress 为系统转圈，
// 无「取消旋转」属性；最接近方案见 utils/ReduceMotion.ets + AppComponents.LoadingDots（静态三点）。
// 注：原「SDK 无系统动画时长开关」结论已更新，可读 ANIMATOR_DURATION_SCALE，但系统 spinner 无静止档，
// 需替换为 LoadingDots 才能降级（会改变分页尾形态，未在本次动效对齐中处理）。


@ComponentV2
export struct LoadingFooter {
  @Param isLoading: boolean = false;

  build() {
    if (!!this.isLoading) {
      Row() {
        LoadingProgress()
          .height(28)
          .width(28)
          .color($r('app.color.PRIMARY'))
      }
      .width("100%")
      .height(56)
      .justifyContent(FlexAlign.Center)
    }
  }
}
