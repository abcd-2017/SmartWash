import { DesignFontFeature } from "../constant/DesignSystem"

@ComponentV2
export struct OrderItemRow {
  @Param title: string | null = null
  @Param content: string | null = null

  build() {
    Row() {
      Text(`${this.title}`)
        .fontSize(14)
        .fontWeight(FontWeight.Regular)

      Text(`${this.content}`)
        .fontSize(13)
        .fontWeight(FontWeight.Medium)
        // 金额/订单号/时间等数字槽位开 tnum（DESIGN.md 数字等宽规则）
        .fontFeature(DesignFontFeature.TNUM)
    }.width("100%")
    // 列表行高 56vp（DESIGN-SPEC v0.4 §3.4：列表行高 56dp），行内容垂直居中
    .height(56)
    .justifyContent(FlexAlign.SpaceBetween)
    .alignItems(VerticalAlign.Center)
  }
}