import { EmptyState, LoadingState, PageHeader } from "../view/common/AppComponents"
import { DesignColor, DesignDimens, DesignElevation, DesignFontSize, DesignFontFeature, DesignFontWeight } from "../constant/DesignSystem"
import { safeBottomVp, safeTopVp } from "../utils/SafeArea"
import { getTransactions } from "../network/api/UserApi"
import { TransactionVo } from "../network/vo/TransactionVo"
import { HttpStatusCode } from "@ohos/axios"
import { LoadingFooter } from "../view/LoadingFooter"
import { showToastSafety } from "../utils/ToastUtil"
import { logger } from "../utils/Logger"
import { PagingController } from "../utils/PagingUtil"
import { ThemeRefresh } from '../view/common/ThemeRefresh'

// 分页每页条数（后端 pageSize 上限 50，常规列表沿用 10）
const PAGE_SIZE: number = 10

// 流水类型标识（对齐后端 TransactionVo.type 取值）
const TYPE_RECHARGE: string = "recharge"
const TYPE_PAYMENT: string = "payment"
// 成功状态标识（对齐后端 TransactionVo.status 取值）
const STATUS_SUCCESS: string = "success"

@Builder
export function WalletTransactionsBuilder() {
  WalletTransactions()
}

@Component
struct WalletTransactions {
  @State recordList: Array<TransactionVo> = []
  @State isLoadingMore: boolean = false
  // 首屏加载态：数据返回前展示占位，避免页面空白（对齐充值记录页 LoadingState 模式）
  @State isLoadingFirst: boolean = true
  // 轻量分页封装：触底加载/上锁防重/封底判定统一收敛
  private paging: PagingController = new PagingController(
    (page: number): Promise<boolean> => {
      return this.fetchTransactionPage(page)
    },
    (loading: boolean) => {
      this.isLoadingMore = loading
    }
  )

  async aboutToAppear(): Promise<void> {
    logger.debug('walletTransactions', '钱包流水页挂载，拉取首页数据')
    await this.fetchTransactionPage(1)
    this.isLoadingFirst = false
  }

  /**
   * 流水时间展示格式化（防御性兼容两种入参，与充值记录页 formatRechargeTime 同策略）：
   * 已是空格分隔串则原样展示，ISO（含 T）则转为 `yyyy-MM-dd HH:mm`；解析失败原样返回，不吞数据。
   */
  private formatTransactionTime(raw: string): string {
    if (raw === "") {
      return raw
    }
    if (!raw.includes("T")) {
      return raw
    }
    // ISO 取前 16 位（yyyy-MM-ddTHH:mm）并把 T 替换为空格
    const head: string = raw.length >= 16 ? raw.substring(0, 16) : raw
    return head.replace("T", " ")
  }

  /** 类型标签文案（充值/消费） */
  private typeLabel(type: string): string {
    return type === TYPE_RECHARGE ? "充值" : "消费"
  }

  /** 状态文案：后端当前仅产出 success，未知值原样兜底展示不吞数据 */
  private statusText(status: string): string {
    return status === STATUS_SUCCESS ? "成功" : status
  }

  /**
   * 金额展示：充值加号绿色 / 消费减号警示色；消费流水后端已下发负数金额，
   * 统一按类型重算符号（对 amount 取绝对值），避免依赖后端符号约定。
   */
  private amountText(item: TransactionVo): string {
    const value = Math.abs(item.amount ?? 0).toFixed(2)
    return item.type === TYPE_RECHARGE ? `+¥${value}` : `-¥${value}`
  }

  /** 金额色：充值 = SUCCESS 绿（大字号允许 3.5:1）；消费 = DANGER_INK 警示色 */
  private amountColor(item: TransactionVo): number {
    return item.type === TYPE_RECHARGE ? DesignColor.SUCCESS : DesignColor.DANGER_INK
  }

  /**
   * 按页拉取钱包流水。
   * @returns 本页是否有数据（供 PagingController 判断是否封底）
   */
  async fetchTransactionPage(page: number): Promise<boolean> {
    try {
      let res = await getTransactions(page, PAGE_SIZE)
      if (res.code === HttpStatusCode.Ok && res.data !== null) {
        let records = res.data!.records
        if (records === null || records.length === 0) {
          if (this.recordList.length > 0) {
            logger.debug('walletTransactions', `已到末页 page=${page} 已加载=${this.recordList.length} 条`)
            showToastSafety("没有更多数据了")
          }
          return false
        }
        if (page === 1) {
          // stale-while-revalidate（对齐充值记录页）：刷新成功才整体替换，刷新期间旧内容保持可读
          this.recordList = records
          logger.debug('walletTransactions', `钱包流水首页加载成功 count=${records.length}`)
          return true
        }
        this.recordList.push(...records)
        logger.debug('walletTransactions', `钱包流水分页加载成功 page=${page} count=${records.length}`)
        return true
      }
      logger.warn('walletTransactions', `钱包流水加载失败 page=${page} ` +
        `code=${res.code} message=${res.message ?? ""}`)
      showToastSafety(res.message, "钱包流水加载失败")
      return false
    } catch (err) {
      // 网络异常/401 拒绝时 promise 会 throw：兜住并按失败页返回，避免 isLoadingFirst 卡死永久骨架
      if (page === 1) {
        this.recordList = []
      }
      showToastSafety("网络异常，请稍后重试")
      return false
    }
  }

  @Builder
  TransactionCard(item: TransactionVo) {
    Row() {
      // 左侧：类型标签盒（充值=成功容器浅绿 / 消费=错误容器浅粉，44 容器 / 12 圆角，对齐充值记录页图标盒规格）
      Column() {
        Text(this.typeLabel(item.type ?? ""))
          .fontSize(DesignFontSize.CAPTION)
          .fontWeight(DesignFontWeight.MEDIUM)
          .fontColor(item.type === TYPE_RECHARGE
            ? DesignColor.ON_SUCCESS_CONTAINER
            : DesignColor.ON_ERROR_CONTAINER)
      }
      .width(44)
      .height(44)
      .borderRadius(DesignDimens.ICON_CONTAINER_RADIUS)
      .backgroundColor(item.type === TYPE_RECHARGE
        ? DesignColor.SUCCESS_CONTAINER
        : DesignColor.ERROR_CONTAINER)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)

      // 中间：描述 + 时间
      Column() {
        Text(item.description ?? "")
          .fontSize(DesignFontSize.BODY_SMALL)
          .fontWeight(DesignFontWeight.NORMAL)
          .fontColor(DesignColor.TEXT_PRIMARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })

        Text(this.formatTransactionTime(item.transactionTime ?? ""))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_SECONDARY)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 12 })
      .layoutWeight(1)

      // 右侧：金额（+绿 / -警示色，18/600 + tnum）+ 状态文案
      Column() {
        Text(this.amountText(item))
          .fontSize(DesignFontSize.AMOUNT_MEDIUM)
          .fontWeight(DesignFontWeight.SEMI_BOLD)
          .fontFeature(DesignFontFeature.TNUM)
          .fontColor(this.amountColor(item))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Clip })

        Text(this.statusText(item.status ?? ""))
          .fontSize(DesignFontSize.CAPTION)
          .fontColor(DesignColor.TEXT_TERTIARY)
          .margin({ top: 4 })
      }
      .alignItems(HorizontalAlign.End)
      .margin({ left: 12 })
    }
    .width("100%")
    .padding(DesignDimens.CARD_PADDING)
    .backgroundColor(DesignColor.SURFACE)
    .borderRadius(DesignDimens.CARD_RADIUS)
    // DESIGN-SPEC §3.1 唯一卡片画法：1px outline 描边 + 卡片档阴影（对齐充值记录页）
    .border({ width: 1, color: DesignColor.OUTLINE, radius: DesignDimens.CARD_RADIUS })
    .shadow({
      radius: DesignElevation.CARD_SHADOW_RADIUS,
      color: DesignColor.SHADOW_COLOR_CARD,
      offsetX: 0,
      offsetY: DesignElevation.CARD_SHADOW_OFFSET
    })
  }

  build() {
    NavDestination() {
      // 深浅色实时跟随（深色模式审计）：主题重建宿主包裹本页内容——
      // 色模式翻转时 if 双分支重建子树，子树内 DesignColor 静态令牌直读点重读新值
      // （静态字段变更不驱动 UI 刷新，机制见 view/common/ThemeRefresh.ets）
      ThemeRefresh() {
      Column() {
        // 页头（公共 PageHeader：22/600 标题 + 48dp 返回热区）
        PageHeader({ title: "钱包流水" })

        if (this.isLoadingFirst) {
          // 首屏加载态（骨架屏，§3.6 禁转圈）
          LoadingState()
            .width("100%")
            .layoutWeight(1)
            .transition(TransitionEffect.OPACITY)
            .animation({ duration: 200, curve: Curve.EaseOut })
        } else if (this.recordList.length > 0) {
          // 与充值记录页一致：LazyColumn 触底分页，无下拉刷新入口
          List({ space: DesignDimens.CARD_SPACING }) {
            ForEach(this.recordList, (item: TransactionVo, index: number) => {
              ListItem() {
                this.TransactionCard(item)
              }
            }, (item: TransactionVo, index: number) => `${item.transactionTime ?? ""}-${item.type ?? ""}-${index}`)
            ListItem() {
              LoadingFooter({ isLoading: !!this.isLoadingMore })
            }
          }
          .width("100%")
          .height("100%")
          .padding({ left: DesignDimens.PAGE_PADDING, right: DesignDimens.PAGE_PADDING, bottom: safeBottomVp() })
          .onScrollIndex((start: number, end: number) => {
            this.paging.onScrollIndex(end, this.recordList?.length ?? 0)
          })
          .onScrollFrameBegin((offset: number, state: ScrollState) => {
            this.paging.onScrollFrameBegin(offset)
            return { offsetRemain: offset }
          })
          .transition(TransitionEffect.OPACITY)
          .animation({ duration: 200, curve: Curve.EaseOut })
        } else {
          // 空状态（滚筒圆母题 + 引导文案，§3.6）
          EmptyState({ message: "暂无交易记录" })
            .width("100%")
            .layoutWeight(1)
        }
      }
      .width("100%")
      .height("100%")
      // 沉浸式：内容避让状态栏（背景延伸）
      .padding({ top: safeTopVp() })
      }
    }
    .width("100%")
    .height("100%")
    .align(Alignment.Top)
    .backgroundColor(DesignColor.BACKGROUND)
    .hideTitleBar(true)
  }
}
