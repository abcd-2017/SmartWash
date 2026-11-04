import { HttpStatusCode } from "@ohos/axios"
import { RouteNames } from "../constant/RouteNames"
import { getUserInfo } from "../network/api/UserApi"
import { UserInfoVo } from "../network/vo/UserInfoVo"
import { pathStack } from "../utils/PathStackUtil"
import { OrderVo } from "../network/vo/OrderVo"
import { getWashingOrder } from "../network/api/OrderApi"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { getOrderStatusDescription } from "../constant/OrderStatus"
import { OrderStatusEnum } from "../constant/OrderStatus"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { IconBox } from "./common/AppComponents"
import { HeroCard } from "./HeroCard"
import { BizCode } from "../network/HttpUtil"
import { safeTopVp } from "../utils/SafeArea"

// 连续轮询失败达到该次数才提示一次（评审 #4：避免每 5s 弹 toast）
const POLLING_FAILURE_THRESHOLD: number = 3

// 稳定空数组：避免 `?? []` 每次 build 生成新引用导致子组件 @Monitor 反复触发
const EMPTY_ORDER_LIST: Array<OrderVo> = []

/**
 * 首页（对齐安卓 IndexPage.kt，PR #47 后新形态）——结构自上而下：
 * Hero 状态卡（品牌渐变，唯一锚点）→ 账户数据卡（白卡）→ 洗衣服务网格（4 入口）
 * → 进行中订单（单卡多行 + 发丝线）→ 服务须知。
 *
 * 轮询为鸿蒙独有（安卓落 tab 刷新）：5s 拉取进行中订单，失败静默计数，连续失败达阈值才提示一次。
 */
@ComponentV2
export struct IndexPage {
  private scroller = new Scroller()
  @Local userInfo: UserInfoVo | null = null
  @Local orderList: Array<OrderVo> | null = []
  @Param isActive: boolean = true
  // 宿主可见性刷新信号（问题9）：Home NavDestination.onShown 递增，充值等子页返回时拉新余额
  @Param refreshTick: number = 0
  // 余额大字按可用宽度分档（对齐安卓 BoxWithConstraints：36 / 28 / 22 阶梯内降级）
  @Local balanceFontSize: number = DesignFontSize.DATA_LARGE
  private pollingTimer: number = -1
  // 连续轮询失败计数：失败静默，连续失败达到阈值才提示一次；成功后清零
  private pollingFailureCount: number = 0

  aboutToAppear() {
    // 生命周期低频动作：debug 留痕，便于排查轮询定时器启停与泄漏
    logger.debug('indexPage', '首页挂载，拉取用户信息')
    this.refreshUserInfo()
    if (this.isActive) {
      logger.debug('indexPage', 'Tab 处于激活态，启动订单轮询')
      this.startPolling()
    }
  }

  aboutToDisappear() {
    logger.debug('indexPage', '首页销毁，停止轮询')
    this.stopPolling()
  }

  // @ComponentV2 不存在 onDidUpdate 生命周期，改用 @Monitor 监听 isActive（Tab 切换）启停轮询（评审 #4）
  @Monitor('isActive')
  onIsActiveChange() {
    // Tab 切换为高频路径：仅 debug（级别约定禁止高频路径 info/warn）
    if (this.isActive) {
      // 重新激活时立即拉取一次（对齐安卓 LaunchedEffect(isActive) → getInfoData），再启动 5s 轮询
      logger.debug('indexPage', 'Tab 重新激活，立即刷新并恢复轮询')
      this.refreshUserInfo()
      this.getOrderList()
      this.startPolling()
    } else {
      logger.debug('indexPage', 'Tab 切出，停止轮询')
      this.stopPolling()
    }
  }

  // 充值/订单变动返回首页时刷新账户卡（问题9）
  @Monitor('refreshTick')
  onRefreshTickChange() {
    if (this.refreshTick > 0) {
      this.refreshUserInfo()
    }
  }

  private startPolling() {
    // 防御：避免重复 start 造成多个定时器并存
    if (this.pollingTimer !== -1) {
      return
    }
    this.pollingTimer = setInterval(() => {
      this.getOrderList()
    }, 5000)
  }

  private stopPolling() {
    if (this.pollingTimer !== -1) {
      clearInterval(this.pollingTimer)
      this.pollingTimer = -1
    }
  }

  async refreshUserInfo() {
    try {
      let res = await getUserInfo()
      if (res.code === HttpStatusCode.Ok) {
        this.userInfo = res.data
      } else if (res.code !== BizCode.UNAUTHORIZED) {
        // 401 已由 Axios 拦截器统一处理（清 token → 清登录态 → replace 登录页），此处不重复弹 toast
        // 业务失败 warn 留痕（低频：仅挂载/重进 Tab 时调用，非 5s 轮询路径）
        logger.warn('indexPage', `用户信息加载失败 code=${res.code} message=${res.message ?? ""}`)
        showToastSafety(res.message, "用户信息加载失败")
      }
    } catch (e) {
      // 401/网络异常均由 Axios 拦截器统一提示并处理，页面不再重复弹 toast
    }
  }

  async getOrderList() {
    try {
      let res = await getWashingOrder()
      if (res.code === HttpStatusCode.Ok) {
        this.pollingFailureCount = 0
        this.orderList = res.data
      } else if (res.code !== BizCode.UNAUTHORIZED) {
        // 失败静默计数，连续失败达到阈值才提示一次（评审 #4）
        this.handlePollingFailure(res.message)
      }
    } catch (e) {
      // 轮询请求异常（网络断开/401 跳转等）同样静默计数，不每 5s 弹 toast
      this.handlePollingFailure(null)
    }
  }

  /** 轮询失败静默计数：连续失败达到阈值时提示一次 */
  private handlePollingFailure(message: string | null | undefined): void {
    this.pollingFailureCount++
    // 轮询为 5s 高频路径：失败仅 debug 留痕（级别约定禁止轮询路径 info/warn）
    logger.debug('indexPage', `订单轮询失败 count=${this.pollingFailureCount}`)
    if (this.pollingFailureCount === POLLING_FAILURE_THRESHOLD) {
      // 达到阈值提示用户属低频降级事件：warn 留痕
      logger.warn('indexPage', `订单轮询连续失败 ${POLLING_FAILURE_THRESHOLD} 次，已提示用户`)
      showToastSafety(message, "订单状态刷新失败")
    }
  }

  /**
   * 账户数据卡 —— 白底 + 1px 描边 + 轻阴影（对齐安卓 AccountDataCard）。
   * 余额是主角（36sp tnum 大字，可点进充值），优惠券/累计订单是小字半格 + 1×40 发丝竖线。
   */
  @Builder
  AccountDataCard() {
    Column() {
      Column() {
        // 余额（可点进充值）—— 按可用宽度分档缩字号，窄屏不折行不溢出
        Column() {
          Text("账户余额")
            .fontSize(DesignFontSize.CAPTION)
            .fontColor(DesignColor.TEXT_SECONDARY)

          Text(`¥${(this.userInfo?.balance ?? 0).toFixed(2)}`)
            .fontSize(this.balanceFontSize)
            .fontColor(DesignColor.PRIMARY_DARK)
            .fontFeature(DesignFontFeature.TNUM)
            .maxLines(1)
            .margin({ top: 4 })
        }
        .alignItems(HorizontalAlign.Start)
        .width('100%')
        .padding({ top: 16, left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
        .onClick(() => {
          pathStack.pushPathByName(RouteNames.RECHARGE, null)
        })
        .onAreaChange((oldValue: Area, newValue: Area) => {
          const width: number = typeof newValue.width === 'number' ? newValue.width : 0
          if (width >= 160) {
            this.balanceFontSize = DesignFontSize.DATA_LARGE
          } else if (width >= 130) {
            this.balanceFontSize = DesignFontSize.HEADLINE
          } else {
            this.balanceFontSize = DesignFontSize.STATUS_LARGE
          }
        })

        // 券 / 累计订单半格，中间 1×40 发丝竖线
        Row() {
          this.AccountStatCell(`${this.userInfo?.couponCount ?? 0}`, "可用优惠券")
          Row()
            .width(1)
            .height(40)
            .backgroundColor(DesignColor.HAIRLINE)
          this.AccountStatCell(`${this.userInfo?.orderCount ?? 0}`, "累计订单")
        }
        .width('100%')
        .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
        .margin({ top: DesignDimens.SPACE_XS })

        // 卡片底部呼吸位（对齐安卓 Spacer 16）
        Row()
          .height(DesignDimens.SPACE_MD)
      }
      .width('100%')
      .backgroundColor(DesignColor.SURFACE)
      .borderRadius(DesignDimens.CARD_RADIUS)
      .border({ width: 1, color: DesignColor.OUTLINE })
      .shadow({
        radius: DesignElevation.LEVEL_1,
        color: DesignColor.SHADOW_COLOR_CARD,
        offsetX: 0,
        offsetY: 2
      })
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, top: DesignDimens.SPACE_MD })
  }

  /** 账户卡底部半格：数值 18/600 tnum + 标签 12/400，水平居中 */
  @Builder
  AccountStatCell(value: string, label: string) {
    Column() {
      Text(value)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.TEXT_PRIMARY)
        .fontFeature(DesignFontFeature.TNUM)
        .maxLines(1)

      Text(label)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .maxLines(1)
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Center)
  }

  /**
   * 服务网格 —— 4 入口（对齐安卓 ServiceGrid）：预约洗衣 / 取件 / 充值 / 优惠券。
   * 图标进六色浅底容器（IconBox 44/24），标签 12/400 textSecondary，行距 12。
   */
  @Builder
  ServiceGrid() {
    Row({ space: DesignDimens.SPACE_SM }) {
      // 预约洗衣 = 绿（对齐安卓 IndexPage.kt:569-576）
      this.ServiceEntry($r('sys.symbol.laundry'), "预约洗衣",
        DesignColor.ICON_CONTAINER_GREEN, DesignColor.ICON_FOREGROUND_GREEN, () => {
          pathStack.pushPathByName(RouteNames.LAUNDRY, null)
        })
      // 取件 = 橙（对齐安卓 IndexPage.kt:577-584）
      this.ServiceEntry($r('sys.symbol.book_pages'), "取件",
        DesignColor.ICON_CONTAINER_ORANGE, DesignColor.ICON_FOREGROUND_ORANGE, () => {
          pathStack.pushPathByName(RouteNames.PICK_UP, null)
        })
      // 充值 = 绿（对齐安卓 IndexPage.kt:585-593，与预约洗衣同 primaryContainer 底）
      this.ServiceEntry($r('sys.symbol.creditcard'), "充值",
        DesignColor.ICON_CONTAINER_GREEN, DesignColor.ICON_FOREGROUND_GREEN, () => {
          pathStack.pushPathByName(RouteNames.RECHARGE, null)
        })
      // 优惠券 = 蓝（对齐安卓 IndexPage.kt:594-601）
      this.ServiceEntry($r('sys.symbol.label'), "优惠券",
        DesignColor.ICON_CONTAINER_BLUE, DesignColor.ICON_FOREGROUND_BLUE, () => {
          pathStack.pushPathByName(RouteNames.COUPON, null)
        })
    }
    .width('100%')
    .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
    .margin({ top: DesignDimens.SPACE_SM })
  }

  /** 服务入口 —— IconBox 44/24 + 标签 12/400 textSecondary，居中（对齐安卓 ServiceEntry） */
  @Builder
  ServiceEntry(icon: Resource, title: string, containerColor: number, iconColor: number, itemClick: () => void) {
    Column() {
      IconBox({
        icon: icon,
        containerColor: containerColor,
        iconColor: iconColor
      })

      Text(title)
        .fontSize(DesignFontSize.CAPTION)
        .fontColor(DesignColor.TEXT_SECONDARY)
        .margin({ top: DesignDimens.SPACE_XS })
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Center)
    .onClick(() => {
      itemClick()
    })
  }

  /**
   * 进行中订单卡 —— 白底 + 1px 描边 + 轻阴影，单卡多行 + 行内发丝线（对齐安卓 OrderListCard）。
   * 行高 56（36 图标 + 上下各 10），无进度条（进度由 Hero 卡承载）。
   */
  @Builder
  OrderListCard() {
    Column() {
      ForEach(this.orderList ?? [], (order: OrderVo, index: number) => {
        this.OrderListItem(order)
        if (index < (this.orderList?.length ?? 0) - 1) {
          // 行内发丝线只在本卡片内部（左右缩进 20，对齐安卓 padding horizontal 20）
          Row() {
            Row()
              .width('100%')
              .height(1)
              .backgroundColor(DesignColor.HAIRLINE)
          }
          .width('100%')
          .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING })
        }
      })
    }
    .width('100%')
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    .border({ width: 1, color: DesignColor.OUTLINE })
    .shadow({
      radius: DesignElevation.LEVEL_1,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: 2
    })
  }

  /** 订单行 —— 36dp 图标容器 + 状态/订单号 + 等宽价格（对齐安卓 OrderListItem） */
  @Builder
  OrderListItem(order: OrderVo) {
    Row() {
      // 图标容器色按状态语义：清洗中 → water 水蓝，其余 → 橙（对齐安卓 IndexPage.kt:689-704）
      IconBox({
        icon: $r('sys.symbol.laundry'),
        sizeValue: 36,
        iconSize: 20,
        containerColor: order.status === OrderStatusEnum.WASHING ?
          DesignColor.ICON_CONTAINER_BLUE : DesignColor.ICON_CONTAINER_ORANGE,
        iconColor: order.status === OrderStatusEnum.WASHING ?
          DesignColor.WATER : DesignColor.ICON_FOREGROUND_ORANGE
      })
      .margin({ right: DesignDimens.SPACE_SM })

      Column() {
        Text(getOrderStatusDescription(order.status))
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)

        Text(`订单号: ${order.orderNo ?? ""}`)
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .fontFeature(DesignFontFeature.TNUM)
          .maxLines(1)
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Text(`¥${(order.payPrice ?? 0).toFixed(2)}`)
        .fontSize(DesignFontSize.AMOUNT_MEDIUM)
        .fontWeight(DesignFontWeight.SEMI_BOLD)
        .fontColor(DesignColor.PRIMARY_DARK)
        .fontFeature(DesignFontFeature.TNUM)
    }
    .width('100%')
    .padding({ left: DesignDimens.CARD_PADDING, right: DesignDimens.CARD_PADDING, top: 10, bottom: 10 })
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      pathStack.pushPathByName(RouteNames.ORDER_DETAIL, order.orderId)
    })
  }

  /**
   * 服务须知 —— 居中纯文字（对齐安卓 ServiceTips，D-I9）
   */
  @Builder
  ServiceTips() {
    Text("服务时间 07:00 – 22:00 · 节假日正常营业")
      .fontSize(DesignFontSize.CAPTION)
      .fontColor(DesignColor.TEXT_SECONDARY)
      .width('100%')
      .textAlign(TextAlign.Center)
      .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
  }

  build() {
    Scroll(this.scroller) {
      Column() {
        // 1. Hero 状态卡（品牌渐变，全页唯一视觉锚点）
        HeroCard({
          orderList: this.orderList ?? EMPTY_ORDER_LIST,
          onOrderClick: (orderId: number) => {
            pathStack.pushPathByName(RouteNames.ORDER_DETAIL, orderId)
          },
          onBookingClick: () => {
            pathStack.pushPathByName(RouteNames.LAUNDRY, null)
          }
        })

        // 2. 账户数据卡（白卡）
        this.AccountDataCard()

        // 3. 洗衣服务 + 服务网格
        Text("洗衣服务")
          .fontSize(DesignFontSize.SECTION_TITLE)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .width('100%')
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
          .margin({ top: DesignDimens.SPACE_MD })

        this.ServiceGrid()

        // 4. 进行中订单（标题 + 查看全部）
        Row() {
          Text("进行中订单")
            .fontSize(DesignFontSize.SECTION_TITLE)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.TEXT_PRIMARY)

          Blank()

          // 「查看全部 ›」16/600 primaryDark + 48dp 热区（对齐安卓 IndexPage.kt:197-211）
          Text("查看全部 ›")
            .fontSize(DesignFontSize.BODY)
            .fontWeight(DesignFontWeight.SEMI_BOLD)
            .fontColor(DesignColor.PRIMARY_DARK)
            .padding({ left: DesignDimens.SPACE_SM, top: 16, bottom: 16 })
            .onClick(() => {
              pathStack.pushPathByName(RouteNames.ORDER_LIST, 3)
            })
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .margin({ top: DesignDimens.SPACE_XL })
        .alignItems(VerticalAlign.Center)

        // 订单列表 ↔ 空态
        Column() {
          if ((this.orderList?.length ?? 0) > 0) {
            this.OrderListCard()
          } else {
            Text("暂无进行中的订单")
              .fontSize(DesignFontSize.BODY)
              .fontColor(DesignColor.TEXT_SECONDARY)
              .padding({ top: 32, bottom: 32 })
          }
        }
        .width('100%')
        .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING })
        .margin({ top: DesignDimens.SPACE_SM })
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)

        // 5. 服务须知（@Builder 调用返回 void，外包一层容器承载间距）
        Column() {
          this.ServiceTips()
        }
        .margin({ top: 44 })

        // 底部间距（底栏不重叠内容，留一档呼吸位）
        Row() {
        }.height(DesignDimens.SPACE_MD)
      }
    }
    .scrollBar(BarState.Off)
    .width("100%")
    .height("100%")
    .align(Alignment.TopStart)
    // 沉浸式：内容避让状态栏（背景延伸）
    .padding({ top: safeTopVp() })
    .backgroundColor(DesignColor.BACKGROUND)
  }
}
