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
    }.width("100%")
    // 列表行高 56vp（DESIGN-SPEC v0.4 §3.4：列表行高 56dp），行内容垂直居中
    .height(56)
    .justifyContent(FlexAlign.SpaceBetween)
    .alignItems(VerticalAlign.Center)
  }
}