import { HttpStatusCode } from "@ohos/axios"
import { getUserInfo } from "../network/api/UserApi"
import { promptAction } from "@kit.ArkUI"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { LaundryItem } from "../network/vo/LaundryItem"
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { reservationLaundry } from "../network/api/OrderApi"
import { pathStack } from "../utils/PathStackUtil"
import { DesignColor, DesignDimens, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

@Builder
export function LaundryBuilder() {
  Laundry()
}

@Component
struct Laundry {
  @State userInfo: UserInfoVo | null = null
  @State laundryItemList: Array<LaundryItem> | null = []
  @State selectItemId: number | null = null
  @State totalPrice: number | null = 0

  async aboutToAppear() {
    let [res, itemRes] = await Promise.all([getUserInfo(), getLaundryItems()])
    if (res.code == HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
    if (itemRes.code == HttpStatusCode.Ok) {
      this.laundryItemList = itemRes.data
    } else {
      promptAction.showToast({ message: itemRes.message })
    }
  }

  @Builder
  LaundryTypeItem(item: LaundryItem) {
    let isSelected: boolean = this.selectItemId == item.itemId

    Row() {
      Column() {
        Row() {
          Text(`${item.itemName}`)
            .fontSize(DesignFontSize.CARD_TITLE)
            .fontWeight(FontWeight.Bold)
            .fontColor(DesignColor.TEXT_PRIMARY)
        }.margin({ bottom: 4 })

        Row() {
          Text(`${item.description}`)
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
        }
      }.alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Column() {
        Text(`￥${item.basePrice}`)
          .fontSize(DesignFontSize.BODY)
          .fontWeight(FontWeight.Bold)
          .fontColor(isSelected ? DesignColor.PRIMARY : DesignColor.TEXT_PRIMARY)
      }.margin({ left: 18 })
    }
    .backgroundColor(DesignColor.SURFACE)
    .padding(DesignDimens.CARD_PADDING)
    .border({
      width: isSelected ? 2 : 0.5,
      color: isSelected ? DesignColor.PRIMARY : DesignColor.OUTLINE,
      radius: DesignDimens.CARD_RADIUS
    })
    .borderRadius(DesignDimens.CARD_RADIUS)
    .onClick(() => {
      this.selectItemId = item.itemId
      this.totalPrice = item.basePrice
    })
    .margin({
      top: 6,
      bottom: 6,
      left: DesignDimens.PAGE_PADDING,
      right: DesignDimens.PAGE_PADDING
    })
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.Bottom }) {
        Scroll() {
          Column() {
            // 自定义 PageHeader
            Row() {
              SymbolGlyph($r('sys.symbol.chevron_left'))
                .fontSize(24)
                .fontColor([DesignColor.TEXT_PRIMARY])
                .onClick(() => {
                  pathStack.back()
                })

              Text("预约寄存")
                .fontSize(DesignFontSize.PAGE_TITLE)
                .fontWeight(FontWeight.Bold)
                .fontColor(DesignColor.TEXT_PRIMARY)
                .margin({ left: 8 })
            }
            .width("100%")
            .padding({ left: DesignDimens.PAGE_PADDING, top: 12, bottom: 8 })

            // 选择寄存柜卡片
            Text("选择寄存柜")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            RowSplit().height("16")

            Row() {
              SymbolGlyph($r('sys.symbol.circle_below_local_filled'))
                .fontColor([DesignColor.PRIMARY])
                .fontSize(24)
                .fontWeight(FontWeight.Bold)
              Column() {
                Row() {
                  Text(`${this.userInfo?.schoolVo?.schoolName}`)
                    .fontSize(DesignFontSize.CARD_TITLE)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(DesignColor.TEXT_PRIMARY)
                }.margin({ bottom: 4 })

                Row() {
                  Text(`${this.userInfo?.schoolVo?.location}`)
                    .fontSize(DesignFontSize.CAPTION)
                    .fontColor(DesignColor.TEXT_SECONDARY)
                }
              }
              .alignItems(HorizontalAlign.Start)
              .margin({ left: 16 })

              ColumnSplit().layoutWeight(1)

              Column() {
                Text("更换")
                  .fontSize(DesignFontSize.BODY)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DesignColor.PRIMARY)
              }
            }
            .borderRadius(DesignDimens.CARD_RADIUS)
            .backgroundColor(DesignColor.SURFACE)
            .border({ width: 0.5, color: DesignColor.OUTLINE })
            .padding(DesignDimens.CARD_PADDING)
            .margin({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            RowSplit().height("16")

            // 洗护套餐标题
            Text("洗护套餐")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

            RowSplit().height("8")

            // 套餐列表
            Row() {
              List() {
                ForEach(this.laundryItemList, (item: LaundryItem) => {
                  ListItem() {
                    this.LaundryTypeItem(item)
                  }
                })
              }
            }

            RowSplit().height("72")
          }
        }
        .scrollBar(BarState.Off)
        .width("100%")
        .height("100%")
        .align(Alignment.Top)
        .backgroundColor(DesignColor.BACKGROUND)

        // 底部操作栏
        Row() {
          Column() {
            Row() {
              Text("预估费用")
                .fontSize(DesignFontSize.CAPTION)
                .fontColor(DesignColor.TEXT_SECONDARY)
            }

            Row() {
              Text(`￥${this.totalPrice?.toFixed(1)}`)
                .fontSize(DesignFontSize.SECTION_TITLE)
                .fontWeight(FontWeight.Bold)
                .fontColor(DesignColor.PRIMARY)
            }
          }.alignItems(HorizontalAlign.Start)

          ColumnSplit().layoutWeight(1)

          Column() {
            Button("确定预约")
              .onClick(async (event: ClickEvent) => {
                if (this.selectItemId == null) {
                  promptAction.showToast({ message: "请选择套餐" })
                } else {
                  let res = await reservationLaundry({
                    itemsId: this.selectItemId,
                    totalPrice: this.totalPrice
                  })
                  if (res.code == HttpStatusCode.Ok) {
                    pathStack.pop()
                    pathStack.pushPathByName("Payment", `${res.data}`)
                  } else {
                    promptAction.showToast({ message: res.message })
                  }
                }
              })
              .fontSize(DesignFontSize.CARD_TITLE)
              .fontColor(DesignColor.ON_PRIMARY)
              .backgroundColor(DesignColor.PRIMARY)
              .borderRadius(DesignDimens.BUTTON_RADIUS)
          }
        }
        .backgroundColor(DesignColor.SURFACE)
        .border({ width: { top: 0.5 }, color: DesignColor.OUTLINE })
        .width("100%")
        .height(60)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .zIndex(1)
        .align(Alignment.Bottom)
      }
    }
  }
}
