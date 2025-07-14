import { HttpStatusCode } from "@ohos/axios"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { pathStack } from "../utils/PathStackUtil"
import { IconType, promptAction } from "@kit.ArkUI"
import { ItemTitle } from "./ItemTitle"
import { CommonConstants } from "../constant/CommonConstants"
import { OrderVo } from "../network/vo/OrderVo"
import { getWashingOrder } from "../network/api/OrderApi"

@ComponentV2
export struct IndexPage {
  private scroller = new Scroller()
  @Local userInfo: UserInfoVo | null = null
  @Event userInfoClick: () => void
  @Local orderList: Array<OrderVo> | null = []

  aboutToAppear() {
    this.refreshUserInfo()
    this.getOrderList()
    setInterval(() => {
      this.getOrderList()
    }, 5000)
  }

  async refreshUserInfo() {
    let res = await getUserInfo()
    if (res.code == HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  async getOrderList() {
    let res = await getWashingOrder();
    if (res.code == HttpStatusCode.Ok) {
      this.orderList = res.data
    } else {
      promptAction.showToast({ message: res.message })
    }
  }

  @Builder
  ServiceCard(icon: Resource, title: string, content: string, itemClick: () => void) {
    Column() {
      Row() {
        SymbolGlyph(icon)
          .fontColor([$r('sys.color.font_primary')])
          .fontSize(24)
      }.margin({ bottom: 8 })

      Row() {
        Text(title)
          .fontWeight(FontWeight.Bold)
      }.margin({ bottom: 6 })

      Row() {
        Text(content)
          .fontSize(13)
      }
    }
    .onClick(() => itemClick())
    .borderRadius(CommonConstants.BORDER_RADIUS)
    .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    .backgroundColor($r('sys.color.comp_background_primary'))
    .layoutWeight(1)
    .padding(14)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  StatusCard(icon: Resource, title: string, content: string) {
    Row() {
      Column() {
        SymbolGlyph(icon)
          .fontColor([$r('sys.color.font_primary')])
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
      }.margin({ right: 12 })

      Column() {
        Row() {
          Text(title).fontSize(13)
        }.margin({ bottom: 4 })

        Row() {
          Text(content)
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
        }
      }.alignItems(HorizontalAlign.Start)
    }
    .borderRadius(CommonConstants.BORDER_RADIUS)
    .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    .backgroundColor($r('sys.color.comp_background_primary'))
    .layoutWeight(1)
    .padding(18)
  }

  @Builder
  IndexOrderItem(icon: Resource, title: string, content: string, price: string, itemClick: () => void) {
    Row() {
      Column() {
        SymbolGlyph(icon)
          .fontColor([$r('sys.color.font_primary')])
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
      }.margin({ right: 16 })

      Column() {
        Row() {
          Text(title).fontSize(16)
            .fontWeight(FontWeight.Bold)
        }.margin({ bottom: 4 })

        Row() {
          Text(`订单号：${content}`)
            .fontSize(13)
        }
      }.alignItems(HorizontalAlign.Start)

      ColumnSplit().layoutWeight(1)

      Column() {
        Text(`￥${price}`)
          .fontSize(14)
          .fontWeight(FontWeight.Bold)
      }
    }
    .padding(18)
    .borderRadius(CommonConstants.BORDER_RADIUS)
    .shadow(ShadowStyle.OUTER_DEFAULT_SM)
    .backgroundColor($r('sys.color.comp_background_primary'))
    .margin({ top: 4, bottom: 4 })
    .onClick(() => {
      itemClick()
    })
  }

  build() {
    Scroll(this.scroller) {
      Column() {
        Row() {
          Column() {
            SymbolGlyph($r('sys.symbol.circle_below_local_filled'))
              .fontColor([$r('sys.color.font_primary')])
              .fontSize(24)
          }.margin({ right: 12 })

          Column() {
            Row() {
              Text(`${this.userInfo?.schoolVo?.schoolName}`)
                .fontSize(15)
                .fontWeight(FontWeight.Bold)
            }.padding({ bottom: 8 })

            Row() {
              Text("切换地址")
                .fontSize(13)
            }
          }
          .onClick(() => {
            promptAction.showToast({
              message: "更换学校信息请联系客服！",
            })
          })
          .alignItems(HorizontalAlign.Start)

          ColumnSplit().layoutWeight(1)
          Row() {
            SymbolGlyph($r('sys.symbol.person_crop_circle_fill'))
              .fontSize(28)
              .padding({ top: 12, left: 12, bottom: 12 })
              .fontColor([$r('sys.color.font_primary')])
          }.onClick(() => {
            this.userInfoClick()
          })
        }.justifyContent(FlexAlign.SpaceBetween)
        .width("100%")

        RowSplit().height("20")

        ItemTitle({ title: "其他功能" })
        RowSplit().height("16")

        Row() {
          this.ServiceCard($r('sys.symbol.laundry'), "立即预约", "15元/件起", () => {
            pathStack.pushPathByName("Laundry", null)
          })
          RowSplit().width("20")
          this.ServiceCard($r('sys.symbol.book_pages'), "取回衣物", "扫码取件", () => {
            pathStack.pushPathByName("PickUp", null)
          })
        }

        RowSplit().height("16")
        Row() {
          Column() {
            Text("进行中订单")
              .fontWeight(FontWeight.Bold)
          }

          ColumnSplit().layoutWeight(1)
          Column() {
            Text("查看全部")
              .onClick(() => {
                pathStack.pushPathByName("OrderList", 3)
              }).padding(8).fontSize(14).fontColor($r('sys.color.brand'))
          }
        }

        RowSplit().height("8")
        Row() {
          if ((this.orderList?.length ?? 0) > 0) {
            List() {
              ForEach(this.orderList, (order: OrderVo) => {
                ListItem() {
                  this.IndexOrderItem($r('sys.symbol.laundry'), "工厂清洗中", `${order.orderNo}`, `${order.totalPrice}`,
                    () => {
                      pathStack.pushPathByName("OrderDetail", order.orderId)
                    })
                }.padding(4)
              })
            }
          } else {
            Row() {
              Text("暂无进行中的订单")
            }.width("100%")
            .height(60)
            .justifyContent(FlexAlign.Center)
          }
        }.width("100%")

        RowSplit().height("16")
        ItemTitle({ title: "服务状态" })
        RowSplit().height("16")
        Row() {
          this.StatusCard($r('sys.symbol.clock'), "营业时间", "7:00-22:00")
          RowSplit().width("20")
          this.StatusCard($r('sys.symbol.bus_doubledecker'), "营运状态", "正常营运")
        }

        RowSplit().height("72")
      }
    }
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor($r('sys.color.background_primary'))
    .padding({ left: 16, right: 16 })
  }

  onUserInfoChange(changedPropertyName: string) {
  }
}