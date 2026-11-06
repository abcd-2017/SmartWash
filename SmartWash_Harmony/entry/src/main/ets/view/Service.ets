import { AppButton, AppButtonVariant, EmptyState, LoadingState } from './common/AppComponents'
import { HttpStatusCode } from "@ohos/axios"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { getLaundryItems } from "../network/api/LaundryItemsApi"
import { LaundryItem } from "../network/vo/LaundryItem"
import { safeTopVp } from "../utils/SafeArea"
import { RouteNames } from "../constant/RouteNames"
import { pathStack } from "../utils/PathStackUtil"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import vibrator from '@ohos.vibrator'

/**
 * 服务行图标容器配色对（对齐安卓 ServicePage.kt:300-317 serviceTileColors）。
 * 四色语义：干→蓝 / 精·护理·奢→紫 / 熨→橙 / 洗·标准→绿。
 * ⚠️ DESIGN.md「服务五色」把干洗定为紫 #7C3AED，与安卓代码「干→蓝 #4A67C9」冲突，
 * 按任务规则以安卓代码为准（裁决见 docs/design/harmony-align/refix-02-service-laundry.md）。
 */
interface ServiceTileColors {
  container: number
  foreground: number
}

@Component
export struct Service {
  @State laundryItem: Array<LaundryItem> | null = null
  @State isLoading: boolean = true
  @State loadFailed: boolean = false
  // 行按压态（I2，对齐安卓 pressable scale 0.97）
  @State pressedItemId: number | null = null
  private scroller: Scroller = new Scroller()

  async aboutToAppear() {
    await this.fetchItems()
  }

  /** 拉取套餐列表：失败时 toast 提示并置失败态（无缓存层，失败不得静默空白） */
  async fetchItems() {
    this.isLoading = true
    this.loadFailed = false
    try {
      let res = await getLaundryItems()
      if (res.code === HttpStatusCode.Ok) {
        this.laundryItem = res.data
      } else {
        showToastSafety(res.message, "套餐加载失败")
        this.loadFailed = true
      }
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并进失败态，避免 isLoading 卡死永久骨架
      showToastSafety("网络异常，请稍后重试")
      this.loadFailed = true
    } finally {
      this.isLoading = false
    }
  }

  /**
   * 根据套餐名称匹配图标符号资源
   * 对齐安卓 serviceIcon（ServicePage.kt:281-290）：关键字映射 + 兜底按 itemId 取模
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
   * 图标容器四色配色（对齐安卓 serviceTileColors，ServicePage.kt:300-317）
   * 分支顺序与 [serviceIcon] 保持同步；兜底色序与图标兜底序列（洗绿/干蓝/精紫/熨橙）逐位配对。
   * 绿色支取 primaryLight 底 + primaryDark 前景（画板实测绿 tile 配对，不用 ICON_*_GREEN）。
   */
  private serviceTileColors(item: LaundryItem): ServiceTileColors {
    const name = item.itemName ?? ""
    if (name.includes("干")) {
      return { container: DesignColor.ICON_CONTAINER_BLUE, foreground: DesignColor.ICON_FOREGROUND_BLUE }
    }
    if (name.includes("精") || name.includes("护理") || name.includes("奢")) {
      return { container: DesignColor.ICON_CONTAINER_PURPLE, foreground: DesignColor.ICON_FOREGROUND_PURPLE }
    }
    if (name.includes("熨")) {
      return { container: DesignColor.ICON_CONTAINER_ORANGE, foreground: DesignColor.ICON_FOREGROUND_ORANGE }
    }
    if (name.includes("洗") || name.includes("标准")) {
      return { container: DesignColor.PRIMARY_LIGHT, foreground: DesignColor.PRIMARY_DARK }
    }
    switch ((item.itemId ?? 0) % 4) {
      case 1:
        return { container: DesignColor.ICON_CONTAINER_BLUE, foreground: DesignColor.ICON_FOREGROUND_BLUE }
      case 2:
        return { container: DesignColor.ICON_CONTAINER_PURPLE, foreground: DesignColor.ICON_FOREGROUND_PURPLE }
      case 3:
        return { container: DesignColor.ICON_CONTAINER_ORANGE, foreground: DesignColor.ICON_FOREGROUND_ORANGE }
      default:
        return { container: DesignColor.PRIMARY_LIGHT, foreground: DesignColor.PRIMARY_DARK }
    }
  }

  /** 轻触反馈（I1，对齐安卓 HapticEffect.LIGHT）：设备不支持时静默忽略 */
  private hapticLight(): void {
    try {
      vibrator.startVibration({ type: 'time', duration: 10 }, { id: 0, usage: 'unknown' })
    } catch (err) {
      // 不支持触感（模拟器 / 部分设备）时忽略
    }
  }

  /**
   * 服务行：36vp 四色 IconBox + 名称/描述 + 两位小数价格 + chevron（对齐安卓 ServiceItemRow）
   * 行点击携该套餐 id 进预约页预选（F1，对齐安卓 ServicePage.kt:117-124）
   */
  @Builder
  ServiceItemBuilder(item: LaundryItem) {
    Row() {
      // 左侧图标容器（IconBox 规格 36/20，对齐安卓 icon.sm 档，V4）
      Column() {
        SymbolGlyph(this.serviceIcon(item))
          .fontSize(20)
          .fontColor([this.serviceTileColors(item).foreground])
      }
      .width(36)
      .height(36)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(this.serviceTileColors(item).container)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      // 中间文字区域
      Column() {
        Text(item.itemName)
          .fontSize(DesignFontSize.CARD_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)

        // 描述不限行（对齐安卓 ServiceItemRow：可换行，无 maxLines/Ellipsis 约束）
        Text(item.description)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .margin({ top: 4 })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })

      // 右侧价格 + chevron（V2/V3：价格恒 primaryDark 600 + tnum，行尾 chevron 20 textTertiary）
      Column() {
        Text(`¥${item.basePrice?.toFixed(2)}`)
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.PRIMARY_DARK)
          .fontFeature(DesignFontFeature.TNUM)

        SymbolGlyph($r('sys.symbol.chevron_right'))
          .fontSize(20)
          .fontColor([DesignColor.TEXT_TERTIARY])
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
    // I2 按压反馈：scale 0.97（对齐安卓 pressable；stateEffect 非通用属性，见 DivComponents.ets 注释）
    .scale({
      x: this.pressedItemId === item.itemId ? 0.97 : 1,
      y: this.pressedItemId === item.itemId ? 0.97 : 1
    })
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedItemId = item.itemId
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedItemId = null
      }
    })
    .onClick(() => {
      // I1 点击因果时刻补触觉，随后携套餐 id 跳预约页（对齐安卓先触感后导航的顺序）
      this.hapticLight()
      pathStack.pushPathByName(RouteNames.LAUNDRY, item.itemId ?? -1)
    })
  }

  @Builder
  ServiceListCard() {
    // 单张卡片包裹全部套餐行，行间 1dp 发丝线分隔（对齐安卓 ServiceListCard 的 1dp 线）
    Column() {
      if (this.laundryItem !== null) {
        ForEach(this.laundryItem, (item: LaundryItem, index: number) => {
          this.ServiceItemBuilder(item)
          if (index < (this.laundryItem?.length ?? 0) - 1) {
            Divider()
              .strokeWidth(1)
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
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
  }

  build() {
    Stack() {
      // 内容层常驻：加载期透明 + 退出命中测试，加载完成 180ms 渐隐揭示。
      // S1 裁决：ArkUI 无 Crossfade 等价组件，以内容层 opacity 渐隐近似
      //（骨架底色与页面底同色，其卸载瞬间不可见，切点被抹平）
      Column() {
        Scroll(this.scroller) {
          Column() {
            // 页头（对齐安卓 ServicePage.kt:101-113 —— 洗衣服务 SectionTitle 18/600，无副标题）
            Text("洗衣服务")
              .fontSize(DesignFontSize.SECTION_TITLE)
              .fontWeight(DesignFontWeight.SEMI_BOLD)
              .fontColor(DesignColor.TEXT_PRIMARY)
              .width("100%")
              .margin({ top: 8 })

            // 服务项目列表（单卡包裹）
            Column() {
              this.ServiceListCard()
            }
            .width("100%")
            .margin({ top: 12 })

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
        // 沉浸式状态栏避让 + 内容不足一屏顶对齐（问题11 布局部分：原默认居中导致内容悬在中部）
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: safeTopVp() })
        .align(Alignment.Top)
      }
      .width("100%")
      .height("100%")
      .opacity(this.isLoading ? 0 : 1)
      .animation({ duration: 180, curve: Curve.EaseInOut })
      .enabled(!this.isLoading)

      // 加载态（首屏请求期间全屏 Loading）
      if (this.isLoading) {
        // 加载态（对齐安卓 LoadingState：骨架屏，§3.6 禁转圈）
        LoadingState()
          .width("100%")
          .height("100%")
          .backgroundColor(DesignColor.BACKGROUND)
      }

      // 失败态（无数据且加载失败）：EmptyState + 重试按钮，不留空白页（F3/T3）
      if (!this.isLoading && this.loadFailed && (this.laundryItem === null || this.laundryItem.length === 0)) {
        Column() {
          EmptyState({
            message: "套餐加载失败",
            action: () => {
              AppButton({
                text: "重试",
                variant: AppButtonVariant.SECONDARY,
                click: () => {
                  this.fetchItems()
                }
              })
              .margin({ top: DesignDimens.SPACE_MD })
            }
          })
        }
        .width("100%")
        .height("100%")
        .backgroundColor(DesignColor.BACKGROUND)
        .justifyContent(FlexAlign.Center)
      }
    }
    .width("100%")
    .height("100%")
    .backgroundColor(DesignColor.BACKGROUND)
  }
}
