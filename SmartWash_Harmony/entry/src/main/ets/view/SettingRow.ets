@ComponentV2
export struct SettingRow {
  @Param icon: Resource | null = null
  @Param title: string | null = null
  @Param description: string | null = null
  @Event rowClick: () => void

  build() {
    Row() {
      Column() {
        SymbolGlyph(this.icon)
          .fontSize(18)
          .margin({ left: 12, right: 12 })
          .fontColor([$r('sys.color.font_primary')])
      }.layoutWeight(1)

      Column() {
        Row() {
          Text(this.title)
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
        }

        RowSplit().height("8")
        Row() {
          Text(this.description)
            .fontSize(13)
        }
      }.layoutWeight(5)
      .alignItems(HorizontalAlign.Start)

      Column() {
        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(24)
          .fontColor([$r('sys.color.font_primary')])
      }
      .layoutWeight(1)
    }.width("100%")
    .padding({ top: 12, bottom: 12 })
    .onClick(() => {
      this.rowClick()
    })
  }
}