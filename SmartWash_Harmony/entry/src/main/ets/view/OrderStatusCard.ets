import { CommonConstants } from "../constant/CommonConstants"

@ComponentV2
export struct OrderStatusCard {
  @Param icon: Resource | null = null
  @Param content: string | null = null
  @Param orderCount: string | null = null
  @Event orderCardClick: () => void

  build() {
   Column(){
     Column() {
       Row() {
         SymbolGlyph(this.icon)
           .fontColor([$r('sys.color.icon_primary')])
           .fontSize("26")
       }

       Text(`${this.content}`)
         .fontSize(14)
         .margin({ top: 12, bottom: 2 })

       Text(`${this.orderCount}个订单`)
         .fontSize(12)
     }
     .width("80")
     .height("100")
     .backgroundColor($r('sys.color.comp_background_primary'))
     .shadow(ShadowStyle.OUTER_DEFAULT_SM)
     .borderRadius(CommonConstants.BORDER_RADIUS)
     .justifyContent(FlexAlign.Center)
     .onClick(() => {
       this.orderCardClick()
     })
   }.padding(10)
  }
}