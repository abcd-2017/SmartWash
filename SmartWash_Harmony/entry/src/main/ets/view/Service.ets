import { HttpStatusCode } from "@ohos/axios"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight, DesignElevation } from "../constant/DesignSystem"
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { LaundryItem } from "../network/vo/LaundryItem"

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

  /**
   * 根据套餐名称匹配图标
   * 参考安卓端 serviceIcon 函数逻辑
   */
  @Builder
  ServiceIconBuilder(itemName: string | null) {
    if (itemName != null && itemName.includes("干")) {
      // 干洗
      SymbolGlyph($r('sys.symbol.wind'))
        .fontSize(24)
        .fontColor([Color(DesignColor.PRIMARY)])
    } else if (itemName != null && (itemName.includes("精") || itemName.includes("护理") || itemName.includes("奢"))) {
      // 精洗/护理/奢护
      SymbolGlyph($r('sys.symbol.hanger'))
        .fontSize(24)
        .fontColor([Color(DesignColor.PRIMARY)])
    } else if (itemName != null && itemName.includes("熨")) {
      // 熨烫
      SymbolGlyph($r('sys.symbol.steam'))
        .fontSize(24)
        .fontColor([Color(DesignColor.PRIMARY)])
    } else {
      // 默认洗衣
      SymbolGlyph($r('sys.symbol.laundry'))
        .fontSize(24)
        .fontColor([Color(DesignColor.PRIMARY)])
    }
  }

  @Builder
  ServiceItemBuilder(item: LaundryItem) {
    Row() {
      // 左侧图标容器
      Column() {
        this.ServiceIconBuilder(item.itemName)
      }
      .width(48)
      .height(48)
      .borderRadius(14)
      .backgroundColor(Color(DesignColor.PRIMARY_CONTAINER))
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      // 中间文字区域
      Column() {
        Text(item.itemName)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(Color(DesignColor.TEXT_PRIMARY))

        Text(item.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(Color(DesignColor.TEXT_SECONDARY))
          .margin({ top: 2 })

        Text(`¥${item.basePrice}`)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(Color(DesignColor.PRIMARY))
          .margin({ top: 6 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 14 })
    }
    .width("100%")
    .borderRadius(DesignDimens.CARD_RADIUS)
    .backgroundColor(Color(DesignColor.SURFACE))
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: '#1A000000',
      offsetX: 0,
      offsetY: 1
    })
    .padding(DesignDimens.CARD_PADDING)
  }

  build() {
    Scroll(this.scroller) {
      Column() {
        // 页面标题
        Column() {
          Text("洗衣服务")
            .fontSize(DesignFontSize.PAGE_TITLE)
            .fontWeight(DesignFontWeight.BOLD)
            .fontColor(Color(DesignColor.TEXT_PRIMARY))

          Text("专业洗护，安心交付")
            .fontSize(14)
            .fontColor(Color(DesignColor.TEXT_SECONDARY))
            .margin({ top: 4 })
        }
        .alignItems(HorizontalAlign.Start)
        .width("100%")
        .margin({ top: 16 })

        // 服务项目列表
        Column({ space: DesignDimens.CARD_SPACING }) {
          if (this.laundryItem != null) {
            ForEach(this.laundryItem, (item: LaundryItem) => {
              this.ServiceItemBuilder(item)
            })
          }
        }
        .margin({ top: 20 })
        .width("100%")

        // 底部间距
        Column()
          .height(72)
      }
    }
    .scrollable(ScrollDirection.Vertical)
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .backgroundColor(Color(DesignColor.BACKGROUND))
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }
}
