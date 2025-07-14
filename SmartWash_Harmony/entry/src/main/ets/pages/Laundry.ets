import { HttpStatusCode } from "@ohos/axios"
import { CommonConstants } from "../constant/CommonConstants"
import { getUserInfo } from "../network/api/UserApi"
import { ItemTitle } from "../view/ItemTitle"
import { promptAction } from "@kit.ArkUI"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { LaundryItem } from "../network/vo/LaundryItem"
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { reservationLaundry } from "../network/api/OrderApi"
import { pathStack } from "../utils/PathStackUtil"

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
    let res = await getUserInfo()
    let itemRes = await getLaundryItems()
    if (res.code == HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
    if (itemRes.code == HttpStatusCode.Ok) {
      this.laundryItemList = itemRes.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  @Builder
  LaundryTypeItem(item: LaundryItem) {
    Row() {
      Column() {
        Row() {
          Text(`${item.itemName}`).fontSize(16)
            .fontWeight(FontWeight.Bold)
        }.margin({ bottom: 4 })

        Row() {
          Text(`${item.description}`)
            .fontSize(13)
        }
      }.alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Column() {
        Text(`￥${item.basePrice}`)
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
      }.margin({ left: 18 })
    }
    .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    .backgroundColor($r('sys.color.comp_background_primary'))
    .padding(18)
    .border(
      {
        width: 2,
        color: $r(this.selectItemId == item.itemId ? 'sys.color.brand' : 'sys.color.comp_background_primary'),
        radius: CommonConstants.BORDER_RADIUS
      })
    .borderRadius(CommonConstants.BORDER_RADIUS)
    .onClick(() => {
      this.selectItemId = item.itemId
      this.totalPrice = item.basePrice
    })
    .margin({
      top: 8,
      bottom: 8,
      left: 16,
      right: 16
    })
  }

  build() {
    NavDestination() {
      Stack({ alignContent: Alignment.Bottom }) {
        Scroll() {
          Column() {
            ItemTitle({ title: "选择寄存柜" }).padding({ left: 16, right: 16 })
            RowSplit().height("16")

            Row() {
              Column() {
                SymbolGlyph($r('sys.symbol.circle_below_local_filled'))
                  .fontColor([$r('sys.color.font_primary')])
                  .fontSize(24)
                  .fontWeight(FontWeight.Bold)
              }.margin({ right: 16 })

              Column() {
                Row() {
                  Text(`${this.userInfo?.schoolVo?.schoolName}`).fontSize(16)
                    .fontWeight(FontWeight.Bold)
                }.margin({ bottom: 4 })

                Row() {
                  Text(`${this.userInfo?.schoolVo?.location}`)
                    .fontSize(13)
                }
              }.alignItems(HorizontalAlign.Start)

              ColumnSplit().layoutWeight(1)

              Column() {
                Text(`更换`)
                  .fontSize(14)
                  .fontWeight(FontWeight.Bold)
              }
            }
            .borderRadius(CommonConstants.BORDER_RADIUS)
            .shadow(ShadowStyle.OUTER_DEFAULT_SM)
            .backgroundColor($r('sys.color.comp_background_primary'))
            .padding(18)
            .margin({ left: 16, right: 16 })

            RowSplit().height("16")
            ItemTitle({ title: "洗护套餐" }).padding({ left: 16, right: 16 })
            RowSplit().height("8")

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
        .backgroundColor($r('sys.color.background_primary'))

        Row() {
          Column() {
            Row() {
              Text("预估费用")
                .fontSize(13)
            }

            Row() {
              Text(`￥${this.totalPrice?.toFixed(1)}`)
                .fontSize(16)
                .fontWeight(FontWeight.Bold)
                .fontColor($r('sys.color.brand'))
            }
          }.alignItems(HorizontalAlign.Start)

          ColumnSplit().layoutWeight(1)

          Column() {
            Button("确定预约").onClick(async (event: ClickEvent) => {
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
            }).fontSize(15)
          }
        }
        .backgroundColor($r('sys.color.background_primary'))
        .width("100%")
        .height("60")
        .padding({ left: 18, right: 18 })
        .zIndex(1)
        .align(Alignment.Bottom)
      }
    }.title("预约寄存")
  }
}