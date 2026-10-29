import { HttpStatusCode } from "@ohos/axios"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontWeight } from "../constant/DesignSystem"
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { LaundryItem } from "../network/vo/LaundryItem"
import { RouteNames } from "../constant/RouteNames"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"

@Component
export struct Service {
  @State laundryItem: Array<LaundryItem> | null = null
  @State isLoading: boolean = true
  @State loadFailed: boolean = false
  private scroller: Scroller = new Scroller()

  async aboutToAppear() {
    await this.fetchItems()
  }

  /** 拉取套餐列表：失败时 toast 提示并置失败态（无缓存层，失败不得静默空白） */
  async fetchItems() {
    this.isLoading = true
    this.loadFailed = false
    let res = await getLaundryItems()
    if (res.code === HttpStatusCode.Ok) {
      this.laundryItem = res.data
    } else {
      showToastSafety(res.message, "套餐加载失败")
      this.loadFailed = true
    }
    this.isLoading = false
  }

  /**
   * 根据套餐名称匹配图标符号资源
   * 对齐安卓 serviceIcon（ServicePage.kt:247-256）：关键字映射 + 兜底按 itemId 取模
   */
  private serviceIcon(item: LaundryItem): Resource {
    const name = item.itemName ?? ""
    const iconList: Array<Resource> = [
      $r('sys.symbol.laundry'),
      $r('sys.symbol.wind'),
      $r('sys.symbol.hanger_and_towels'),
      $r('sys.symbol.heat_waves')
    ]
    if (name.includes("干")) {
      // 干洗
      return $r('sys.symbol.wind')
    } else if (name.includes("精") || name.includes("护理") || name.includes("奢")) {
      // 精洗/护理/奢护（sys.symbol.hanger 不存在，SDK 5.0.5 无该符号，就近换用衣架+毛巾符号）
      return $r('sys.symbol.hanger_and_towels')
    } else if (name.includes("熨")) {
      // 熨烫（sys.symbol.steam 不存在，SDK 5.0.5 无该符号，就近换用热浪符号）
      return $r('sys.symbol.heat_waves')
    } else if (name.includes("洗") || name.includes("标准")) {
      // 标准洗衣
      return $r('sys.symbol.laundry')
    }
    return iconList[(item.itemId ?? 0) % iconList.length]
  }

  /**
   * 服务行：36vp IconBox + 名称/描述 + 两位小数价格 + 「预约 ›」动作（对齐安卓 ServiceItemRow）
   * 行点击进预约页（安卓 ServicePage.kt:96,199）
   */
  @Builder
  ServiceItemBuilder(item: LaundryItem) {
    Row() {
      // 左侧图标容器（IconBox 规格 36/18，规范 §3.2）
      Column() {
        SymbolGlyph(this.serviceIcon(item))
          .fontSize(18)
          .fontColor([DesignColor.ICON_FOREGROUND_GREEN])
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(DesignColor.ICON_CONTAINER_GREEN)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      // 中间文字区域
      Column() {
        Text(item.itemName)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)

        Text(item.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 4 })
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .maxLines(1)
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })

      // 右侧价格 + 预约动作
      Column() {
        Text(`¥${item.basePrice?.toFixed(2)}`)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.BOLD)
          .fontColor(DesignColor.PRIMARY)

        Text("预约 ›")
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.PRIMARY)
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.End)
    }
    .width("100%")
    // 对齐安卓 ServicePage.ServiceRow：行体 74 = 上 15 + 内容 52 + 下 7，图标盒/名称/价格同顶
    .alignItems(VerticalAlign.Top)
    .padding({
      left: DesignDimens.CARD_PADDING,
      right: DesignDimens.CARD_PADDING,
      top: 15,
      bottom: 7
    })
    .onClick(() => {
      pathStack.pushPathByName(RouteNames.LAUNDRY, null)
    })
  }

  @Builder
  ServiceListCard() {
    // 单张卡片包裹全部套餐行，行间 0.5 发丝线分隔（对齐安卓 ServiceListCard，规范 §3.4）
    Column() {
      if (this.laundryItem !== null) {
        ForEach(this.laundryItem, (item: LaundryItem, index: number) => {
          this.ServiceItemBuilder(item)
          if (index < (this.laundryItem?.length ?? 0) - 1) {
            Divider()
              .strokeWidth(0.5)
              .color(DesignColor.HAIRLINE)
              .margin({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
          }
        }, (item: LaundryItem) => item.itemId?.toString())
      }
    }
    .width("100%")
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // 规范 §2.4 卡片：1px OUTLINE 描边 + 0 2px 12px rgba(20,40,30,.05) 阴影（对齐安卓 v0.4）
    .border({ width: 1, color: DesignColor.OUTLINE })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: 'rgba(20,40,30,0.05)',
      offsetX: 0,
      offsetY: 2
    })
  }

  build() {
    Stack() {
      Scroll(this.scroller) {
        Column() {
          // 页面标题
          Column() {
            Text("洗衣服务")
              .fontSize(DesignFontSize.ROOT_TITLE)
              .fontWeight(DesignFontWeight.BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)

            Text("专业洗护，安心交付")
              .fontSize(14)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .margin({ top: 4 })
          }
          .alignItems(HorizontalAlign.Start)
          .width("100%")
          .margin({ top: 16 })

          // 服务项目列表（单卡包裹）
          Column() {
            this.ServiceListCard()
          }
          .width("100%")
          .margin({ top: 20 })

          // 底部服务提示文案（对齐安卓 service_tips）
          Text("服务时间 07:00 – 22:00 · 节假日正常营业")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .textAlign(TextAlign.Center)
            .width("100%")
            .margin({ top: 24 })

          // 底部间距
          Column()
            .height(24)
        }
      }
      .scrollable(ScrollDirection.Vertical)
      .scrollBar(BarState.Off)
      .width("100%")
      .height("100%")
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })

      // 加载态（首屏请求期间全屏 Loading）
      if (this.isLoading) {
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
      }

      // 失败态（无数据且加载失败）：提供重试入口，不留空白页
      if (!this.isLoading && this.loadFailed && (this.laundryItem === null || this.laundryItem.length === 0)) {
        Column() {
          Text("套餐加载失败")
            .fontSize(DesignFontSize.BODY)
            .fontColor(DesignColor.TEXT_SECONDARY)
            .margin({ bottom: 12 })

          Text("重新加载")
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.PRIMARY)
            .onClick(() => {
              this.fetchItems()
            })
        }
        .width("100%")
        .height("100%")
        .justifyContent(FlexAlign.Center)
      }
    }
    .width("100%")
    .height("100%")
    .backgroundColor(DesignColor.BACKGROUND)
  }
}
