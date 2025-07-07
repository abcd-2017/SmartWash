import { CommonConstants } from "../constant/CommonConstants"

@ComponentV2
export struct AccountInfoCard {
  @Param icon: Resource | null = null
  @Param titleContent: string | null = null
  @Param descContent: string | null = null
  @Param buttonContent: string | null = null
  @Event click: () => void;

  build() {
    Row() {
      Column() {
        SymbolGlyph(this.icon)
          .fontSize(26)
          .fontColor([$r('sys.color.icon_primary')])
      }.margin(12)

      Column() {
        Row() {
          Text(this.titleContent)
            .fontSize(16)
            .fontWeight(FontWeight.Bold)
        }

        RowSplit().height("8")
        Row() {
          Text(this.descContent)
            .fontSize(13)
        }
      }.layoutWeight(5)
      .alignItems(HorizontalAlign.Start)
      .justifyContent(FlexAlign.Center)

      Column() {
        Text(this.buttonContent)
          .fontColor($r('sys.color.brand'))
          .fontSize(15)
          .fontWeight(FontWeight.Bold)
          .padding(12)
          .onClick(() => {
            this.click()
          })
      }.layoutWeight(2)
    }
    .width("100%")
    .padding({
      top: 12,
      bottom: 12,
      left: 8,
    })
    .alignItems(VerticalAlign.Center)
    .backgroundColor($r('sys.color.comp_background_primary'))
    .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    .borderRadius(CommonConstants.BORDER_RADIUS)
    .onClick(() => {
      this.click()
    })
  }
}