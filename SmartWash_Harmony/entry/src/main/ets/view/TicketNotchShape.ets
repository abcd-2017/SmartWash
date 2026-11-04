// 票券打孔公共组件 — 对齐安卓 TicketShape（CouponPage.kt:354-398）
// 安卓为 Compose 自定义 Shape：圆角矩形路径在左右缘中点各挖一个半圆凹口（φ14 / r7），
// 且 Surface 描边跟随该 Shape。ArkUI 声明式 API 无对应的自定义 Shape 裁剪能力
// （.clip() 仅接受内置形状，PathShape 需 SVG 命令串，本仓库无先例），
// 故改用「页面底色圆遮罩」近似：几何位置与半径与安卓完全一致（左右缘中点、r7），
// 视觉上形成同样的半圆凹口。
//
// 近似度：凹口位置 / 半径 / 卡体圆角 / 描边 / 阴影均一致；
// 唯凹口处描边由底色圆外半覆盖（与安卓 Shape 描边跟随等效），
// 且圆外半会覆盖卡体投影，孔位附近阴影被挖去一小块（肉眼难辨）。
// 约束：卡片所在容器底色须恒为 DesignColor.BACKGROUND（优惠券页根容器即该底色）。

import { DesignColor } from "../constant/DesignSystem"

/** 虚线段高（安卓 6dp） */
const DASH_SEGMENT_HEIGHT: number = 6
/** 虚线间隔（安卓 4dp） */
const DASH_SEGMENT_GAP: number = 4

/**
 * 票券打孔覆盖层 — 卡片 Stack 的顶层覆盖层。
 * 左圆圆心落在卡左缘（x=0），右圆圆心落在卡右缘（x=width），只露卡内半圆。
 *
 * @param notchRadius 打孔半径（默认 7，对齐安卓 φ14）
 * @param notchColor  打孔底色（默认页面底色，形成凹口观感）
 */
@ComponentV2
export struct TicketNotch {
  @Param notchRadius: number = 7
  @Param notchColor: number = DesignColor.BACKGROUND

  build() {
    Row() {
      // 左孔：自然位置 x ∈ [0, 2r]，offset -r 后圆心落在 x=0
      Column()
        .width(this.notchRadius * 2)
        .height(this.notchRadius * 2)
        .borderRadius(this.notchRadius)
        .backgroundColor(this.notchColor)
        .offset({ x: -this.notchRadius })
        .hitTestBehavior(HitTestMode.None)

      Blank()
        .hitTestBehavior(HitTestMode.None)

      // 右孔：自然位置 x ∈ [width-2r, width]，offset +r 后圆心落在 x=width
      Column()
        .width(this.notchRadius * 2)
        .height(this.notchRadius * 2)
        .borderRadius(this.notchRadius)
        .backgroundColor(this.notchColor)
        .offset({ x: this.notchRadius })
        .hitTestBehavior(HitTestMode.None)
    }
    .width('100%')
    .height('100%')
    .alignItems(VerticalAlign.Center)
    .hitTestBehavior(HitTestMode.None)
  }
}

/**
 * 票券垂直虚线撕裂线 — 段 6 / 隔 4（安卓 CouponPage.kt:250-266），
 * 段数按可用高度动态计算以填满「上下留 12」的整个区域。
 *
 * @param dashHeight 虚线区高度（= 卡高 - 24）
 * @param dashColor  虚线颜色（默认卡片描边色）
 */
@ComponentV2
export struct TicketDashedLine {
  @Param dashHeight: number = 64
  @Param dashColor: number = DesignColor.OUTLINE

  /** 依高度计算段数（ArkTS build 内不允许循环语句，收敛为方法） */
  private dashKeys(): number[] {
    const step: number = DASH_SEGMENT_HEIGHT + DASH_SEGMENT_GAP
    const count: number = Math.max(1, Math.floor((this.dashHeight + DASH_SEGMENT_GAP) / step))
    const keys: number[] = []
    for (let i = 0; i < count; i++) {
      keys.push(i)
    }
    return keys
  }

  build() {
    Column({ space: DASH_SEGMENT_GAP }) {
      ForEach(this.dashKeys(), (index: number) => {
        Column()
          .width(1)
          .height(DASH_SEGMENT_HEIGHT)
          .backgroundColor(this.dashColor)
          .borderRadius(0.5)
          .hitTestBehavior(HitTestMode.None)
      }, (index: number) => index.toString())
    }
    .height(this.dashHeight)
    .justifyContent(FlexAlign.Center)
    .hitTestBehavior(HitTestMode.None)
  }
}
