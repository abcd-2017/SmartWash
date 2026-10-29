import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { LaundryItem } from "../network/vo/LaundryItem"
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { reservationLaundry } from "../network/api/OrderApi"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { AppButton, AppInfoDialog } from "../view/common/AppComponents"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"

@Builder
export function LaundryBuilder() {
  Laundry()
}

@Component
struct Laundry {
  @State userInfo: UserInfoVo | null = null
  @State laundryItemList: Array<LaundryItem> | null = null
  @State selectItemId: number | null = null
  @State totalPrice: number | null = 0
  // 页面首屏加载态：请求期间全屏 Loading（对齐安卓 LoadingState）
  @State pageLoading: boolean = true
  // 预约请求进行中标记：配合 AppButton loading 展示并拦截连点。
  // 后端预约接口无幂等（评审报告 P0），前端防重是唯一防线
  @State reserving: boolean = false
  // 未选套餐提示弹窗（对齐安卓 AppInfoDialog，替代 toast 形态）
  private selectDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({ message: "请选择套餐" }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })
  // 更换学校提示弹窗（对齐安卓「更换学校信息请联系客服！」）
  private changeSchoolDialogController: CustomDialogController = new CustomDialogController({
    builder: AppInfoDialog({ message: "更换学校信息请联系客服！" }),
    autoCancel: true,
    alignment: DialogAlignment.Center,
    customStyle: true
  })

  async aboutToAppear() {
    // ArkTS 禁止解构声明（arkts-no-destruct-decls），改为先并发发起、再逐个 await（保持并发语义）
    const resTask = getUserInfo()
    const itemResTask = getLaundryItems()
    let res = await resTask
    let itemRes = await itemResTask
    if (res.code === HttpStatusCode.Ok) {
      this.userInfo = res.data
    } else {
      showToastSafety(res.message, "用户信息加载失败")
    }
    if (itemRes.code === HttpStatusCode.Ok) {
      this.laundryItemList = itemRes.data
    } else {
      showToastSafety(itemRes.message, "套餐加载失败")
    }
    this.pageLoading = false
  }

  /**
   * 提交预约（资金链路防重）：
   * reserving 标记 + AppButton loading 双保险，请求期间按钮不可点，连点只发一次请求。
   */
  async submitReservation() {
    if (this.selectItemId === null) {
      this.selectDialogController.open()
      return
    }
    if (this.reserving) {
      return
    }
    this.reserving = true
    try {
      let res = await reservationLaundry({
        itemsId: this.selectItemId,
        totalPrice: this.totalPrice
      })
      if (res.code === HttpStatusCode.Ok) {
        if (res.data !== null) {
          pathStack.pop()
          // 统一传 number 类型 orderId（评审 #6：原模板字符串导致 Payment 收到字符串）
          pathStack.pushPathByName(RouteNames.PAYMENT, res.data)
        } else {
          showToastSafety(res.message, "下单失败，请稍后重试")
        }
      } else {
        showToastSafety(res.message, "下单失败，请稍后重试")
      }
    } finally {
      this.reserving = false
    }
  }

  @Builder
  LaundryTypeItem(item: LaundryItem) {
    // @Builder 内仅允许 UI 语法，选中态直接内联计算（原 let isSelected 不合法）
    // 选中态对齐安卓：primaryLight 底 + 1.5 主色描边，属性动画平滑过渡（LaundryPage.kt:286-310）
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
        Text(`￥${item.basePrice?.toFixed(2)}`)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(this.selectItemId == item.itemId ? DesignColor.PRIMARY : DesignColor.TEXT_PRIMARY)
      }.margin({ left: 18 })
    }
    .backgroundColor(this.selectItemId == item.itemId ? DesignColor.PRIMARY_LIGHT : DesignColor.SURFACE)
    .padding(DesignDimens.CARD_PADDING)
    // 卡片唯一画法（规范 §3.1）：1px 描边 + level1 卡片阴影；
    // 描边宽度不再随选中变化（对齐安卓 v0.4），选中强调由 primaryLight 底 + primary 描边承担
    .border({
      width: 1,
      color: this.selectItemId == item.itemId ? DesignColor.PRIMARY : DesignColor.OUTLINE,
      radius: DesignDimens.CARD_RADIUS
    })
    .borderRadius(DesignDimens.CARD_RADIUS)
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: 'rgba(20,40,30,0.05)',
      offsetX: 0,
      offsetY: 2
    })
    .animation({ duration: 200, curve: Curve.EaseOut })
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
                  pathStack.pop()
                })

              Text("预约寄存")
                .fontSize(DesignFontSize.ROOT_TITLE)
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
              .layoutWeight(1)

              // 「更换」动作：弹「联系客服」提示弹窗（对齐安卓 LaundryPage.kt:119-124）
              Column() {
                Text("更换")
                  .fontSize(DesignFontSize.BODY)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(DesignColor.PRIMARY)
                  .padding({ left: 8, right: 8, top: 4, bottom: 4 })
              }
              .onClick(() => {
                this.changeSchoolDialogController.open()
              })
            }
            .borderRadius(DesignDimens.CARD_RADIUS)
            .backgroundColor(DesignColor.SURFACE)
            // 卡片唯一画法（规范 §3.1）：1px OUTLINE 描边 + level1 卡片阴影
            .border({ width: 1, color: DesignColor.OUTLINE })
            .shadow({
              radius: DesignElevation.LEVEL_1,
              color: 'rgba(20,40,30,0.05)',
              offsetX: 0,
              offsetY: 2
            })
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

            // 套餐列表（补 ForEach key：itemId，对齐安卓 LazyColumn key）
            Row() {
              List() {
                ForEach(this.laundryItemList, (item: LaundryItem) => {
                  ListItem() {
                    this.LaundryTypeItem(item)
                  }
                }, (item: LaundryItem) => item.itemId?.toString())
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
              Text(`￥${this.totalPrice?.toFixed(2)}`)
                .fontSize(DesignFontSize.AMOUNT_MEDIUM)
                .fontWeight(FontWeight.Bold)
                .fontColor(DesignColor.PRIMARY)
            }
          }.alignItems(HorizontalAlign.Start)

          ColumnSplit().layoutWeight(1)

          Column() {
            // AppButton 自带 loading 防重（请求中禁点），配合 reserving 拦截连点
            AppButton({
              text: "确定预约",
              loading: this.reserving,
              click: () => {
                this.submitReservation()
              }
            })
          }
          .width(160)
        }
        .backgroundColor(DesignColor.SURFACE)
        .border({ width: { top: 1 }, color: DesignColor.OUTLINE })
        .width("100%")
        .height(72)
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .zIndex(1)
        .align(Alignment.Bottom)

        // 首屏加载态（对齐安卓全屏 LoadingState）
        if (this.pageLoading) {
          Column() {
            LoadingProgress()
              .width(40)
              .height(40)
              .color(DesignColor.PRIMARY)
          }
          .width("100%")
          .height("100%")
          .justifyContent(FlexAlign.Center)
          .backgroundColor(DesignColor.BACKGROUND)
          .zIndex(2)
        }
      }
    }
  }
}
