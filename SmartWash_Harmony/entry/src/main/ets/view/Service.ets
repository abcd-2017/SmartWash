import { HttpStatusCode } from "@ohos/axios"
import { CommonConstants } from "../constant/CommonConstants";
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { LaundryItem } from "../network/entity/LaundryItem"

@Component
export struct Service {
  @State laundryItem: Array<LaundryItem> | null = null;
  private scroller: Scroller = new Scroller()

  async aboutToAppear() {
    let res = await getLaundryItems()
    if (res.code == HttpStatusCode.Ok) {
      this.laundryItem = res.data
    }
  }

  @Builder
  ServiceItemBuilder(priceContent?: string, titleContent?: string, descContent?: string, iconContent?: Resource) {
    Row() {
      if (iconContent != null) {
        Column() {
          SymbolGlyph(iconContent)
            .fontSize(24)
            .fontColor([$r('sys.color.icon_primary')])
        }.layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .margin({ right: 8 })
      }
      Column() {
        Row() {
          Text(titleContent)
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
        }

        Row() {
          Text(descContent)
            .fontSize(12)
            .lineHeight(20)
        }
      }.layoutWeight(5)
      .alignItems(HorizontalAlign.Start)

      Column() {
        Text(priceContent)
          .fontSize(14)
          .fontColor($r('sys.color.brand'))
      }.layoutWeight(1)
    }
    .width("100%")
    .borderRadius(CommonConstants.BORDER_RADIUS)
    .backgroundColor($r('sys.color.comp_background_primary'))
    .padding(16)
    .shadow(ShadowStyle.OUTER_DEFAULT_SM)
  }

  build() {
    Scroll(this.scroller) {
      Column() {
        Column() {
          Text("洗衣服务")
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .margin({ top: 8 })
        }.alignItems(HorizontalAlign.Start)
        .width("100%")

        RowSplit().height("16")

        Column() {
          Row() {
            Text("服务说明")
              .fontSize(16)
              .fontWeight(FontWeight.Bold)
          }

          Row() {
            Text("• 所有服务均包含免费取送\n" +
              "• 标准服务24小时内完成\n" +
              "• 支持在线支付和积分抵扣")
              .fontSize(14)
              .lineHeight(24)
          }
        }
        .width("100%")
        .borderRadius(CommonConstants.BORDER_RADIUS)
        .backgroundColor($r('sys.color.comp_background_primary'))
        .alignItems(HorizontalAlign.Start)
        .padding(16)
        .shadow(ShadowStyle.OUTER_DEFAULT_SM)

        RowSplit().height("16")

        //洗衣套餐列表
        Column() {
          ForEach(this.laundryItem, (item: LaundryItem) => {
            this.ServiceItemBuilder(`￥${item.basePrice}`, `${item.itemName}`, `${item.description}`)
            RowSplit().height("16")
          })
        }

        this.ServiceItemBuilder("免费", "取件服务", "扫码取件，快捷服务", $r('sys.symbol.briefcase'))

        RowSplit().height("72")
      }
    }
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .backgroundColor($r('sys.color.background_primary'))
    .padding({ left: 16, right: 16 })
  }
}
