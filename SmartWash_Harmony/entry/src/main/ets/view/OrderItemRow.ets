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
    .justifyContent(FlexAlign.SpaceBetween)
    .margin({ top: 6, bottom: 6 })
  }
}